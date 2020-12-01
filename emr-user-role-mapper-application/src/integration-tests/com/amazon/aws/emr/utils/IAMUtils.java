package com.amazon.aws.emr.utils;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.AttachRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.CreatePolicyRequest;
import com.amazonaws.services.identitymanagement.model.CreatePolicyResult;
import com.amazonaws.services.identitymanagement.model.CreateRoleRequest;
import com.amazonaws.services.identitymanagement.model.CreateRoleResult;
import com.amazonaws.services.identitymanagement.model.DeletePolicyRequest;
import com.amazonaws.services.identitymanagement.model.DeleteRoleRequest;
import com.amazonaws.services.identitymanagement.model.DetachRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.DetachRolePolicyResult;
import com.amazonaws.services.identitymanagement.model.Policy;
import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityResult;

public class IAMUtils {

  final static AmazonIdentityManagement iam =
      AmazonIdentityManagementClientBuilder.defaultClient();


  public static Policy createPolicy(String policy) {
    CreatePolicyRequest request = new CreatePolicyRequest()
        .withPolicyName("urm-policy" + policy.hashCode())
        .withPolicyDocument(policy);

    CreatePolicyResult response = iam.createPolicy(request);
    return response.getPolicy();
  }

  public static void attachPolicyToRole(String roleArn, String PolicyArn) {
    AttachRolePolicyRequest attach_request =
        new AttachRolePolicyRequest()
            .withRoleName(roleArn)
            .withPolicyArn(PolicyArn);

    iam.attachRolePolicy(attach_request);
  }

  public static void detachPolicyFromRole(String role, String policy) {
    DetachRolePolicyRequest detachRolePolicyRequest =
        new DetachRolePolicyRequest()
            .withRoleName(role)
            .withPolicyArn(policy);

    iam.detachRolePolicy(detachRolePolicyRequest);
  }

  public static Role createRole(String name, String policy) {
    CreateRoleResult createRoleResult = iam.createRole(new CreateRoleRequest().withRoleName(name)
        .withAssumeRolePolicyDocument(policy));
    return createRoleResult.getRole();
  }


  public static void deletePolicy(String policyArn) {
    DeletePolicyRequest deletePolicyRequest = new DeletePolicyRequest();
    deletePolicyRequest.setPolicyArn(policyArn);
    iam.deletePolicy(deletePolicyRequest);
  }

  public static void deleteRole(String roleName) {
    DeleteRoleRequest deleteRoleRequest = new DeleteRoleRequest();
    deleteRoleRequest.setRoleName(roleName);
    iam.deleteRole(deleteRoleRequest);
  }
}
