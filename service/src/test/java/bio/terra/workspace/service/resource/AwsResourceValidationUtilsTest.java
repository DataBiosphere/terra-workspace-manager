package bio.terra.workspace.service.resource;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseAwsUnitTest;
import bio.terra.workspace.service.resource.controlled.cloud.aws.AwsResourceConstants;
import bio.terra.workspace.service.resource.exception.InvalidNameException;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import org.junit.jupiter.api.Test;

public class AwsResourceValidationUtilsTest extends BaseAwsUnitTest {

  @Test
  public void awsS3StorageFolderName() {
    AwsResourceValidationUtils.validateAwsS3StorageFolderName("valid-s3-storage-folder");

    for (char c : AwsResourceValidationUtils.s3ObjectDisallowedChars.pattern().toCharArray()) {
      assertThrows(
          InvalidNameException.class,
          () -> AwsResourceValidationUtils.validateAwsS3StorageFolderName("a" + c + "b"),
          String.format("Strings with character %c expected to be invalid", c));
    }

    assertThrows(
        InvalidNameException.class,
        () -> AwsResourceValidationUtils.validateAwsS3StorageFolderName(""),
        "Empty string expected to be invalid");
    assertThrows(
        InvalidNameException.class,
        () ->
            AwsResourceValidationUtils.validateAwsS3StorageFolderName(
                "a".repeat(AwsResourceConstants.MAX_S3_STORAGE_FOLDER_NAME_LENGTH) + 1),
        String.format(
            "Strings longer than %d characters expected to be invalid",
            AwsResourceConstants.MAX_S3_STORAGE_FOLDER_NAME_LENGTH));
  }

  @Test
  public void awsSagemakerNotebookName() {
    AwsResourceValidationUtils.validateAwsSagemakerNotebookName("valid-s3-sagemaker-instance");

    assertThrows(
        InvalidNameException.class,
        () -> AwsResourceValidationUtils.validateAwsSagemakerNotebookName("-starts-with-dash"),
        "Strings starting with dash expected to be invalid");

    assertThrows(
        InvalidNameException.class,
        () -> AwsResourceValidationUtils.validateAwsSagemakerNotebookName("ends-with-dash-"),
        "Strings ending with dash expected be invalid");

    assertThrows(
        InvalidNameException.class,
        () -> AwsResourceValidationUtils.validateAwsSagemakerNotebookName("with_underscore"),
        "Strings outside pattern \"^[a-zA-Z0-9](-*[a-zA-Z0-9])*\" expected to be invalid");

    assertThrows(
        InvalidNameException.class,
        () -> AwsResourceValidationUtils.validateAwsSagemakerNotebookName("-"),
        "Empty string expected to be invalid");
    assertThrows(
        InvalidNameException.class,
        () ->
            AwsResourceValidationUtils.validateAwsSagemakerNotebookName(
                "a".repeat(AwsResourceConstants.MAX_SAGEMAKER_NOTEBOOK_INSTANCE_NAME_LENGTH) + 1),
        String.format(
            "Strings longer than %d characters expected to be invalid",
            AwsResourceConstants.MAX_SAGEMAKER_NOTEBOOK_INSTANCE_NAME_LENGTH));
  }

  @Test
  public void awsCredentialDurationSecond() {
    assertThrows(
        InvalidNameException.class,
        () -> AwsResourceValidationUtils.validateAwsCredentialDurationSecond(9),
        String.format(
            "Duration less than %d expected to be invalid",
            AwsResourceConstants.MIN_CREDENTIAL_DURATION_SECONDS));
    assertThrows(
        InvalidNameException.class,
        () -> AwsResourceValidationUtils.validateAwsCredentialDurationSecond(99999),
        String.format(
            "Duration more than %d expected to be invalid",
            AwsResourceConstants.MAX_CREDENTIAL_DURATION_SECONDS));
  }
}
