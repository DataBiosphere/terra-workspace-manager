package bio.terra.workspace.service.resource;

import bio.terra.workspace.service.resource.controlled.cloud.aws.AwsResourceConstants;
import bio.terra.workspace.service.resource.exception.InvalidNameException;
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
    if (nameLength < 1
        || nameLength > AwsResourceConstants.MAX_S3_STORAGE_FOLDER_NAME_LENGTH
        || s3ObjectDisallowedChars.matcher(prefixName).find()) {
      throw new InvalidNameException(
          String.format(
              "storage folder names must contain any sequence of valid Unicode characters (excluding %s), of length 1-1024 bytes when UTF-8 encoded",
              s3ObjectDisallowedChars.pattern()));
    }
  }

  /**
   * Validate AWS credential duration.
   *
   * @param duration duration in seconds
   * @throws InvalidNameException invalid duration
   */
  public static void validateAwsCredentialDurationSecond(int duration) {
    if (duration < AwsResourceConstants.MIN_CREDENTIAL_DURATION_SECONDS
        || duration > AwsResourceConstants.MAX_CREDENTIAL_DURATION_SECONDS) {
      throw new InvalidNameException("credential duration must be between 900 & 3600 seconds");
    }
  }
}
