package com.amazon.aws.emr.ws;

import com.amazon.aws.emr.ApplicationConfiguration;
import com.amazon.aws.emr.common.Constants;
import javax.annotation.PostConstruct;

public class IntegTestsAppConfig extends ApplicationConfiguration {

  @PostConstruct
  public void init() {
    properties.setProperty(Constants.ROLE_MAPPING_S3_KEY, "testKey");
    properties.setProperty(Constants.ROLE_MAPPING_S3_BUCKET, "testBucket");
  }
}
