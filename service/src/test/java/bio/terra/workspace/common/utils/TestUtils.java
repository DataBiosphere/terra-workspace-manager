package bio.terra.workspace.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import java.util.Locale;
import java.util.UUID;

public class TestUtils {
  public static String appendRandomNumber(String prefix) {
    // Can't have dash because BQ dataset names can't have dash.
    // Can't have underscore because for controlled buckets, GCP recommends not having underscore
    // in bucket name.
    String randomString = prefix + UUID.randomUUID();
    return randomString.replaceAll("[-_]", "");
  }

  /**
   * Generates random string based on UUID value. Hyphens are removed from the result string.
   * Maximum length of result string is 32: UUID length of 36 minus 4 hyphens.
   *
   * @param length maximum length of the output string. Length will be cut off down to its max value
   *     if exceeds max value of 32.
   * @return string value containing lowercase characters and digits
   */
  public static String getRandomString(int length) {
    // removes hyphen since it's forbidden to use in Azure storage account
    int maxLength = 32;
    return UUID.randomUUID()
        .toString()
        .toLowerCase(Locale.ROOT)
        .replace("-", "")
        .substring(0, length <= maxLength ? length - 1 : maxLength - 1);
  }

  public static void assertStepResultFatal(StepResult result, Class<?> exceptionClass) {
    assertStepResultFatal(result, exceptionClass, null);
  }

  public static void assertStepResultFatal(
      StepResult result, Class<?> exceptionClass, String message) {
    assertTrue(result.getException().isPresent(), message);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, result.getStepStatus(), message);
    assertEquals(exceptionClass, result.getException().get().getClass(), message);
  }
}
