package scripts.utils;

import java.util.Random;

public class TestUtils {
  private static final Random RANDOM = new Random();

  public static String appendRandomNumber(String string) {
    // Can't be dash because BQ dataset names can't have dash
    return string + "_" + RANDOM.nextInt(10000);
  }
}
