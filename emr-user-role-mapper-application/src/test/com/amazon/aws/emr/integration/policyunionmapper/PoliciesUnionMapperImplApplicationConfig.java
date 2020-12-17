package com.amazon.aws.emr.integration.policyunionmapper;

import com.amazon.aws.emr.ApplicationConfiguration;
import com.amazon.aws.emr.common.Constants;
import com.amazon.aws.emr.integration.IntegrationTestBase;
import com.amazon.aws.emr.mapping.ManagedPolicyBasedUserRoleMapperImpl;
import javax.annotation.PostConstruct;
import lombok.Setter;

/**
 * Application config used to test {@link com.amazon.aws.emr.mapping.ManagedPolicyBasedUserRoleMapperImpl}
 */
public class PoliciesUnionMapperImplApplicationConfig extends ApplicationConfiguration {

  @Setter
  static String roleArn;

  @PostConstruct
  public void init() {
    properties.setProperty(Constants.ROLE_MAPPING_S3_BUCKET,
        IntegrationTestBase.POLICY_UNION_MAPPER_IMPL_BUCKET);
    properties.setProperty(Constants.ROLE_MAPPING_S3_KEY,
        IntegrationTestBase.POLICY_UNION_MAPPER_IMPL_MAPPING);
    properties.setProperty(Constants.ROLE_MAPPPING_REFRESH_INTERVAL_MIN,
        String.valueOf(IntegrationTestBase.RELOAD_CFG_TIME_MIN));
    properties.setProperty(Constants.ROLE_MAPPING_ROLE_ARN, roleArn);
    properties.setProperty(Constants.ROLE_MAPPER_CLASS,
        ManagedPolicyBasedUserRoleMapperImpl.class.getName());
  }
}
