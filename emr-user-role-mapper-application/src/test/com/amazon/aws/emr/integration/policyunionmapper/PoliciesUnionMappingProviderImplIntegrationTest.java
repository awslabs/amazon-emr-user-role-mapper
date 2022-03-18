package com.amazon.aws.emr.integration.policyunionmapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.amazon.aws.emr.ApplicationConfiguration;
import com.amazon.aws.emr.common.system.impl.CommandBasedPrincipalResolver;
import com.amazon.aws.emr.integration.IntegrationTestBase;
import com.amazon.aws.emr.integration.utils.IAMUtils;
import com.amazon.aws.emr.integration.utils.S3Utils;
import com.amazon.aws.emr.integration.utils.STSUtils;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.identitymanagement.model.Policy;
import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.EC2MetadataUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
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
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Integration test for {@link com.amazon.aws.emr.mapping.ManagedPolicyBasedUserRoleMapperImpl}
 */
@Slf4j
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PoliciesUnionMappingProviderImplIntegrationTest extends IntegrationTestBase {

  private static QueuedThreadPool pool = new QueuedThreadPool();
  private static Server jettyServer = null;
  private static int TEST_PORT = 9945;
  private static String TEST_S3_OBJECT_CONTENTS = "This is a test object";
  private static String userPolicyArn; // Policy granted to username
  private static String groupPolicyArn; // Policy granted to groupname
  private static String USER_ACCESS_BUCKET = "urm-integ-user-bucket-" + user;
  private static String GROUP_ACCESS_BUCKET = "urm-integ-group-bucket-" + user;
  private static List<String> groups;

  static private void createAwsResources() {
    groups = new CommandBasedPrincipalResolver(new ApplicationConfiguration()).runCommand(Arrays.asList("id", "-Gn"));
    if (groups.isEmpty()) {
      log.warn("No groups found - skipping the tests");
      return;
    }
    // Create the user policy accessible bucket
    S3Utils.createBucket(USER_ACCESS_BUCKET);
    // Upload a test object in the user bucket
    S3Utils.uploadObject(USER_ACCESS_BUCKET, TEST_CFG_OBJECT, TEST_S3_OBJECT_CONTENTS);
    // Create the group policy accessible bucket
    S3Utils.createBucket(GROUP_ACCESS_BUCKET);
    // Upload a test object in the group bucket
    S3Utils.uploadObject(GROUP_ACCESS_BUCKET, TEST_CFG_OBJECT, TEST_S3_OBJECT_CONTENTS);

    // Find the current AWS principal account
    String loggedPrincipalAccount = STSUtils.getLoggedUserAccount();
    // Now create the Role to be used in the Mapping
    createMappingRole(loggedPrincipalAccount);
    // Create the user policy
    createUsernamePolicy(loggedPrincipalAccount);
    // Create the group policy
    createGroupNamePolicy(loggedPrincipalAccount);
    // Finally create the mapper S3 bucket, and the mapping file
    uploadManagedPoliciesUnionImplMapping();
  }

  private static void createUsernamePolicy(String awsAccount) {
    String policyDocument = limitedS3AccessJsonPolicyDocument
        .replaceFirst(BUCKET_NAME_TO_CHANGE, USER_ACCESS_BUCKET);
    Policy policy = IAMUtils.createPolicy(awsAccount, policyDocument);
    userPolicyArn = policy.getArn();
  }

  private static void createGroupNamePolicy(String awsAccount) {
    String policyDocument = limitedS3AccessJsonPolicyDocument
        .replaceFirst(BUCKET_NAME_TO_CHANGE, GROUP_ACCESS_BUCKET);
    Policy policy = IAMUtils.createPolicy(awsAccount, policyDocument);
    groupPolicyArn = policy.getArn();
  }

  static private void uploadManagedPoliciesUnionImplMapping() {
    S3Utils.createBucket(IntegrationTestBase.POLICY_UNION_MAPPER_IMPL_BUCKET);
    String mappingJson = managedPoliciesImplMappingJsonTemplate
        .replaceFirst(USER_TO_CHANGE, user)
        .replaceFirst(USER_POLICY_TO_CHANGE, userPolicyArn)
        // Any '\' in the group name need to be escaped.
        // They need to land like "ANT\\DOMAIN" in the generated JSON.
        .replaceFirst(GROUP_TO_CHANGE, groups.get(0).replace("\\", "\\\\\\\\"))
        .replaceFirst(GROUP_POLICY_TO_CHANGE, groupPolicyArn);
    S3Utils.uploadObject(IntegrationTestBase.POLICY_UNION_MAPPER_IMPL_BUCKET,
        IntegrationTestBase.POLICY_UNION_MAPPER_IMPL_MAPPING, mappingJson);
  }

  static private void createMappingRole(String awsAccount) {
    // Replace the principal in the template
    String roleDoc = rolePolicyDocumentTemplate.replaceFirst(AWS_ACCOUNT_TO_CHANGE, awsAccount);
    Policy policy = IAMUtils.createPolicy(awsAccount, fullS3AccessJsonPolicyDocument);
    testPolicyArn = policy.getArn();
    // Now create the role
    testRoleName = TEST_ROLE_PREFIX + "-" + policy.hashCode() + "-" + roleDoc.hashCode();
    Role role = IAMUtils.createRole(testRoleName, roleDoc);
    IAMUtils.attachPolicyToRole(role.getRoleName(), policy.getArn());
    testRoleArn = role.getArn();
    PoliciesUnionMapperImplApplicationConfig.setRoleArn(testRoleArn);
  }

  @BeforeClass
  static public void setUp() throws Exception {
    if (!isOsSupported()) {
      log.warn("This OS is not supported for integration tests");
      return;
    }
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
            PoliciesUnionProviderImplIntegrationApplication.class.getName());

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
      log.info("Detaching " + testPolicyArn + " from " + testRoleArn);
      IAMUtils.detachPolicyFromRole(testRoleName, testPolicyArn);
    }
    if (testRoleArn != null) {
      log.info("Deleting role: " + testRoleArn);
      IAMUtils.deleteRole(testRoleName);
    }
    //Give time for IAM to delete the role.
    Thread.sleep(2 * 1000);
    if (testPolicyArn != null) {
      log.info("Deleting policy: " + testPolicyArn);
      IAMUtils.deletePolicy(testPolicyArn);
    }
    if (userPolicyArn != null) {
      log.info("Deleting policy: " + userPolicyArn);
      IAMUtils.deletePolicy(userPolicyArn);
    }
    if (groupPolicyArn != null) {
      log.info("Deleting policy: " + groupPolicyArn);
      IAMUtils.deletePolicy(groupPolicyArn);
    }

    S3Utils.deleteObject(USER_ACCESS_BUCKET, TEST_CFG_OBJECT);
    S3Utils.deleteBucket(USER_ACCESS_BUCKET);

    S3Utils.deleteObject(GROUP_ACCESS_BUCKET, TEST_CFG_OBJECT);
    S3Utils.deleteBucket(GROUP_ACCESS_BUCKET);

    S3Utils.deleteObject(POLICY_UNION_MAPPER_IMPL_BUCKET, POLICY_UNION_MAPPER_IMPL_MAPPING);
    S3Utils.deleteBucket(DEFAULT_MAPPER_IMPL_BUCKET);
  }

  @Before
  public void beforeMethod() {
    // Skip tests if the underlying OS is not supported.
    Assume.assumeFalse(!isOsSupported() || groups.isEmpty());
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
    BasicSessionCredentials sessionCredentials = getCredentialsFromResponse(httpResponse);

    AmazonS3 s3 = AmazonS3ClientBuilder.standard()
        .withCredentials(new AWSStaticCredentialsProvider(sessionCredentials))
        .build();
    S3Object s3Object1 = s3.getObject(new GetObjectRequest(USER_ACCESS_BUCKET, TEST_CFG_OBJECT));
    assertThat(S3Utils.getS3FileAsString(s3Object1), is(TEST_S3_OBJECT_CONTENTS));
    S3Object s3Object2 = s3.getObject(new GetObjectRequest(GROUP_ACCESS_BUCKET, TEST_CFG_OBJECT));
    assertThat(S3Utils.getS3FileAsString(s3Object2), is(TEST_S3_OBJECT_CONTENTS));
  }

  @Test
  public void reload_config_with_changed_role_mapping_gives_new_role() throws Exception {
    // Upload mapping that revokes group mapping
    // This should now deny access to GROUP_ACCESS_BUCKET
    String mappingJson = managedPoliciesImplMappingJsonTemplate
        .replaceFirst(USER_TO_CHANGE, user)
        .replaceFirst(USER_POLICY_TO_CHANGE, userPolicyArn);
    S3Utils.uploadObject(IntegrationTestBase.POLICY_UNION_MAPPER_IMPL_BUCKET,
        IntegrationTestBase.POLICY_UNION_MAPPER_IMPL_MAPPING, mappingJson);
    Thread.sleep(RELOAD_CFG_TIME_MIN * 60 * 1000);
    HttpUriRequest request =
        new HttpGet(LOCALHOST_SERVER + ":" + TEST_PORT + IMDS_CREDENTIALS_URI + testRoleName);
    HttpResponse httpResponse = HttpClientBuilder.create().build().execute(request);
    assertThat(httpResponse.getStatusLine().getStatusCode(), is(HttpStatus.SC_OK));
    BasicSessionCredentials sessionCredentials = getCredentialsFromResponse(httpResponse);

    AmazonS3 s3 = AmazonS3ClientBuilder.standard()
        .withCredentials(new AWSStaticCredentialsProvider(sessionCredentials))
        .build();
    String s3Obj = testS3GetOBject(USER_ACCESS_BUCKET, TEST_CFG_OBJECT, s3, false);
    assertEquals(s3Obj, TEST_S3_OBJECT_CONTENTS);
    testS3GetOBject(GROUP_ACCESS_BUCKET, TEST_CFG_OBJECT, s3, true);
  }

  @Test
  public void disallowed_role_access() throws Exception {
    HttpUriRequest request =
            new HttpGet(LOCALHOST_SERVER + ":" + TEST_PORT + IMDS_CREDENTIALS_URI + "unauthorizedrole");
    HttpResponse httpResponse = HttpClientBuilder.create().build().execute(request);
    assertThat(httpResponse.getStatusLine().getStatusCode(), is(HttpStatus.SC_NO_CONTENT));
  }

  @Test
  public void reload_policy_no_matching_policy() throws Exception {
    String noMappingsJson = "{\"PrincipalPolicyMappings\": []}";
    S3Utils.uploadObject(IntegrationTestBase.POLICY_UNION_MAPPER_IMPL_BUCKET,
            IntegrationTestBase.POLICY_UNION_MAPPER_IMPL_MAPPING, noMappingsJson);
    Thread.sleep(RELOAD_CFG_TIME_MIN * 60 * 1000);

    HttpUriRequest request =
        new HttpGet(LOCALHOST_SERVER + ":" + TEST_PORT + IMDS_CREDENTIALS_URI + testRoleName);
    HttpResponse httpResponse = HttpClientBuilder.create().build().execute(request);
    assertThat(httpResponse.getStatusLine().getStatusCode(), is(HttpStatus.SC_OK));

    BasicSessionCredentials sessionCredentials = getCredentialsFromResponse(httpResponse);
    AmazonS3 s3 = AmazonS3ClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(sessionCredentials))
            .build();

    testS3GetOBject(USER_ACCESS_BUCKET, TEST_CFG_OBJECT, s3, true);
    testS3GetOBject(GROUP_ACCESS_BUCKET, TEST_CFG_OBJECT, s3, true);
  }

  private BasicSessionCredentials getCredentialsFromResponse(HttpResponse httpResponse)
          throws IOException {
    String responseString = new BasicResponseHandler().handleResponse(httpResponse);
    EC2MetadataUtils.IAMSecurityCredential credentials = GSON.fromJson(responseString,
            EC2MetadataUtils.IAMSecurityCredential.class);
    return new BasicSessionCredentials(
            credentials.accessKeyId,
            credentials.secretAccessKey,
            credentials.token);
  }

  private String testS3GetOBject(String bucket, String key, AmazonS3 s3, boolean shouldFail)
          throws IOException {
    try {
      S3Object s3Object1 = s3.getObject(new GetObjectRequest(bucket, key));
      if (shouldFail) {
        fail("Access not denied to s3://" + bucket + "/" + key);
      }
      return S3Utils.getS3FileAsString(s3Object1);
    } catch (AmazonServiceException e) {
      if (shouldFail) {
        assertThat(e.getStatusCode(), is(HttpStatus.SC_FORBIDDEN));
        return null;
      }
      throw e;
    }
  }

}
