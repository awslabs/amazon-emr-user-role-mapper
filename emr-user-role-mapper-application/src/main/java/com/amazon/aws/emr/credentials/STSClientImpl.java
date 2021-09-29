package com.amazon.aws.emr.credentials;

import com.amazon.aws.emr.ApplicationConfiguration;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.hk2.api.Immediate;

/**
 * Creates a custom AWS STS client based on {@link ApplicationConfiguration}
 */
@Slf4j
@Immediate
public class STSClientImpl implements STSClient {
  // If using regional configurations we will use us-west-2 as default
  // This is primarily used in integration tests
  private String regionString = "us-west-2";

  @Inject
  ApplicationConfiguration applicationConfiguration;

  AWSSecurityTokenService stsClient;

  @PostConstruct
  void init() {
    if (applicationConfiguration.isRegionalStsEnabled()) {
      Region region = null;
      try {
        region = Regions.getCurrentRegion();
        regionString = region.getName();
      } catch (Exception e) {
        log.error("Cannot determine the AWS region. Defaulting to {}", regionString);
      }
      String endpoint = String.format("https://sts.%s.amazonaws.com", regionString);
      log.info("Running the application with regional STS endpoint " + endpoint);
      stsClient = AWSSecurityTokenServiceClientBuilder
          .standard()
          .withEndpointConfiguration(new EndpointConfiguration(endpoint, regionString))
          .build();
    } else {
      log.info("Running the application with global STS endpoint.");
      stsClient = AWSSecurityTokenServiceClientBuilder
          .standard()
          .build();
    }
  }

  @Override
  public AssumeRoleResult assumeRole(AssumeRoleRequest assumeRoleRequest) {
    return stsClient.assumeRole(assumeRoleRequest);
  }
}
