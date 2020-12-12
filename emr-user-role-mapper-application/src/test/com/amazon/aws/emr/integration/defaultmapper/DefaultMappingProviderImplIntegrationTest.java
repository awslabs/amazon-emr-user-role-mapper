package com.amazon.aws.emr.integration.defaultmapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import com.amazon.aws.emr.integration.IntegrationTestBase;
import com.amazon.aws.emr.integration.utils.IAMUtils;
import com.amazon.aws.emr.integration.utils.S3Utils;
import com.amazon.aws.emr.integration.utils.STSUtils;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.identitymanagement.model.Policy;
import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.EC2MetadataUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.glassfish.jersey.servlet.ServletContainer;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Integration test for {@link com.amazon.aws.emr.mapping.DefaultUserRoleMapperImpl}
 */
@Slf4j
public class DefaultMappingProviderImplIntegrationTest extends IntegrationTestBase {

  private static QueuedThreadPool pool = new QueuedThreadPool();
  private static Server jettyServer = null;
  private static int TEST_PORT = 9944;
  private static String TEST_S3_OBJECT_CONTENTS = "This is a test object";

  static private void createAwsResources() {
    // This bucket is used to read the mapping
    S3Utils.createBucket(TEST_CFG_BUCKET);
    // Upload a test object in the test bucket
    S3Utils.uploadObject(TEST_CFG_BUCKET, TEST_CFG_OBJECT, TEST_S3_OBJECT_CONTENTS);
    // Find the current AWS principal account
    String loggedPrincipalAccount = STSUtils.getLoggedUserAccount();
    // Now create the Role to be used in the Mapping
    createMappingRole(loggedPrincipalAccount);
    // Finally create the mapper S3 bucket, and the mapping file
    uploadDefaultImplMapping();
  }

  static private void uploadDefaultImplMapping() {
    S3Utils.createBucket(IntegrationTestBase.DEFAULT_MAPPER_IMPL_BUCKET);
    String defaultImplMappingJson = defaultImplMappingJsonTemplate
        .replaceFirst(USER_TO_CHANGE, user)
        .replaceFirst(USER_ROLE_TO_CHANGE, testRoleArn);
    S3Utils.uploadObject(IntegrationTestBase.DEFAULT_MAPPER_IMPL_BUCKET,
        IntegrationTestBase.DEFAULT_MAPPER_IMPL_MAPPING, defaultImplMappingJson);
  }

  static private void createMappingRole(String awsAccount) {
    // Replace the principal in the template
    String roleDoc = rolePolicyDocumentTemplate.replaceFirst(AWS_ACCOUNT_TO_CHANGE, awsAccount);
    Policy policy = IAMUtils.createPolicy(awsAccount, jsonPolicyDocument);
    testPolicyArn = policy.getArn();
    // Now create the role
    testRoleName = TEST_ROLE_PREFIX + "-" + policy.hashCode() + "-" + roleDoc.hashCode();
    Role role = IAMUtils.createRole(testRoleName, roleDoc);
    IAMUtils.attachPolicyToRole(role.getRoleName(), policy.getArn());
    testRoleArn = role.getArn();
  }

  @BeforeClass
  static public void setUp() throws Exception {
    if (!isOsSupported()) {
      log.warn("This OS is not supported for integration tests");
      return;
    }
    user = System.getProperty("user.name");
    createAwsResources();
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
    context.setContextPath("/");
    jettyServer = new Server(pool);
    jettyServer.setHandler(context);

    ServerConnector httpConnector = new ServerConnector(jettyServer);
    httpConnector.setPort(TEST_PORT);
    jettyServer.addConnector(httpConnector);

    ServletHolder jerseyServlet = context.addServlet(ServletContainer.class, "/*");
    jerseyServlet.setInitOrder(0);

    // Tells the Jersey Servlet which REST service/class to load.
    jerseyServlet.setInitParameter("jersey.config.server.provider.packages", "com.amazon.emr.api");
    jerseyServlet
        .setInitParameter("javax.ws.rs.Application",
            DefaultProviderImplIntegrationApplication.class.getName());

    log.debug("Starting the Jetty server");
    jettyServer.start();
    // AWS Role creation is eventually consistent.
    // TODO - Fine tune this.
    Thread.sleep(10 * 1000);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    if (jettyServer != null) {
      jettyServer.stop();
      jettyServer.join();
      jettyServer.destroy();
      jettyServer = null;
    }
    // Role deletion requires no policies to be attached.
    if (testRoleArn != null && testPolicyArn != null) {
      IAMUtils.detachPolicyFromRole(testRoleName, testPolicyArn);
    }
    if (testRoleArn != null) {
      IAMUtils.deleteRole(testRoleName);
    }
    if (testPolicyArn != null) {
      IAMUtils.deletePolicy(testPolicyArn);
    }
    S3Utils.deleteObject(IntegrationTestBase.DEFAULT_MAPPER_IMPL_BUCKET,
        IntegrationTestBase.DEFAULT_MAPPER_IMPL_MAPPING);
    S3Utils.deleteBucket(DEFAULT_MAPPER_IMPL_BUCKET);
  }

