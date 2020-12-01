package com.amazon.aws.emr.system.user;

import com.amazon.aws.emr.common.system.PrincipalResolver;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IntegTestPrincipalResolver implements PrincipalResolver {

  @Override
  public Optional<String> getUsername(int uid) {
    log.debug("********* USE *******");
    return Optional.of(System.getProperty("user.name"));
  }

  @Override
  public Optional<List<String>> getGroups(String username) {
    return Optional.empty();
  }
}
