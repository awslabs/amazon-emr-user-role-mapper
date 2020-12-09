package com.amazon.aws.emr.integration;

import com.amazon.aws.emr.common.system.PrincipalResolver;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IntegrationTestsPrincipalResolver implements PrincipalResolver {

  @Override
  public Optional<String> getUsername(int uid) {
    return Optional.of(System.getProperty("user.name"));
  }

  @Override
  public Optional<List<String>> getGroups(String username) {
    return Optional.empty();
  }
}
