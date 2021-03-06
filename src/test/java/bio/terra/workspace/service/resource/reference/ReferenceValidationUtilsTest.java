package bio.terra.workspace.service.resource.reference;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.reference.exception.InvalidReferenceException;
import org.junit.jupiter.api.Test;

public class ReferenceValidationUtilsTest extends BaseUnitTest {

  @Test
  public void testInvalidCharInDataRepoName() {
    assertThrows(
        InvalidReferenceException.class,
        () -> ValidationUtils.validateDataRepoName("invalid@@@name"));
  }

  @Test
  public void validCharInDataRepoNameOk() {
    ValidationUtils.validateDataRepoName("valid_name");
  }

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

  @Test
  public void testEmptyDataRepoName() {
    assertThrows(InvalidReferenceException.class, () -> ValidationUtils.validateDataRepoName(""));
  }
}
