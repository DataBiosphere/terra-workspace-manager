package bio.terra.workspace.service.workspace.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.generated.model.ApiJobReport.StatusEnum;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.AsyncJobResult;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

public class RemoveUserFromWorkspaceFlightTest extends BaseConnectedTest {

  @Autowired private WorkspaceService workspaceService;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private JobService jobService;
  @Autowired private SamService samService;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private UserAccessUtils userAccessUtils;

  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(3);

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void removeUserFromWorkspaceFlightDoUndo() throws Exception {
    // Create a workspace as the default test user
    WorkspaceRequest request =
        WorkspaceRequest.builder()
            .workspaceId(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .spendProfileId(Optional.of(spendUtils.defaultSpendId()))
            .build();
    UUID workspaceId =
        workspaceService.createWorkspace(request, userAccessUtils.defaultUserAuthRequest());
    // Add the secondary test user as a writer
    samService.grantWorkspaceRole(
        workspaceId,
        userAccessUtils.defaultUserAuthRequest(),
        WsmIamRole.WRITER,
        userAccessUtils.getSecondUserEmail());

    // Create a GCP context as default user
    String makeContextJobId = RandomStringUtils.randomAlphabetic(8);
    workspaceService.createGcpCloudContext(
        workspaceId, makeContextJobId, userAccessUtils.defaultUserAuthRequest());
    jobService.waitForJob(makeContextJobId);
    AsyncJobResult<GcpCloudContext> createContextJobResult =
        jobService.retrieveAsyncJobResult(
            makeContextJobId, GcpCloudContext.class, userAccessUtils.defaultUserAuthRequest());
    assertEquals(StatusEnum.SUCCEEDED, createContextJobResult.getJobReport().getStatus());

    // Create a private dataset for secondary user
    String datasetId = RandomStringUtils.randomAlphabetic(8);
    ControlledBigQueryDatasetResource privateDataset = buildPrivateDataset(workspaceId, datasetId);
    assertNotNull(privateDataset);

    // Validate with Sam that secondary user can read their private resource
    assertTrue(
        samService.isAuthorized(
            userAccessUtils.secondUserAuthRequest(),
            privateDataset.getCategory().getSamResourceName(),
            privateDataset.getResourceId().toString(),
            SamControlledResourceActions.WRITE_ACTION));

    // Run the "removeUser" flight to the very end, then undo it, retrying steps along the way.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(RemoveUserFromSamStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        CheckUserStillInWorkspaceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        ReadUserPrivateResourcesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        RemovePrivateResourceAccessStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        RevokePetUsagePermissionStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo failingDebugInfo =
        FlightDebugInfo.newBuilder().undoStepFailures(retrySteps).lastStepFailure(true).build();

    FlightMap inputParameters = new FlightMap();
    inputParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId.toString());
    inputParameters.put(
        WorkspaceFlightMapKeys.USER_TO_REMOVE, userAccessUtils.getSecondUserEmail());
    inputParameters.put(
        WorkspaceFlightMapKeys.ROLE_TO_REMOVE, ControlledResourceIamRole.WRITER.name());
    // Auth info comes from default user, as they are the ones "making this request"
    inputParameters.put(
        JobMapKeys.AUTH_USER_INFO.getKeyName(), userAccessUtils.defaultUserAuthRequest());
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            RemoveUserFromWorkspaceFlight.class,
            inputParameters,
            STAIRWAY_FLIGHT_TIMEOUT,
            failingDebugInfo);
    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());

    // Validate that secondary user is still a workspace writer and can still read their private
    // resource.
    assertTrue(
        samService.isAuthorized(
            userAccessUtils.secondUserAuthRequest(),
            SamConstants.SAM_WORKSPACE_RESOURCE,
            workspaceId.toString(),
            SamConstants.SAM_WORKSPACE_WRITE_ACTION));
    assertTrue(
        samService.isAuthorized(
            userAccessUtils.secondUserAuthRequest(),
            privateDataset.getCategory().getSamResourceName(),
            privateDataset.getResourceId().toString(),
            SamControlledResourceActions.WRITE_ACTION));

    // Run the flight again, this time to success. Retry each do step once.
    FlightDebugInfo passingDebugInfo =
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build();
    FlightState passingFlightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            RemoveUserFromWorkspaceFlight.class,
            inputParameters,
            STAIRWAY_FLIGHT_TIMEOUT,
            passingDebugInfo);
    assertEquals(FlightStatus.SUCCESS, passingFlightState.getFlightStatus());

    // Verify the secondary user can no longer access the workspace or their private resource
    assertFalse(
        samService.isAuthorized(
            userAccessUtils.secondUserAuthRequest(),
            SamConstants.SAM_WORKSPACE_RESOURCE,
            workspaceId.toString(),
            SamConstants.SAM_WORKSPACE_WRITE_ACTION));
    assertFalse(
        samService.isAuthorized(
            userAccessUtils.secondUserAuthRequest(),
            privateDataset.getCategory().getSamResourceName(),
            privateDataset.getResourceId().toString(),
            SamControlledResourceActions.WRITE_ACTION));

    // Cleanup
    workspaceService.deleteWorkspace(workspaceId, userAccessUtils.defaultUserAuthRequest());
  }

  private ControlledBigQueryDatasetResource buildPrivateDataset(
      UUID workspaceId, String datasetName) {
    ControlledBigQueryDatasetResource datasetToCreate =
        ControlledBigQueryDatasetResource.builder()
            .workspaceId(workspaceId)
            .resourceId(UUID.randomUUID())
            .name(datasetName)
            .cloningInstructions(CloningInstructions.COPY_NOTHING)
            .assignedUser(userAccessUtils.getSecondUserEmail())
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .datasetName(datasetName)
            .build();
    ApiGcpBigQueryDatasetCreationParameters datasetCreationParameters =
        new ApiGcpBigQueryDatasetCreationParameters()
            .datasetId(datasetName)
            .location("us-central1");
    List<ControlledResourceIamRole> privateRoles =
        ImmutableList.of(ControlledResourceIamRole.WRITER, ControlledResourceIamRole.EDITOR);
    return controlledResourceService.createBigQueryDataset(
        datasetToCreate,
        datasetCreationParameters,
        privateRoles,
        userAccessUtils.secondUserAuthRequest());
  }
}
