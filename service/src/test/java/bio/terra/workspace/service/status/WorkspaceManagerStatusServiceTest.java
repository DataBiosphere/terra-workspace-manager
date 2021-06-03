package bio.terra.workspace.service.status;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import bio.terra.workspace.app.configuration.external.StatusCheckConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.iam.SamService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class WorkspaceManagerStatusServiceTest extends BaseUnitTest {

  @MockBean private SamService mockSamService;

  @Autowired private WorkspaceManagerStatusService statusService;
  @Autowired private StatusCheckConfiguration configuration;

  private static final Boolean IS_OK = true;
  private static final Boolean NOT_OK = false;

  private boolean enabledSetting;

  @BeforeEach
  private void setup() {
    // Enable status checking. This won't start the scheduled thread, since that is done
    // post construction, but it allows us to manually test the code.
    enabledSetting = configuration.isEnabled();
    configuration.setEnabled(true);
  }

  @AfterEach
  private void teardown() {
    // Restore the config setting
    configuration.setEnabled(enabledSetting);
  }

  @Test
  void testStatusWithWorkingEndpoints() {
    doReturn(IS_OK).when(mockSamService).status();
    statusService.checkStatus();
    assertTrue(statusService.getCurrentStatus());
  }

  @Test
  void testCriticalFailureNotOk() {
    doReturn(NOT_OK).when(mockSamService).status();
    statusService.checkStatus();
    assertFalse(statusService.getCurrentStatus());
  }
}
