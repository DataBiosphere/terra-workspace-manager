package bio.terra.workspace.service.workspace.flight.gcp;

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
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.DeleteGcpProjectStep;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFlight;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.BuildAndValidateResourceListStep;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextFinishStep;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextFlight;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextStartStep;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteResourcesStep;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
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
            workspace, null, null, null, userAccessUtils.defaultUserAuthRequest());
  }

  @AfterEach
  public void tearDown() {
    workspaceService.deleteWorkspace(workspace, userAccessUtils.defaultUserAuthRequest());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void deleteContextDo() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    FlightMap createParameters =
        WorkspaceFixtures.createCloudContextInputs(
            workspaceUuid, userRequest, CloudPlatform.GCP, spendUtils.defaultGcpSpendProfile());

    // Create the google context.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateCloudContextFlight.class,
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

    // Delete the GCP context.
    FlightMap deleteParameters =
        WorkspaceFixtures.deleteCloudContextInputs(workspaceUuid, userRequest, CloudPlatform.GCP);

    // Force each step to be retried once to ensure proper behavior.
    Map<String, StepStatus> doFailures = new HashMap<>();
    doFailures.put(
        DeleteCloudContextStartStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(
        BuildAndValidateResourceListStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(DeleteResourcesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(DeleteGcpProjectStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(
        DeleteCloudContextFinishStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(doFailures).build();

    flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteCloudContextFlight.class,
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
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    FlightMap createParameters =
        WorkspaceFixtures.createCloudContextInputs(
            workspaceUuid, userRequest, CloudPlatform.GCP, spendUtils.defaultGcpSpendProfile());

    // Create the GCP context.
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateCloudContextFlight.class,
            createParameters,
            CREATION_FLIGHT_TIMEOUT,
            null);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // Delete the GCP context.
    FlightMap deleteParameters =
        WorkspaceFixtures.deleteCloudContextInputs(workspaceUuid, userRequest, CloudPlatform.GCP);

    // Fail at the end of the flight to verify it can't be undone.
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().lastStepFailure(true).build();

    flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteCloudContextFlight.class,
            deleteParameters,
            DELETION_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.FATAL, flightState.getFlightStatus());

    // Because this flight cannot be undone, the context should still be deleted even after undoing.
    assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceUuid).isEmpty());
  }
}
