package bio.terra.workspace.service.status;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import bio.terra.workspace.app.configuration.external.StatusCheckConfiguration;
import bio.terra.workspace.common.BaseSpringBootUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class WorkspaceManagerStatusServiceTest extends BaseSpringBootUnitTest {

  @Autowired private WorkspaceManagerStatusService statusService;
  @Autowired private StatusCheckConfiguration configuration;

  private static final Boolean IS_OK = true;
  private static final Boolean NOT_OK = false;

  private boolean enabledSetting;
  private int stalenessSetting;

  @BeforeEach
  void setup() {
    // Enable status checking. This won't start the scheduled thread, since that is done
    // post construction, but it allows us to manually test the code.
    enabledSetting = configuration.isEnabled();
    configuration.setEnabled(true);

    // Remember and restore staleness
    stalenessSetting = configuration.getStalenessThresholdSeconds();
  }

  @AfterEach
  void teardown() {
    // Restore the config settings
    configuration.setEnabled(enabledSetting);
    configuration.setStalenessThresholdSeconds(stalenessSetting);
  }

  @Test
  void testStatusWithWorkingEndpoints() {
    doReturn(IS_OK).when(mockSamService()).status();
    statusService.checkStatus();
    assertTrue(statusService.getCurrentStatus());
  }

  @Test
  void testFailureNotOk() {
    doReturn(NOT_OK).when(mockSamService()).status();
    statusService.checkStatus();
    assertFalse(statusService.getCurrentStatus());
  }
}
