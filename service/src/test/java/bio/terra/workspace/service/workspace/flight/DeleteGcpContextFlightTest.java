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
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("connectedPlus")
class DeleteGcpContextFlightTest extends BaseConnectedTest {

  /**
   * How long to wait for a delete context Stairway flight to complete before timing out the test.
   */
  private static final Duration DELETION_FLIGHT_TIMEOUT = Duration.ofMinutes(3);
  /**
   * How long to wait for a create context Stairway flight to complete before timing out the test.
   */
  private static final Duration CREATION_FLIGHT_TIMEOUT = Duration.ofMinutes(20);

  @Autowired private WorkspaceService workspaceService;
  @Autowired private WorkspaceConnectedTestUtils workspaceConnectedTestUtils;
  @Autowired private JobService jobService;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private GcpCloudContextService gcpCloudContextService;

  private Workspace workspace;
  private UUID workspaceUuid;

  @BeforeEach
  public void setup() {
    // Create a new workspace at the start of each test.
    UUID uuid = UUID.randomUUID();
    workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(uuid)
            .spendProfileId(spendUtils.defaultSpendId())
            .build();
    workspaceUuid =
        workspaceService.createWorkspace(
            workspace, null, null, userAccessUtils.defaultUserAuthRequest());
  }

  @AfterEach
  public void tearDown() {
    workspaceService.deleteWorkspace(workspace, userAccessUtils.defaultUserAuthRequest());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void deleteContextDo() throws Exception {
    FlightMap createParameters = new FlightMap();
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    createParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    createParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    // Create the google context.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateGcpContextFlightV2.class,
            createParameters,
            CREATION_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    String projectId = gcpCloudContextService.getGcpProject(workspaceUuid).orElse(null);
    assertNotNull(projectId);

    // validate that required project does not throw and gives the same answer
    String projectId2 = gcpCloudContextService.getRequiredGcpProject(workspaceUuid);
    assertEquals(projectId, projectId2);

    workspaceConnectedTestUtils.assertProjectIsActive(projectId);

    // Delete the google context.
    FlightMap deleteParameters = new FlightMap();
    deleteParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());

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

    assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceUuid).isEmpty());

    // make sure required really requires
    assertThrows(
        CloudContextRequiredException.class,
        () -> gcpCloudContextService.getRequiredGcpProject(workspaceUuid));

    workspaceConnectedTestUtils.assertProjectIsBeingDeleted(projectId);
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void deleteContextUndo() throws Exception {
    FlightMap createParameters = new FlightMap();
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    createParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    createParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    // Create the google context.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateGcpContextFlightV2.class,
            createParameters,
            CREATION_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // Delete the google context.
    FlightMap deleteParameters = new FlightMap();
    deleteParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());

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
    assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceUuid).isEmpty());
  }

  @Test
  void deleteNonExistentContextIsOk() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceUuid).isEmpty());

    FlightMap inputParameters = new FlightMap();
    inputParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteGcpContextFlight.class,
            inputParameters,
            DELETION_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
    assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceUuid).isEmpty());
  }
}
