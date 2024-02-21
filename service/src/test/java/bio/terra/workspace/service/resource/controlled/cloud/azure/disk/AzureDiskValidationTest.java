package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.workspace.common.BaseAzureSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import org.junit.jupiter.api.Test;

public class AzureDiskValidationTest extends BaseAzureSpringBootUnitTest {

  @Test
  void validateInvalidDiskSize() {
    assertThrows(
        InconsistentFieldsException.class,
        () ->
            ControlledAzureResourceFixtures.getAzureDisk(
                "test", ControlledAzureResourceFixtures.DEFAULT_AZURE_RESOURCE_REGION, 0));
  }
}
