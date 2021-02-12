package bio.terra.workspace.service.datareference;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import bio.terra.workspace.service.datareference.utils.DataReferenceValidationUtils;
import org.junit.jupiter.api.Test;

public class DataReferenceValidationUtilsTest extends BaseUnitTest {

  @Test
  public void testInvalidCharInReferenceName() {
    assertThrows(
        InvalidDataReferenceException.class,
        () -> DataReferenceValidationUtils.validateReferenceName("invalid@@@name"));
  }

  @Test
  public void validCharInReferenceNameOk() {
    DataReferenceValidationUtils.validateReferenceName("valid_name");
  }

  @Test
  public void testInvalidCharInBucketName() {
    assertThrows(
        InvalidDataReferenceException.class,
        () -> DataReferenceValidationUtils.validateBucketName("INVALIDBUCKETNAME"));
  }

  @Test
  public void validBucketNameOk() {
    DataReferenceValidationUtils.validateBucketName("valid-bucket_name.1");
  }

  @Test
  public void testInvalidCharInBqDatasetName() {
    assertThrows(
        InvalidDataReferenceException.class,
        () -> DataReferenceValidationUtils.validateReferenceName("invalid-name-for-dataset"));
  }

  @Test
  public void validBqDatasetNameOk() {
    DataReferenceValidationUtils.validateBqDatasetName("valid_bigquery_name_1");
  }

  @Test
  public void testEmptyReferenceName() {
    assertThrows(
        InvalidDataReferenceException.class,
        () -> DataReferenceValidationUtils.validateReferenceName(""));
  }
}
