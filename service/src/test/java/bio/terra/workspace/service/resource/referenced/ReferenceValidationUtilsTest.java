package bio.terra.workspace.service.resource.referenced;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import org.junit.jupiter.api.Test;

public class ReferenceValidationUtilsTest extends BaseUnitTest {

  @Test
  public void testInvalidCharInBucketName() {
    assertThrows(
        InvalidReferenceException.class,
        () -> ValidationUtils.validateBucketName("INVALIDBUCKETNAME"));
  }

  @Test
  public void validBucketNameOk() {
    ValidationUtils.validateBucketName("valid-bucket_name.1");
  }

  @Test
  public void testInvalidCharInBqDatasetName() {
    assertThrows(
        InvalidReferenceException.class,
        () -> ValidationUtils.validateBqDatasetName("invalid-name-for-dataset"));
  }

  @Test
  public void validBqDatasetNameOk() {
    ValidationUtils.validateBqDatasetName("valid_bigquery_name_1");
  }
}
