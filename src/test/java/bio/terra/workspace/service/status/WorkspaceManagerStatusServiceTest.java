package bio.terra.workspace.service.status;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.app.configuration.external.DataRepoConfiguration;
import bio.terra.workspace.generated.model.SystemStatusSystems;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.SamService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(profiles = "test")
public class WorkspaceManagerStatusServiceTest {

  @MockBean private DataRepoService mockDataRepoService;
  @MockBean private DataRepoConfiguration mockDataRepoConfiguration;
  @MockBean private SamService mockSamService;

  @Autowired private WorkspaceManagerStatusService statusService;

  @BeforeEach
  public void setup() {
    SystemStatusSystems passingStatus = new SystemStatusSystems().ok(true);
    doReturn(passingStatus).when(mockDataRepoService).status(any());
    doReturn(new SystemStatusSystems().ok(true)).when(mockSamService).status();
    // Although we mock out the DataRepoConfig, it's only used in the StatusService's constructor.
    // Beans get autowired (meaning that constructor gets called) before this method, so there's
    // no way to configure the return values of mockDataRepoConfig methods. Instead, they'll just
    // return default values.
  }

  @Test
  public void testStatusWithWorkingEndpoints() throws Exception {
    // Manually check subsystems, since @Scheduled doesn't work nicely in unit tests.
    statusService.checkSubsystems();
    assertTrue(statusService.getCurrentStatus().isOk());
  }

  @Test
  public void testCriticalFailureNotOk() throws Exception {
    doReturn(new SystemStatusSystems().ok(false).addMessagesItem("Sam is kill"))
        .when(mockSamService)
        .status();
    // Manually check subsystems, since @Scheduled doesn't work nicely in unit tests.
    statusService.checkSubsystems();
    assertFalse(statusService.getCurrentStatus().isOk());
    Map<String, SystemStatusSystems> subsystemStatus =
        statusService.getCurrentStatus().getSystems();
    assertFalse(subsystemStatus.get("Sam").isOk());
  }
}
