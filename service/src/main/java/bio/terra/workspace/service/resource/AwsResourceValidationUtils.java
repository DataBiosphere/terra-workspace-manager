package bio.terra.workspace.service.resource;

import bio.terra.workspace.service.resource.controlled.cloud.aws.AwsResourceConstants;
import bio.terra.workspace.service.resource.exception.InvalidNameException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** A collection of static validation functions specific to AWS */
@Component
public class AwsResourceValidationUtils {
  // https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-keys.html
  static final Pattern s3ObjectKeyDisallowedChars =
      Pattern.compile("[{}^%`<>~#|@*+\\[\\]\"\'\\\\/]");

  // https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_CreateNotebookInstance.html#sagemaker-CreateNotebookInstance-request-NotebookInstanceName
  private static final Pattern sageMakerInstanceNamePattern =
      Pattern.compile("^[a-zA-Z0-9](-*[a-zA-Z0-9])*");

  /**
   * Validate AWS Storage Folder name.
   *
   * @param prefixName prefix name
   * @throws InvalidNameException invalid prefix name
   */
  public static void validateAwsS3StorageFolderName(String prefixName) {
    int nameLength = prefixName.getBytes(StandardCharsets.UTF_8).length;
    if (nameLength < 1
        || nameLength > AwsResourceConstants.MAX_S3_STORAGE_FOLDER_NAME_LENGTH
        || s3ObjectKeyDisallowedChars.matcher(prefixName).find()) {
      throw new InvalidNameException(
          String.format(
              "Storage folder names must contain any sequence of valid Unicode characters (excluding %s), of length 1-1024 bytes when UTF-8 encoded",
              s3ObjectKeyDisallowedChars.pattern()));
    }
  }

  /**
   * Validate AWS SageMaker Notebook name.
   *
   * @param instanceName prefix name
   * @throws InvalidNameException invalid instance name
   */
  public static void validateAwsSageMakerNotebookName(String instanceName) {
    int nameLength = instanceName.getBytes(StandardCharsets.UTF_8).length;
    if (nameLength < 1
        || nameLength > AwsResourceConstants.MAX_SAGEMAKER_NOTEBOOK_INSTANCE_NAME_LENGTH
        || !sageMakerInstanceNamePattern.matcher(instanceName).matches()) {
      throw new InvalidNameException(
          "SageMaker instance names must contain any sequence alphabets, numbers and dashes (dash may not be first or last character), of length 1-64");
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
      throw new InvalidNameException(
          String.format(
              "Credential duration must be between %d & %d seconds",
              AwsResourceConstants.MIN_CREDENTIAL_DURATION_SECONDS,
              AwsResourceConstants.MAX_CREDENTIAL_DURATION_SECONDS));
    }
  }
}
