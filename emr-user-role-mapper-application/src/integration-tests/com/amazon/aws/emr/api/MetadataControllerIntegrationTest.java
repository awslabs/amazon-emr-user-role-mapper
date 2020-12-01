package com.amazon.aws.emr.api;

import com.amazon.aws.emr.common.Constants;
import com.amazon.aws.emr.utils.IAMUtils;
import com.amazon.aws.emr.utils.ResourceUtils;
import com.amazon.aws.emr.utils.STSUtils;
import com.amazon.aws.emr.ws.UserRoleMapperIntegApplication;
import com.amazonaws.services.identitymanagement.model.Policy;
import com.amazonaws.services.identitymanagement.model.Role;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
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
public class MetadataControllerIntegrationTest extends IntegTestBase {

  private static Server jettyServer = null;
  static QueuedThreadPool pool = new QueuedThreadPool();

  static String TEST_BUCKET = "test-urm-bucket";
  static String TEST_ROLE = "test-integ-urm-role";
  static String policyArn;
  static String roleArn;

  static String rolePolicyDocumentTemplate = "{\n"
      + "  \"Version\": \"2012-10-17\",\n"
      + "  \"Statement\": [\n"
      + "    {\n"
      + "      \"Effect\": \"Allow\",\n"
      + "      \"Principal\": {\n"
      + "        \"AWS\": \"CURRENT_USER\"\n"
      + "      },\n"
      + "      \"Action\": \"sts:AssumeRole\"\n"
      + "    }\n"
      + "  ]\n"
      + "}";

  static String jsonPolicyDocument = "{" +
      "    \"Version\": \"2012-10-17\"," +
      "    \"Statement\": [" +
      "        {" +
      "            \"Effect\": \"Allow\"," +
      "            \"Action\": [" +
      "                \"s3:Put*\"," +
      "                \"s3:List*\"," +
      "                \"s3:Get*\"" +
      "            ]," +
      "            \"Resource\": \"arn:aws:s3:::"+TEST_BUCKET+"/*\"" +
      "        }" +
      "    ]" +
      "}";

  static void createAwsResources() {
    String loggedUser = STSUtils.getLoggedUser();
    String roleDoc = rolePolicyDocumentTemplate.replaceFirst("CURRENT_USER", loggedUser);
    ResourceUtils.createBucket(TEST_BUCKET);
    Policy policy = IAMUtils.createPolicy(jsonPolicyDocument);
    policyArn = policy.getArn();
    Role role = IAMUtils.createRole(TEST_ROLE, roleDoc);
    IAMUtils.attachPolicyToRole(role.getRoleName(), policy.getArn());
    roleArn = role.getArn();
    log.debug("******** AWS "  + role.getArn());
  }

  static void createRole() {

  }

  @BeforeClass
  static public void setUp() throws Exception {
    createAwsResources();
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
    context.setContextPath("/");
    jettyServer = new Server(pool);
    jettyServer.setHandler(context);

    ServerConnector httpConnector = new ServerConnector(jettyServer);
    httpConnector.setPort(Constants.JETTY_PORT);
    jettyServer.addConnector(httpConnector);

    ServletHolder jerseyServlet = context.addServlet(ServletContainer.class, "/*");
    jerseyServlet.setInitOrder(0);

    // Tells the Jersey Servlet which REST service/class to load.
    jerseyServlet.setInitParameter("jersey.config.server.provider.packages", "com.amazon.emr.api");
    jerseyServlet
        .setInitParameter("javax.ws.rs.Application", UserRoleMapperIntegApplication.class.getName());

    log.debug("Starting the Jetty server");
    jettyServer.start();
  }

  @Test
  public void list_roles_api() throws Exception {
    HttpUriRequest request = new HttpGet(
        "http://localhost:9944/latest/meta-data/iam/security-credentials/");
    HttpResponse httpResponse = HttpClientBuilder.create().build().execute(request);
    log.debug("**********" + httpResponse.getStatusLine().getStatusCode());
  }

  @Test
  public void credentials_api() throws Exception {
    HttpUriRequest request = new HttpGet(
        "http://localhost:9944/latest/meta-data/iam/security-credentials/testrole");
    HttpResponse httpResponse = HttpClientBuilder.create().build().execute(request);
    log.debug("**********" + httpResponse.getStatusLine().getStatusCode());
  }

  @Test
  public void invalid_api() throws Exception {
    HttpUriRequest request = new HttpGet(
        "http://localhost:9944/latest/meta-data/iam/invalid/testrole");
    HttpResponse httpResponse = HttpClientBuilder.create().build().execute(request);
    log.debug("**********" + httpResponse.getStatusLine().getStatusCode());
  }


  @AfterClass
  public static void tearDown() throws Exception {
    if (jettyServer != null) {
      jettyServer.stop();
      jettyServer.join();
      jettyServer.destroy();
      jettyServer = null;
    }
    IAMUtils.detachPolicyFromRole(TEST_ROLE, policyArn);
    IAMUtils.deleteRole(TEST_ROLE);
    IAMUtils.deletePolicy(policyArn);
  }
}
