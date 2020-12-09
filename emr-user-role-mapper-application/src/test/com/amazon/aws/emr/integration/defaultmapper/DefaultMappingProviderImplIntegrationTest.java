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
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class DefaultMappingProviderImplIntegrationTest extends IntegrationTestBase {

  private static QueuedThreadPool pool = new QueuedThreadPool();
  private static Server jettyServer = null;
  private static int TEST_PORT = 9944;
  private static String TEST_S3_OBJECT_CONTENTS = "This is a test object";

  static void createAwsResources() {
    // This bucket is given authorization to in the Role
    S3Utils.createBucket(TEST_BUCKET);
    // Upload a test object in the test bucket
    S3Utils.uploadObject(TEST_BUCKET, TEST_OBJECT, TEST_S3_OBJECT_CONTENTS);
    // Find the current AWS principal account
    String loggedPrincipal = STSUtils.getLoggedUserAccount();
    // Now create the Role to be used in the Mapping
    createMappingRole(loggedPrincipal);
    // Finally create the mapper S3 bucket, and the mapping file
    uploadDefaultImplMapping();
  }

  static void uploadDefaultImplMapping() {
    S3Utils.createBucket(IntegrationTestBase.DEFAULT_MAPPER_IMPL_BUCKET);
    String defaultImplMappingJson = defaultImplMappingJsonTemplate
        .replaceFirst(USER_TO_CHANGE, System.getProperty("user.name"))
        .replaceFirst(USER_ROLE_TO_CHANGE, roleArn);
    S3Utils.uploadObject(IntegrationTestBase.DEFAULT_MAPPER_IMPL_BUCKET,
        IntegrationTestBase.DEFAULT_MAPPER_IMPL_MAPPING, defaultImplMappingJson);
  }

  static void createMappingRole(String awsAccount) {
    // Replace the principal in the template
    String roleDoc = rolePolicyDocumentTemplate.replaceFirst(AWS_ACCOUNT_TO_CHANGE, awsAccount);
    Policy policy = IAMUtils.createPolicy(awsAccount, jsonPolicyDocument);
    policyArn = policy.getArn();
    // Now create the role
    TEST_USER_ROLE = TEST_ROLE_PREFIX + "-" + policy.hashCode() + "-" + roleDoc.hashCode();
    Role role = IAMUtils.createRole(TEST_USER_ROLE, roleDoc);
    IAMUtils.attachPolicyToRole(role.getRoleName(), policy.getArn());
    roleArn = role.getArn();
  }

  @BeforeClass
  static public void setUp() throws Exception {
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
            UserRoleMapperIntegrationApplication.class.getName());

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
    IAMUtils.detachPolicyFromRole(TEST_USER_ROLE, policyArn);
    IAMUtils.deleteRole(TEST_USER_ROLE);
    IAMUtils.deletePolicy(policyArn);
  }

  @Test
  public void list_roles_api() throws Exception {
    HttpUriRequest request =
        new HttpGet(LOCALHOST_SERVER + ":" + TEST_PORT + IMDS_CREDENTIALS_URI);
    HttpResponse httpResponse = HttpClientBuilder.create().build().execute(request);
    assertThat(httpResponse.getStatusLine().getStatusCode(), is(HttpStatus.SC_OK));
    String responseString = new BasicResponseHandler().handleResponse(httpResponse);
    assertThat(responseString, is(TEST_USER_ROLE));
  }

  @Test
  public void credentials_api() throws Exception {
    HttpUriRequest request =
        new HttpGet(LOCALHOST_SERVER + ":" + TEST_PORT + IMDS_CREDENTIALS_URI + TEST_USER_ROLE);
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
    S3Object s3Object = s3.getObject(new GetObjectRequest(TEST_BUCKET, TEST_OBJECT));
    assertThat(S3Utils.getS3FileAsString(s3Object.getObjectContent()), is(TEST_S3_OBJECT_CONTENTS));
  }

  @Test
  public void unauthorized_role_access() throws Exception {
    HttpUriRequest request =
        new HttpGet(LOCALHOST_SERVER + ":" + TEST_PORT + IMDS_CREDENTIALS_URI + "unauthorizedrole");
    HttpResponse httpResponse = HttpClientBuilder.create().build().execute(request);
    assertThat(httpResponse.getStatusLine().getStatusCode(), is(HttpStatus.SC_NO_CONTENT));
  }
}
