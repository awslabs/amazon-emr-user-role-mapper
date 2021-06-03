package com.amazonaws.emr.urm.hive.urmstoragebasedauthorizer;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLConnection;

public class URMCredentialsRetriever
{
    private static final String URM_ADDRESS_FOR_IMPERSONATION = "http://localhost:9944/latest/meta-data/iam/security-credentials/impersonation/";
    private static final Log LOG = LogFactory.getLog(URMCredentialsRetriever.class);

    AWSCredentials getCredentialsForUser(String userName) {
        try {
            //Create http-client to get user mapped role credentials
            URI uri = URI.create(URM_ADDRESS_FOR_IMPERSONATION + userName);
            URLConnection connection = uri.toURL().openConnection();
            InputStream response = connection.getInputStream();
            try (BufferedReader rd = new BufferedReader(new InputStreamReader(response))) {
                StringBuilder responseString = new StringBuilder(); // or StringBuffer if Java version 5+
                String line;
                while ((line = rd.readLine()) != null) {
                    responseString.append(line);
                    responseString.append('\r');
                }
                return extractCredentialsFromResponse(responseString.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("Caught exception [%s] while fetching user mapped role credentials for user %s.", e, userName), e);
        }
    }

    /**
     * Returns the AWSCredentials after parsing inputResponse.
     * @param inputResponse Json string
     * @return AWS Credentials
     * @throws IOException Thrown if inputResponse is not parsable.
     */
    private AWSCredentials extractCredentialsFromResponse(String inputResponse) throws IOException
    {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(inputResponse);

        if (rootNode == null) {
            throw new RuntimeException("AwsCredentialExtractor: Rootnode is null!");
        }

        JsonNode accessKeyId = rootNode.path("AccessKeyId");
        JsonNode secretAccessKey = rootNode.path("SecretAccessKey");
        JsonNode sessionToken = rootNode.path("Token");

        if (accessKeyId == null || secretAccessKey == null || sessionToken == null) {
            LOG.error(String.format("ExtractCredentialsFromResponse: AccessKeyId isNull:%s secretAccessKey isNull: %s token isNull: %s",
                    accessKeyId == null, secretAccessKey == null, sessionToken == null));
            throw new RuntimeException("ExtractCredentialsFromResponse: Credentials from URM came back with null value! ");
        }

        // Create a BasicSessionCredentials object that contains the credentials you just retrieved.
        return new BasicSessionCredentials(accessKeyId.asText(), secretAccessKey.asText(), sessionToken.asText());
    }
}