  @Before
  public void beforeMethod() {
    // Skip tests if the underlying OS is not supported.
    Assume.assumeFalse(!isOsSupported());
  }

  @Test
  public void list_roles_api() throws Exception {
    HttpUriRequest request =
        new HttpGet(LOCALHOST_SERVER + ":" + TEST_PORT + IMDS_CREDENTIALS_URI);
    HttpResponse httpResponse = HttpClientBuilder.create().build().execute(request);
    assertThat(httpResponse.getStatusLine().getStatusCode(), is(HttpStatus.SC_OK));
    String responseString = new BasicResponseHandler().handleResponse(httpResponse);
    assertThat(responseString, is(testRoleName));
  }

  @Test
  public void credentials_api() throws Exception {
    HttpUriRequest request =
        new HttpGet(LOCALHOST_SERVER + ":" + TEST_PORT + IMDS_CREDENTIALS_URI + testRoleName);
    HttpResponse httpResponse = HttpClientBuilder.create().build().execute(request);
    assertThat(httpResponse.getStatusLine().getStatusCode(), is(HttpStatus.SC_OK));
    String responseString = new BasicResponseHandler().handleResponse(httpResponse);
    EC2MetadataUtils.IAMSecurityCredential credentials = GSON.fromJson(responseString,
        EC2MetadataUtils.IAMSecurityCredential.class);
    BasicSessionCredentials sessionCredentials = new BasicSessionCredentials(
        credentials.accessKeyId,
        credentials.secretAccessKey,
        credentials.token);

    AmazonS3 s3 = AmazonS3ClientBuilder.standard()
        .withCredentials(new AWSStaticCredentialsProvider(sessionCredentials))
        .build();
    S3Object s3Object = s3.getObject(new GetObjectRequest(TEST_CFG_BUCKET, TEST_CFG_OBJECT));
    assertThat(S3Utils.getS3FileAsString(s3Object), is(TEST_S3_OBJECT_CONTENTS));
  }

  @Test
  public void reload_config_with_no_role_mapping_gives_no_credentials() throws Exception {
    HttpUriRequest request =
        new HttpGet(LOCALHOST_SERVER + ":" + TEST_PORT + IMDS_CREDENTIALS_URI);
    HttpResponse beforeMappingChangeHttpResponse = HttpClientBuilder.create().build().execute(request);
    assertThat(beforeMappingChangeHttpResponse.getStatusLine().getStatusCode(), is(HttpStatus.SC_OK));
    // Now change the mapping file with no role mapping
    String defaultImplMappingJson = defaultImplMappingJsonTemplate
        .replaceFirst(USER_TO_CHANGE, user)
        .replaceFirst(USER_ROLE_TO_CHANGE, "arn:aws:iam::12345678912:role/test-urm-2");
    S3Utils.uploadObject(IntegrationTestBase.DEFAULT_MAPPER_IMPL_BUCKET,
        IntegrationTestBase.DEFAULT_MAPPER_IMPL_MAPPING, defaultImplMappingJson);
    // Sleep long enough for new mapping to be in effect.
    Thread.sleep(RELOAD_CFG_TIME_MIN * 60 * 1000);
    HttpResponse httpResponse = HttpClientBuilder.create().build().execute(request);
    assertThat(httpResponse.getStatusLine().getStatusCode(), is(HttpStatus.SC_OK));
    String responseString = new BasicResponseHandler().handleResponse(httpResponse);
    assertThat(responseString, is("test-urm-2"));
  }

  @Test
  public void unauthorized_role_access() throws Exception {
    HttpUriRequest request =
        new HttpGet(LOCALHOST_SERVER + ":" + TEST_PORT + IMDS_CREDENTIALS_URI + "unauthorizedrole");
    HttpResponse httpResponse = HttpClientBuilder.create().build().execute(request);
    assertThat(httpResponse.getStatusLine().getStatusCode(), is(HttpStatus.SC_NO_CONTENT));
  }
}
