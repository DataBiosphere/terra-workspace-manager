package bio.terra.workspace.service.workspace.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.google.api.services.cloudresourcemanager.model.Project;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

class DeleteGoogleContextFlightTest extends BaseConnectedTest {
  /**
   * How long to wait for a delete context Stairway flight to complete before timing out the test.
   */
  private static final Duration DELETION_FLIGHT_TIMEOUT = Duration.ofMinutes(3);
  /**
   * How long to wait for a create context Stairway flight to complete before timing out the test.
   */
  private static final Duration CREATION_FLIGHT_TIMEOUT = Duration.ofMinutes(3);

  @Autowired private WorkspaceService workspaceService;
  @Autowired private CrlService crl;
  @Autowired private JobService jobService;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private UserAccessUtils userAccessUtils;

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = bufferServiceDisabledEnvsRegEx)
  void deleteContext() throws Exception {
    UUID workspaceId = createWorkspace();
    FlightMap createParameters = new FlightMap();
    AuthenticatedUserRequest userReq = userAccessUtils.defaultUserAuthRequest();
    createParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId);
    createParameters.put(
        WorkspaceFlightMapKeys.BILLING_ACCOUNT_ID, spendUtils.defaultBillingAccountId());
    createParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userReq);

    // Create the google context.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateGcpContextFlight.class,
            createParameters,
            CREATION_FLIGHT_TIMEOUT);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    Workspace workspace = workspaceService.getWorkspace(workspaceId, userReq);
    String projectId =
        workspace.getGcpCloudContext().map(GcpCloudContext::getGcpProjectId).orElse(null);
    assertNotNull(projectId);

    Project project = crl.getCloudResourceManagerCow().projects().get(projectId).execute();
    assertEquals("ACTIVE", project.getLifecycleState());

    // Delete the google context.
    FlightMap deleteParameters = new FlightMap();
    deleteParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId);
    flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteGcpContextFlight.class,
            deleteParameters,
            DELETION_FLIGHT_TIMEOUT);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    workspace = workspaceService.getWorkspace(workspaceId, userReq);
    assertTrue(workspace.getGcpCloudContext().isEmpty());

    project = crl.getCloudResourceManagerCow().projects().get(projectId).execute();
    assertEquals("DELETE_REQUESTED", project.getLifecycleState());
  }

  @Test
  void deleteNonExistentContextIsOk() throws Exception {
    UUID workspaceId = createWorkspace();
    AuthenticatedUserRequest userReq = userAccessUtils.defaultUserAuthRequest();
    Workspace workspace = workspaceService.getWorkspace(workspaceId, userReq);
    assertTrue(workspace.getGcpCloudContext().isEmpty());

    FlightMap inputParameters = new FlightMap();
    inputParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId);
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteGcpContextFlight.class,
            inputParameters,
            DELETION_FLIGHT_TIMEOUT);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    workspace = workspaceService.getWorkspace(workspaceId, userReq);
    assertTrue(workspace.getGcpCloudContext().isEmpty());
  }

  /** Creates a workspace, returning its workspaceId. */
  private UUID createWorkspace() {
    WorkspaceRequest request =
        WorkspaceRequest.builder()
            .workspaceId(UUID.randomUUID())
            .jobId(UUID.randomUUID().toString())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    return workspaceService.createWorkspace(request, userAccessUtils.defaultUserAuthRequest());
  }
}
