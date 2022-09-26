package bio.terra.workspace.common.utils;

import java.util.Random;

public class TestUtils {
  private static final Random RANDOM = new Random();

  public static String appendRandomNumber(String string) {
    // Can't have dash because BQ dataset names can't have dash.
    // Can't have underscore because for controlled buckets, GCP recommends not having underscore
    // in bucket name.
    return string + RANDOM.nextInt(100000);
  }
}
