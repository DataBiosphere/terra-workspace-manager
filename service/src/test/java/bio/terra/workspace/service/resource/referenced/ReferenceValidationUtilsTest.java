package bio.terra.workspace.service.resource.referenced;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.resource.GcpResourceValidationUtils;
import bio.terra.workspace.service.resource.exception.InvalidNameException;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import org.junit.jupiter.api.Test;

public class ReferenceValidationUtilsTest extends BaseUnitTest {

  @Test
  public void testInvalidCharInBucketName() {
    assertThrows(
        InvalidNameException.class,
        () ->
            GcpResourceValidationUtils.validateGcsBucketNameAllowsUnderscore("INVALIDBUCKETNAME"));
  }

  @Test
  public void validBucketNameOk() {
    GcpResourceValidationUtils.validateGcsBucketNameAllowsUnderscore("valid-bucket_name.1");
  }

  @Test
  public void testInvalidCharInBqDatasetName() {
    assertThrows(
        InvalidReferenceException.class,
        () -> GcpResourceValidationUtils.validateBqDatasetName("invalid-name-for-dataset"));
  }

  @Test
  public void validBqDatasetNameOk() {
    GcpResourceValidationUtils.validateBqDatasetName("valid_bigquery_name_1");
  }
}
