package bio.terra.workspace.app.configuration.external;

import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("azure")
public class AzureStateEnabledTest extends BaseUnitTest {

  @Autowired private AzureState azureState;

  @Test
  void azureIsEnableTest() {
    assertTrue(azureState.isEnabled(), "azure is enabled");
  }
}
