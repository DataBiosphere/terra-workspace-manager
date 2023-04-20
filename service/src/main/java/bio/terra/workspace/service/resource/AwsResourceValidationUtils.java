package bio.terra.workspace.service.resource;

import bio.terra.workspace.service.resource.exception.InvalidNameException;
import com.google.common.annotations.VisibleForTesting;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** A collection of static validation functions specific to AWS */
@Component
public class AwsResourceValidationUtils {
  static final Pattern s3ObjectDisallowedChars = Pattern.compile("[{}^%`<>~#|@*+\\[\\]\"\'\\\\/]");

  /**
   * Validate AWS storage folder name.
   *
   * @param prefixName prefix name
   * @throws InvalidNameException invalid prefix name
   */
  public static void validateAwsS3StorageFolderName(String prefixName) {
    int nameLength = prefixName.getBytes(StandardCharsets.UTF_8).length;
    if (nameLength < 1 || nameLength > 1024 || s3ObjectDisallowedChars.matcher(prefixName).find()) {
      throw new InvalidNameException(
          String.format(
              "storage folder names must contain any sequence of valid Unicode characters (excluding %s), of length 1-1024 bytes when UTF-8 encoded",
              s3ObjectDisallowedChars.pattern()));
    }
  }
}
