package com.amazon.aws.emr.integration.utils;

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
import com.amazonaws.services.identitymanagement.model.EntityAlreadyExistsException;
import com.amazonaws.services.identitymanagement.model.GetPolicyRequest;
import com.amazonaws.services.identitymanagement.model.GetRoleRequest;
import com.amazonaws.services.identitymanagement.model.Policy;
import com.amazonaws.services.identitymanagement.model.Role;

public class IAMUtils {

  final static AmazonIdentityManagement iam =
      AmazonIdentityManagementClientBuilder.defaultClient();


  public static Policy createPolicy(String account, String policy) {
    String policyName = "urm-policy" + policy.hashCode();
    CreatePolicyResult response;
    try {
      CreatePolicyRequest request = new CreatePolicyRequest()
          .withPolicyName(policyName)
          .withPolicyDocument(policy);
      response = iam.createPolicy(request);
    } catch (EntityAlreadyExistsException ex) {
      String policyArn = getIamPolicyArn(account, policyName);
      return iam.getPolicy(new GetPolicyRequest().withPolicyArn(policyArn)).getPolicy();
    }
    return response.getPolicy();
  }

  private static String getIamPolicyArn(String account, String policyName) {
    return String.format("arn:aws:iam::%s:policy/%s", account, policyName);
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
    CreateRoleResult createRoleResult;
    try {
      createRoleResult = iam.createRole(new CreateRoleRequest().withRoleName(name)
          .withAssumeRolePolicyDocument(policy));
    } catch (EntityAlreadyExistsException ex) {
      return iam.getRole(new GetRoleRequest().withRoleName(name)).getRole();
    }
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
