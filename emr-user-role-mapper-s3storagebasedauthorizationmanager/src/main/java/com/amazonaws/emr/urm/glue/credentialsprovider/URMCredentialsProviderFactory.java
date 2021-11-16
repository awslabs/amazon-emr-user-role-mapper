package com.amazonaws.emr.urm.glue.credentialsprovider;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.emr.urm.credentialsprovider.URMCredentialsProvider;
import com.amazonaws.glue.catalog.metastore.AWSCredentialsProviderFactory;
import org.apache.hadoop.hive.conf.HiveConf;

public class URMCredentialsProviderFactory implements AWSCredentialsProviderFactory {

  @Override
  public AWSCredentialsProvider buildAWSCredentialsProvider(HiveConf hiveConf) {
    return new URMCredentialsProvider();
  }
}
