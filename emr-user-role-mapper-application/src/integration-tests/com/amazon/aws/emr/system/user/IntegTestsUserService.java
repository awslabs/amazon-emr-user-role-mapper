package com.amazon.aws.emr.system.user;

import com.amazon.aws.emr.common.system.user.UserIdService;
import java.util.OptionalInt;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.systems.global.macosx;

@Slf4j
public class IntegTestsUserService implements UserIdService {

  @Override
  public OptionalInt resolveSystemUID(String localAddr, int localPort, String remoteAddr,
      int remotePort) {
    log.debug("ID ******** ");
    int id = macosx.getuid();
    log.debug("ID2 ******** " + id + macosx.getpwuid(id).pw_name().getString());
    return OptionalInt.of(id);
  }
}
