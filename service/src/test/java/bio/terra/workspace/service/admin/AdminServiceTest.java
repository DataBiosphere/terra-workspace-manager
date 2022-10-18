package bio.terra.workspace.service.admin;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.workspace.flight.cloudcontext.gcp.GcpIamCustomRolePatchStep;
import bio.terra.workspace.service.workspace.flight.cloudcontext.gcp.RetrieveGcpIamCustomRoleStep;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class AdminServiceTest extends BaseConnectedTest {

  @Autowired AdminService adminService;
  @Autowired JobService jobService;
  @Autowired WorkspaceDao workspaceDao;
  @Autowired WorkspaceConnectedTestUtils connectedTestUtils;
  @Autowired UserAccessUtils userAccessUtils;

  private Workspace workspace1;
  private Workspace workspace2;
  private Workspace workspace3;

  @BeforeEach
  void setup() {
    workspace1 = connectedTestUtils.createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest());
    workspace2 = connectedTestUtils.createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest());
    workspace3 = connectedTestUtils.createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest());
  }

  @Test
  public void syncIamRole() {
    // Test idempotency of steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(RetrieveGcpIamCustomRoleStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        GcpIamCustomRolePatchStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    adminService.syncIamRoleForAllGcpProjects(userAccessUtils.defaultUserAuthRequest());
  }

  @Test
  public void syncIamRole_undo() {
    // Test idempotency of steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(RetrieveGcpIamCustomRoleStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        GcpIamCustomRolePatchStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).lastStepFailure(true).build());

    // Service methods which wait for a flight to complete will throw an
    // InvalidResultStateException when that flight fails without a cause, which occurs when a
    // flight fails via debugInfo.
    assertThrows(
        InvalidResultStateException.class,
        () ->
        adminService.syncIamRoleForAllGcpProjects(userAccessUtils.defaultUserAuthRequest()));
  }
}
