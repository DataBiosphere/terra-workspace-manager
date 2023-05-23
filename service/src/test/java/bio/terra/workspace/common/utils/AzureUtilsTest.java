package bio.terra.workspace.common.utils;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.common.BaseAzureUnitTest;
import org.junit.jupiter.api.Test;

public class AzureUtilsTest extends BaseAzureUnitTest {

  @Test
  void validatingValidSasExpirationDurations() {
    AzureUtils.validateSasExpirationDuration(1L, 10L);
    AzureUtils.validateSasExpirationDuration(3600L, 60L);
    AzureUtils.validateSasExpirationDuration(null, 60L);
  }

  @Test
  void validatingInvalidSasExpirationDurations() {
    assertThrows(
        ValidationException.class, () -> AzureUtils.validateSasExpirationDuration(0L, 10L));
    assertThrows(
        ValidationException.class, () -> AzureUtils.validateSasExpirationDuration(-5L, 10L));
    assertThrows(
        ValidationException.class, () -> AzureUtils.validateSasExpirationDuration(3601L, 60L));
  }

  @Test
  void validateSasBlobName() {
    AzureUtils.validateSasBlobName(null);
    AzureUtils.validateSasBlobName("hello");
    assertThrows(ValidationException.class, () -> AzureUtils.validateSasBlobName(""));
    String largeString = "a".repeat(1025);
    assertThrows(ValidationException.class, () -> AzureUtils.validateSasBlobName(largeString));
  }
}
