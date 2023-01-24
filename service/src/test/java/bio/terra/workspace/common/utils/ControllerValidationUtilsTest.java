package bio.terra.workspace.common.utils;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiAzureContext;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import org.junit.jupiter.api.Test;

public class ControllerValidationUtilsTest extends BaseUnitTest {

  @Test
  void validatingANullIpAddress() {
    ControllerValidationUtils.validateIpAddressRange(null);
  }

  @Test
  void validatingAnEmptyStringIpAddress() {
    assertThrows(
        ValidationException.class, () -> ControllerValidationUtils.validateIpAddressRange(""));
  }

  @Test
  void validatingAValidSingleIpV4Address() {
    ControllerValidationUtils.validateIpAddressRange("168.1.5.60");
  }

  @Test
  void validatingAnInvalidSingleIpV4Address() {
    assertThrows(
        ValidationException.class,
        () -> ControllerValidationUtils.validateIpAddressRange("168.1.5."));
  }

  @Test
  void validatingAValidIpV4AddressRange() {
    ControllerValidationUtils.validateIpAddressRange("168.1.5.60-168.1.5.70");
  }

  @Test
  void validatingAnInvalidIpV4AddressRange() {
    assertThrows(
        ValidationException.class,
        () -> ControllerValidationUtils.validateIpAddressRange("168.1.5.60-168.1.5."));
  }

  @Test
  void validatingAValidIpV6Address() {
    ControllerValidationUtils.validateIpAddressRange("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
  }

  @Test
  void validatingAValidIpV6ShorthandAddress() {
    ControllerValidationUtils.validateIpAddressRange("2001:0db8:85a3::8a2e:0370:7334");
  }

  @Test
  void validatingAValidIpV6AddressRange() {
    ControllerValidationUtils.validateIpAddressRange(
        "2001:0db8:85a3:0000:0000:8a2e:0370:7334-2001:0db8:85a3:0000:0000:8a2e:0370:7336");
  }

  @Test
  void userFacingIdCanStartWithNumber() {
    ControllerValidationUtils.validateUserFacingId("1000-genomes");
  }

  @Test
  void validatingValidSasExpirationDurations() {
    ControllerValidationUtils.validateSasExpirationDuration(1L, 10L);
    ControllerValidationUtils.validateSasExpirationDuration(3600L, 60L);
    ControllerValidationUtils.validateSasExpirationDuration(null, 60L);
  }

  @Test
  void validatingInvalidSasExpirationDurations() {
    assertThrows(
        ValidationException.class,
        () -> ControllerValidationUtils.validateSasExpirationDuration(0L, 10L));
    assertThrows(
        ValidationException.class,
        () -> ControllerValidationUtils.validateSasExpirationDuration(-5L, 10L));
    assertThrows(
        ValidationException.class,
        () -> ControllerValidationUtils.validateSasExpirationDuration(3601L, 60L));
  }

  @Test
  void validateSasBlobName() {
    ControllerValidationUtils.validateSasBlobName(null);
    ControllerValidationUtils.validateSasBlobName("hello");
    assertThrows(
        ValidationException.class, () -> ControllerValidationUtils.validateSasBlobName(""));
    String largeString = "a".repeat(1025);
    assertThrows(
        ValidationException.class,
        () -> ControllerValidationUtils.validateSasBlobName(largeString));
  }

  @Test
  void validateAzureContextRequestBody() {
    ApiAzureContext apiAzureContext = new ApiAzureContext();
    ControllerValidationUtils.validateAzureContextRequestBody(apiAzureContext, false);
    ControllerValidationUtils.validateAzureContextRequestBody(null, true);
    assertThrows(
        CloudContextRequiredException.class,
        () -> ControllerValidationUtils.validateAzureContextRequestBody(apiAzureContext, true));
    assertThrows(
        CloudContextRequiredException.class,
        () -> ControllerValidationUtils.validateAzureContextRequestBody(null, false));
  }
}
