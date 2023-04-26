package bio.terra.workspace.service.resource;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.resource.exception.InvalidNameException;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import org.junit.jupiter.api.Test;

public class AwsResourceValidationUtilsTest extends BaseUnitTest {

  @Test
  public void awsS3StorageFolderName() {
    AwsResourceValidationUtils.validateAwsS3StorageFolderName("valid-s3-storage-folder");

    for (char c : AwsResourceValidationUtils.s3ObjectDisallowedChars.pattern().toCharArray()) {
      assertThrows(
          InvalidNameException.class,
          () -> AwsResourceValidationUtils.validateAwsS3StorageFolderName("a" + c + "b"),
          String.format("Character %c expected to be invalid", c));
    }

    assertThrows(
        InvalidReferenceException.class,
        () -> ResourceValidationUtils.validateAiNotebookInstanceId(""));
    assertThrows(
        InvalidReferenceException.class,
        () -> ResourceValidationUtils.validateAiNotebookInstanceId("a".repeat(2050)));
  }
}
