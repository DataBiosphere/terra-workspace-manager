package bio.terra.workspace.common.utils;

import java.util.Random;

public class TestUtils {
  private static final Random RANDOM = new Random();

  public static String appendRandomNumber(String string) {
    return string + "-" + RANDOM.nextInt(100000);
  }
}
