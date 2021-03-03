package bio.terra.workspace.service.datareference;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.resource.reference.exception.InvalidReferenceException;
import bio.terra.workspace.service.resource.ValidationUtils;
import org.junit.jupiter.api.Test;

public class DataReferenceValidationUtilsTest extends BaseUnitTest {

  @Test
  public void testInvalidCharInReferenceName() {
    assertThrows(
        InvalidReferenceException.class,
        () -> ValidationUtils.validateReferenceName("invalid@@@name"));
  }

  @Test
  public void validCharInReferenceNameOk() {
    ValidationUtils.validateReferenceName("valid_name");
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
        () -> ValidationUtils.validateReferenceName("invalid-name-for-dataset"));
  }

  @Test
  public void validBqDatasetNameOk() {
    ValidationUtils.validateBqDatasetName("valid_bigquery_name_1");
  }

  @Test
  public void testEmptyReferenceName() {
    assertThrows(
        InvalidReferenceException.class,
        () -> ValidationUtils.validateReferenceName(""));
  }
}
