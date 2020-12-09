package com.amazon.aws.emr.integration.defaultmapper;

import com.amazon.aws.emr.ApplicationConfiguration;
import com.amazon.aws.emr.common.Constants;
import com.amazon.aws.emr.integration.IntegrationTestBase;
import javax.annotation.PostConstruct;

/**
 * Application config used to test {@link com.amazon.aws.emr.mapping.DefaultUserRoleMapperImpl}
 */
public class DefaultMapperImplApplicationConfig extends ApplicationConfiguration {

  @PostConstruct
  public void init() {
    properties.setProperty(Constants.ROLE_MAPPING_S3_BUCKET, IntegrationTestBase.DEFAULT_MAPPER_IMPL_BUCKET);
    properties.setProperty(Constants.ROLE_MAPPING_S3_KEY, IntegrationTestBase.DEFAULT_MAPPER_IMPL_MAPPING);
  }
}
