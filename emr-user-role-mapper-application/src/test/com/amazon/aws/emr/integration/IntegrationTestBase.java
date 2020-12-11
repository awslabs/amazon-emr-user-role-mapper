package com.amazon.aws.emr.integration;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Common functionality and constants for integration tests.
 */
public class IntegrationTestBase {

  protected static final Gson GSON = new GsonBuilder()
      .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
      .setPrettyPrinting()
      .create();
  public static int RELOAD_CFG_TIME_MIN = 1;
  public static String DEFAULT_MAPPER_IMPL_BUCKET = "urm-integ-test-default-mapper";
  public static String DEFAULT_MAPPER_IMPL_MAPPING = "default-impl.json";
  protected static String LOCALHOST_SERVER = "http://localhost";
  protected static String IMDS_CREDENTIALS_URI = "/latest/meta-data/iam/security-credentials/";
  protected static String USER_TO_CHANGE = "USER_TO_CHANGE";
  protected static String USER_ROLE_TO_CHANGE = "USER_ROLE_TO_CHANGE";
  protected static String AWS_ACCOUNT_TO_CHANGE = "AWS_ACCOUNT_TO_CHANGE";
  protected static String user = System.getProperty("user.name");
  protected static String TEST_CFG_BUCKET = "test-urm-bucket-" + user;
  protected static String TEST_CFG_OBJECT = "test-urm-object-" + user;
  protected static String TEST_ROLE_PREFIX = "test-integ-urm-role-" + user;
  protected static String testPolicyArn;
  protected static String testRoleArn;
  protected static String testRoleName;

  protected static String rolePolicyDocumentTemplate = "{\n"
      + "  \"Version\": \"2012-10-17\",\n"
      + "  \"Statement\": [\n"
      + "    {\n"
      + "      \"Effect\": \"Allow\",\n"
      + "      \"Principal\": {\n"
      + "        \"AWS\": \"" + AWS_ACCOUNT_TO_CHANGE + "\"\n"
      + "      },\n"
      + "      \"Action\": \"sts:AssumeRole\"\n"
      + "    }\n"
      + "  ]\n"
      + "}";
  protected static String jsonPolicyDocument = "{" +
      "    \"Version\": \"2012-10-17\"," +
      "    \"Statement\": [" +
      "        {" +
      "            \"Effect\": \"Allow\"," +
      "            \"Action\": [" +
      "                \"s3:Put*\"," +
      "                \"s3:List*\"," +
      "                \"s3:Get*\"" +
      "            ]," +
      "            \"Resource\": \"arn:aws:s3:::" + TEST_CFG_BUCKET + "/*\"" +
      "        }" +
      "    ]" +
      "}";
  protected static String defaultImplMappingJsonTemplate = "{\n"
      + "  \"PrincipalRoleMappings\": [\n"
      + "    {\n"
      + "      \"username\": \"" + USER_TO_CHANGE + "\",\n"
      + "      \"rolearn\": \"" + USER_ROLE_TO_CHANGE + "\"\n"
      + "    },\n"
      + "    {\n"
      + "      \"groupname\": \"GROUP\",\n"
      + "      \"rolearn\": \"GROUP-ROLE\",\n"
      + "      \"duration\": 1800\n"
      + "    }\n"
      + "  ]\n"
      + "}";

  /**
   * Integration tests only run on OSX and Unix as the core application needs Unix style OS.
   * @return {@code true} if running OS is OSX, or a Unix falvor, else {@code false}.
   */
  protected static boolean isOsSupported() {
    String osString = System.getProperty("os.name", "generic").toLowerCase();
    if ((osString.indexOf("mac") >= 0) || (osString.indexOf("darwin") >= 0) ||(osString.indexOf("nux") >= 0)) {
      return true;
    }
    return false;
  }
}
