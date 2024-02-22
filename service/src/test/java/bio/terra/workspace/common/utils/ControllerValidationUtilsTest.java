package bio.terra.workspace.common.utils;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.common.BaseSpringBootUnitTest;
import org.junit.jupiter.api.Test;

public class ControllerValidationUtilsTest extends BaseSpringBootUnitTest {

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
}
