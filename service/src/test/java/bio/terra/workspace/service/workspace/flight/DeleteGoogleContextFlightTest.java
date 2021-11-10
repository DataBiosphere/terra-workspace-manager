package bio.terra.workspace.service.workspace.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
  @Autowired private GcpCloudContextService gcpCloudContextService;
  @Autowired private CrlService crl;
  @Autowired private JobService jobService;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private UserAccessUtils userAccessUtils;

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void deleteContextDo() throws Exception {
    UUID workspaceId = createWorkspace();
    FlightMap createParameters = new FlightMap();
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    createParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId.toString());
    createParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    // Create the google context.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateGcpContextFlight.class,
            createParameters,
            CREATION_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    String projectId =
        workspaceService.getAuthorizedGcpProject(workspaceId, userRequest).orElse(null);
    assertNotNull(projectId);

    // validate that required project does not throw and gives the same answer
    String projectId2 = workspaceService.getAuthorizedRequiredGcpProject(workspaceId, userRequest);
    assertEquals(projectId, projectId2);

    Project project = crl.getCloudResourceManagerCow().projects().get(projectId).execute();
    assertEquals("ACTIVE", project.getState());

    // Delete the google context.
    FlightMap deleteParameters = new FlightMap();
    deleteParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId.toString());

    // Force each step to be retried once to ensure proper behavior.
    Map<String, StepStatus> doFailures = new HashMap<>();
    doFailures.put(
        DeleteControlledSamResourcesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(
        DeleteControlledDbResourcesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(DeleteGcpProjectStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(DeleteGcpContextStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(doFailures).build();

    flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteGcpContextFlight.class,
            deleteParameters,
            DELETION_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    assertTrue(workspaceService.getAuthorizedGcpCloudContext(workspaceId, userRequest).isEmpty());

    // make sure required really requires
    assertThrows(
        CloudContextRequiredException.class,
        () -> workspaceService.getAuthorizedRequiredGcpProject(workspaceId, userRequest));

    project = crl.getCloudResourceManagerCow().projects().get(projectId).execute();
    assertEquals("DELETE_REQUESTED", project.getState());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void deleteContextUndo() throws Exception {
    UUID workspaceId = createWorkspace();
    FlightMap createParameters = new FlightMap();
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    createParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId.toString());
    createParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    // Create the google context.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateGcpContextFlight.class,
            createParameters,
            CREATION_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // Delete the google context.
    FlightMap deleteParameters = new FlightMap();
    deleteParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId.toString());

    // Fail at the end of the flight to verify it can't be undone.
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().lastStepFailure(true).build();

    flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteGcpContextFlight.class,
            deleteParameters,
            DELETION_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.FATAL, flightState.getFlightStatus());

    // Because this flight cannot be undone, the context should still be deleted even after undoing.
    assertTrue(workspaceService.getAuthorizedGcpCloudContext(workspaceId, userRequest).isEmpty());
  }

  @Test
  void deleteNonExistentContextIsOk() throws Exception {
    UUID workspaceId = createWorkspace();
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    assertTrue(workspaceService.getAuthorizedGcpCloudContext(workspaceId, userRequest).isEmpty());

    FlightMap inputParameters = new FlightMap();
    inputParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId.toString());
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteGcpContextFlight.class,
            inputParameters,
            DELETION_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
    assertTrue(workspaceService.getAuthorizedGcpCloudContext(workspaceId, userRequest).isEmpty());
  }

  /** Creates a workspace, returning its workspaceId. */
  private UUID createWorkspace() {
    WorkspaceRequest request =
        WorkspaceRequest.builder()
            .workspaceId(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .spendProfileId(Optional.of(spendUtils.defaultSpendId()))
            .build();
    return workspaceService.createWorkspace(request, userAccessUtils.defaultUserAuthRequest());
  }
}
