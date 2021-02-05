package com.amazon.aws.emr.mapping;

import com.amazon.aws.emr.common.Constants;
import com.amazon.aws.emr.common.system.PrincipalResolver;
import com.amazon.aws.emr.model.PrincipalPolicyMapping;
import com.amazon.aws.emr.model.PrincipalPolicyMappings;
import com.amazon.aws.emr.rolemapper.UserRoleMapperProvider;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.PolicyDescriptorType;
import com.google.common.annotations.VisibleForTesting;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mapping impl based on union of policies after principal resolution.
 */
@Slf4j
@NoArgsConstructor
public class ManagedPolicyBasedUserRoleMapperImpl extends S3BasedUserMappingImplBase
    implements UserRoleMapperProvider {

  @VisibleForTesting
  static String DEFAULT_NO_MATCH_POLICY_ARN = "arn:aws:iam::aws:policy/AWSDenyAll";

  private PrincipalResolver principalResolver;
  private String roleArn;
  private String noMatchPolicyArn = DEFAULT_NO_MATCH_POLICY_ARN;

  private final Map<String, List<PolicyDescriptorType>> principalRoleMapping = new HashMap<>();

  private static final Gson GSON = new GsonBuilder()
      .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
      .setPrettyPrinting()
      .create();

  public ManagedPolicyBasedUserRoleMapperImpl(String bucketName, String key,
      PrincipalResolver principalResolver) {
    this.bucketName = Objects.requireNonNull(bucketName);

    // TODO: We may relax this to allow null value. In case of null value, parse all keys under above bucket
    this.key = Objects.requireNonNull(key);
    this.etag = null;
    this.principalResolver = Objects.requireNonNull(principalResolver);
  }

  @Override
  public void init(Map<String, String> configMap) {
    roleArn = Objects.requireNonNull(configMap.get(Constants.ROLE_MAPPING_ROLE_ARN));
  }

  @Override
  public Optional<AssumeRoleRequest> getMapping(String username) {
    log.debug("Got request to map user {}", username);

    List<String> principals = new ArrayList<>();
    List<PolicyDescriptorType> policyDescriptorTypes = new ArrayList<>();

    principals.add(username);
    Optional<List<String>> groups = principalResolver.getGroups(username);
    if (groups.isPresent()) {
      principals.addAll(groups.get());
    }

    log.debug("Groups user belongs to is {}", groups.orElse(Collections.EMPTY_LIST));

    principals.stream()
        .map(principal -> principalRoleMapping.getOrDefault(principal, Collections.emptyList()))
        .filter(policies -> !policies.isEmpty())
        .flatMap(List::stream)
        .distinct()
        .forEach(policyDescriptorTypes::add);

    if (policyDescriptorTypes.isEmpty()) {
      if (noMatchPolicyArn != null && noMatchPolicyArn.length() > 0) {
        log.debug("Found no mappings for this user. Returning credentials with default policy arn");
        policyDescriptorTypes.add(new PolicyDescriptorType().withArn(noMatchPolicyArn));
      } else {
        return Optional.empty();
      }
    }

    log.debug("Policies mapped for user: {}", policyDescriptorTypes);

    AssumeRoleRequest assumeRoleRequest = new AssumeRoleRequest()
        .withRoleArn(roleArn)
        .withRoleSessionName(username)
        .withPolicyArns(policyDescriptorTypes);
    return Optional.of(assumeRoleRequest);
  }

  @Override
  void processFile(String jsonString) {
    log.info("Received the following JSON {}", jsonString);
    PrincipalPolicyMappings principalPolicyMappings = GSON
        .fromJson(jsonString, PrincipalPolicyMappings.class);
    // Clear the old mapping now since we found a new valid mapping!
    principalRoleMapping.clear();
    noMatchPolicyArn = DEFAULT_NO_MATCH_POLICY_ARN;

    if (principalPolicyMappings.getNoMatchPolicyArn() != null) {
      noMatchPolicyArn = principalPolicyMappings.getNoMatchPolicyArn();
    }
    log.info("No-Match Policy ARN is : " + noMatchPolicyArn);

    for (PrincipalPolicyMapping principalPolicyMapping : principalPolicyMappings
        .getPrincipalPolicyMappings()) {
      if (!isValidMapping(principalPolicyMapping)) {
        log.info("Invalid record!");
        continue;
      }

      String principal =
          principalPolicyMapping.getUsername() != null ? principalPolicyMapping.getUsername() :
              principalPolicyMapping.getGroupname();

      List<PolicyDescriptorType> policyDescriptorTypes = new ArrayList<>();
      principalPolicyMapping.getPolicyArns().stream()
          .map(p -> new PolicyDescriptorType().withArn(p))
          .forEach(policyDescriptorTypes::add);

      principalRoleMapping.put(principal, policyDescriptorTypes);

      log.info("Mapped {} to {}", principal, principalPolicyMapping.getPolicyArns());
    }
  }

  boolean isValidMapping(PrincipalPolicyMapping principalPolicyMapping) {
    if (principalPolicyMapping == null) {
      log.info("Invalid record!");
      return false;
    }
    String principal =
        principalPolicyMapping.getUsername() != null ? principalPolicyMapping.getUsername() :
            principalPolicyMapping.getGroupname();
    if (principal == null) {
      log.info("Invalid record containing no username or groupname {}", principalPolicyMapping);
      return false;
    }

    if (principalPolicyMapping.getPolicyArns() == null || principalPolicyMapping.getPolicyArns().isEmpty()) {
      log.info("Invalid record containing no policy {}", principalPolicyMapping);
      return false;
    }
    return true;
  }
}
