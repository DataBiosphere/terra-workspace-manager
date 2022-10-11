package bio.terra.workspace.common.utils;

import java.util.UUID;

public class TestUtils {
  public static String appendRandomNumber(String prefix) {
    // Can't have dash because BQ dataset names can't have dash.
    // Can't have underscore because for controlled buckets, GCP recommends not having underscore
    // in bucket name.
    String randomString = prefix + UUID.randomUUID();
    return randomString.replaceAll("[-_]", "");
  }
}
