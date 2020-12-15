package com.amazon.aws.emr.integration;

import com.amazon.aws.emr.common.system.impl.CommandBasedPrincipalResolver;
import com.amazon.aws.emr.common.system.user.UserIdService;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalInt;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IntegrationTestsUserService implements UserIdService {

  @Override
  public OptionalInt resolveSystemUID(String localAddr, int localPort, String remoteAddr,
      int remotePort, boolean isNativeIMDSApi) {
     Optional<String> uid = new CommandBasedPrincipalResolver()
        .runCommand(Arrays.asList("id", "-u")).stream().findFirst();
     return uid.map(u -> OptionalInt.of(Integer.parseInt(u)))
         .orElse(OptionalInt.empty());
  }
}
