package com.amazon.aws.emr.credentials;

import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;

/**
 * Interface representing the AWS STS client.
 */
public interface STSClient {
  
  AssumeRoleResult assumeRole(AssumeRoleRequest assumeRoleRequest);
}
