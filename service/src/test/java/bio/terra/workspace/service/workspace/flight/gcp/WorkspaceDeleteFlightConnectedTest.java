package bio.terra.workspace.service.workspace.flight.gcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.delete.workspace.DeleteWorkspaceAuthzStep;
import bio.terra.workspace.service.workspace.flight.delete.workspace.DeleteWorkspaceFinishStep;
import bio.terra.workspace.service.workspace.flight.delete.workspace.DeleteWorkspaceStartStep;
import bio.terra.workspace.service.workspace.flight.delete.workspace.RunDeleteCloudContextFlightStep;
import bio.terra.workspace.service.workspace.flight.delete.workspace.WorkspaceDeleteFlight;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("connectedPlus")
public class WorkspaceDeleteFlightConnectedTest extends BaseConnectedTest {
  /**
   * How long to wait for a delete context Stairway flight to complete before timing out the test.
   */
  private static final Duration DELETION_FLIGHT_TIMEOUT = Duration.ofMinutes(3);

  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private WorkspaceConnectedTestUtils connectedTestUtils;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private JobService jobService;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private GcpCloudContextService gcpCloudContextService;

  // TODO(BENCH-845): Autowire samService to debug flaky tests
  // samService is not directly used in the test. Autowire it to ensure it is ready before tests
  @Autowired private SamService samService;

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void deleteMcWorkspaceWithResource() throws Exception {
    // Create a workspace with a controlled resource
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    Workspace workspace = connectedTestUtils.createWorkspaceWithGcpContext(userRequest);
    UUID workspaceId = workspace.getWorkspaceId();
    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceId);

    ControlledBigQueryDatasetResource dataset =
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceId)
            .projectId(projectId)
            .build();

    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        ControlledGcpResourceFixtures.defaultBigQueryDatasetCreationParameters()
            .datasetId(dataset.getDatasetName());
    controlledResourceService
        .createControlledResourceSync(dataset, null, userRequest, creationParameters)
        .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);

    ControlledBigQueryDatasetResource gotResource =
        controlledResourceService
            .getControlledResource(workspace.getWorkspaceId(), dataset.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    assertTrue(dataset.partialEqual(gotResource));

    // Run the delete flight, retrying every step once
    FlightMap deleteParameters = new FlightMap();
    deleteParameters.put(
        WorkspaceFlightMapKeys.WORKSPACE_ID, workspace.getWorkspaceId().toString());
    deleteParameters.put(
        WorkspaceFlightMapKeys.WORKSPACE_STAGE, workspace.getWorkspaceStage().name());
    deleteParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    Map<String, StepStatus> doFailures = new HashMap<>();
    doFailures.put(DeleteWorkspaceStartStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(
        RunDeleteCloudContextFlightStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(DeleteWorkspaceAuthzStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    doFailures.put(DeleteWorkspaceFinishStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(doFailures).build();

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            WorkspaceDeleteFlight.class,
            deleteParameters,
            DELETION_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // Verify the resource and workspace are not in WSM DB
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                dataset.getWorkspaceId(), dataset.getResourceId()));
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(workspace.getWorkspaceId()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void cannotUndoWorkspaceDelete() throws Exception {
    // Create a workspace with a controlled resource
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    Workspace workspace = connectedTestUtils.createWorkspaceWithGcpContext(userRequest);
    UUID workspaceId = workspace.getWorkspaceId();
    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceId);

    ControlledBigQueryDatasetResource dataset =
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceId)
            .projectId(projectId)
            .build();
    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        ControlledGcpResourceFixtures.defaultBigQueryDatasetCreationParameters()
            .datasetId(dataset.getDatasetName());
    controlledResourceService
        .createControlledResourceSync(dataset, null, userRequest, creationParameters)
        .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);

    ControlledBigQueryDatasetResource gotResource =
        controlledResourceService
            .getControlledResource(workspace.getWorkspaceId(), dataset.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    assertTrue(dataset.partialEqual(gotResource));

    FlightMap deleteParameters = new FlightMap();
    deleteParameters.put(
        WorkspaceFlightMapKeys.WORKSPACE_ID, workspace.getWorkspaceId().toString());
    deleteParameters.put(
        WorkspaceFlightMapKeys.WORKSPACE_STAGE, workspace.getWorkspaceStage().name());
    deleteParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    // Deletion steps can't be undone, so if the flight fails at the end, this should be marked as
    // a dismal failure and the deletion should persist.
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().lastStepFailure(true).build();
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            WorkspaceDeleteFlight.class,
            deleteParameters,
            DELETION_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.FATAL, flightState.getFlightStatus());

    // Verify the resource and workspace are still deleted, as delete steps have no undo.
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                dataset.getWorkspaceId(), dataset.getResourceId()));
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(workspace.getWorkspaceId()));
  }
}
