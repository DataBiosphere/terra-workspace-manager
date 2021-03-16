package bio.terra.workspace.service.status;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import bio.terra.workspace.app.configuration.external.DataRepoConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiSystemStatusSystems;
import bio.terra.workspace.service.buffer.BufferService;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.SamService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class WorkspaceManagerStatusServiceTest extends BaseUnitTest {

  @MockBean private DataRepoService mockDataRepoService;
  @MockBean private DataRepoConfiguration mockDataRepoConfiguration;
  @MockBean private SamService mockSamService;
  @MockBean private BufferService mockBufferService;

  @Autowired private WorkspaceManagerStatusService statusService;

  @BeforeEach
  public void setup() {
    ApiSystemStatusSystems passingStatus = new ApiSystemStatusSystems().ok(true);
    doReturn(passingStatus).when(mockDataRepoService).status(any());
    doReturn(new ApiSystemStatusSystems().ok(true)).when(mockSamService).status();
    doReturn(passingStatus).when(mockBufferService).status();
    // Although we mock out the DataRepoConfig, it's only used in the StatusService's constructor.
    // Beans get autowired (meaning that constructor gets called) before this method, so there's
    // no way to configure the return values of mockDataRepoConfig methods. Instead, they'll just
    // return default values.
  }

  @Test
  void testStatusWithWorkingEndpoints() {
    // Manually check subsystems, since @Scheduled doesn't work nicely in unit tests.
    statusService.checkSubsystems();
    assertTrue(statusService.getCurrentStatus().isOk());
  }

  @Test
  void testCriticalFailureNotOk() {
    doReturn(new ApiSystemStatusSystems().ok(false).addMessagesItem("Sam is kill"))
        .when(mockSamService)
        .status();
    // Manually check subsystems, since @Scheduled doesn't work nicely in unit tests.
    statusService.checkSubsystems();
    assertFalse(statusService.getCurrentStatus().isOk());
    Map<String, ApiSystemStatusSystems> subsystemStatus =
        statusService.getCurrentStatus().getSystems();
    assertFalse(subsystemStatus.get("Sam").isOk());
  }

  @Test
  void testBufferCriticalFailureNotOk() {
    doReturn(new ApiSystemStatusSystems().ok(false).addMessagesItem("Buffer down"))
        .when(mockBufferService)
        .status();
    // Manually check subsystems, since @Scheduled doesn't work nicely in unit tests.
    statusService.checkSubsystems();
    assertFalse(statusService.getCurrentStatus().isOk());
    Map<String, ApiSystemStatusSystems> subsystemStatus =
        statusService.getCurrentStatus().getSystems();
    assertFalse(subsystemStatus.get("Buffer").isOk());
  }
}
