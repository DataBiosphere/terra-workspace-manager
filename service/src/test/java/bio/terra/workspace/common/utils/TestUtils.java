package bio.terra.workspace.common.utils;

import java.util.UUID;

public class TestUtils {

  public static String uniqueName(String prefix) {
    return prefix + "-" + UUID.randomUUID();
  }
}
