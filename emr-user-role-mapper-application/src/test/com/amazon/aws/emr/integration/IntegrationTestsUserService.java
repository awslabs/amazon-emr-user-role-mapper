package com.amazon.aws.emr.integration;

import com.amazon.aws.emr.common.system.user.UserIdService;
import com.amazon.aws.emr.integration.utils.OSUtils;
import java.util.OptionalInt;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IntegrationTestsUserService implements UserIdService {

  @Override
  public OptionalInt resolveSystemUID(String localAddr, int localPort, String remoteAddr,
      int remotePort) {
    int uid = OSUtils.getUid();
    log.info("Detected callers uid as {}", uid);
    return OptionalInt.of(uid);
  }
}
