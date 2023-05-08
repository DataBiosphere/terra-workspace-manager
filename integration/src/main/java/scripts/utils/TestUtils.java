package scripts.utils;

import java.util.Random;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestUtils {
  private static final Logger logger = LoggerFactory.getLogger(TestUtils.class);

  private static final Random RANDOM = new Random();

  public static String appendRandomNumber(String string) {
    // Can't be dash because BQ dataset names can't have dash
    return string + "_" + RANDOM.nextInt(10000);
  }

  public static void assertContains(String actual, String expectedContains) {
    if (StringUtils.contains(actual, expectedContains)) {
      return;
    }
    logger.error("Actual '{}' does not contain '{}'", actual, expectedContains);
    Assertions.fail("Actual does not contain expected");
  }
}
