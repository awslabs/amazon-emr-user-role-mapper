package com.amazon.aws.emr.integration.utils;

import java.util.List;
import org.bytedeco.systems.global.linux;
import org.bytedeco.systems.global.macosx;

public class OSUtils {

  public enum OS {
    OSX, LINUX, OTHER
  };

  private static OS detectedOS;

  /**
   * Detect the operating system from the os.name.
   *
   * @returns - the operating system detected
   */
  public static OS getOperatingSystemType() {
    if (detectedOS == null) {
      String osString = System.getProperty("os.name", "generic").toLowerCase();
      if ((osString.indexOf("mac") >= 0) || (osString.indexOf("darwin") >= 0)) {
        detectedOS = OS.OSX;
      } else if (osString.indexOf("nux") >= 0) {
        detectedOS = OS.LINUX;
      } else {
        detectedOS = OS.OTHER;
      }
    }
    return detectedOS;
  }

  public static int getUid() {
    int uid;
    OS os = OSUtils.getOperatingSystemType();
    if (os == OS.OSX) {
      uid = macosx.getuid();
    } else if (os == OS.LINUX) {
      uid = linux.getuid();
    } else {
      throw new RuntimeException("Currently integration tests don't run on this OS!");
    }
    return uid;
  }
}
