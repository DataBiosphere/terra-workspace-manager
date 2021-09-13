package bio.terra.workspace.app.configuration.external;

import static org.junit.jupiter.api.Assertions.assertFalse;

import bio.terra.workspace.common.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class AzureStateDisabledTest extends BaseUnitTest {

  @Autowired private AzureState azureState;

  @Test
  void azureIsEnableTest() {
    assertFalse(azureState.isEnabled(), "azure is not enabled");
  }
}
