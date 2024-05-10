package bio.terra.workspace.service.workspace.flight.gcp;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_SPEND_PROFILE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.GcpCloudUtils;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.generated.model.ApiJobReport.StatusEnum;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.iam.model.SamConstants.SamResource;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.petserviceaccount.PetSaService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.spendprofile.SpendProfileService;
import bio.terra.workspace.service.spendprofile.model.SpendProfile;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.RemoveUserFromWorkspaceFlight;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.RevokePetUsagePermissionStep;
import bio.terra.workspace.service.workspace.flight.removeuser.CheckWorkspaceUserActionsStep;
import bio.terra.workspace.service.workspace.flight.removeuser.ClaimUserPrivateResourcesStep;
import bio.terra.workspace.service.workspace.flight.removeuser.MarkPrivateResourcesAbandonedStep;
import bio.terra.workspace.service.workspace.flight.removeuser.ReleasePrivateResourceCleanupClaimsStep;
import bio.terra.workspace.service.workspace.flight.removeuser.RemovePrivateResourceAccessStep;
import bio.terra.workspace.service.workspace.flight.removeuser.RemoveUserFromSamStep;
import bio.terra.workspace.service.workspace.flight.removeuser.ValidateUserRoleStep;
import bio.terra.workspace.service.workspace.model.CloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.iam.v1.Iam;
import com.google.api.services.iam.v1.model.TestIamPermissionsRequest;
import com.google.api.services.iam.v1.model.TestIamPermissionsResponse;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("connectedPlus")
public class RemoveUserFromWorkspaceFlightTest extends BaseConnectedTest {
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(5);
  @Autowired private WorkspaceService workspaceService;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private JobService jobService;
  @Autowired private JobApiUtils jobApiUtils;
  @Autowired private SamService samService;
  @Autowired private PetSaService petSaService;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private WorkspaceConnectedTestUtils connectedTestUtils;
  @Autowired private SpendProfileService spendProfileService;
  @Autowired private FeatureConfiguration features;

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void removeUserFromWorkspaceFlightDoUndo() throws Exception {
    // Create a workspace as the default test user
    Workspace workspace =
        connectedTestUtils.createWorkspace(userAccessUtils.defaultUserAuthRequest());
    UUID workspaceUuid = workspace.getWorkspaceId();
    // Add the secondary test user as a writer
    samService.grantWorkspaceRole(
        workspaceUuid,
        userAccessUtils.defaultUserAuthRequest(),
        WsmIamRole.WRITER,
        userAccessUtils.getSecondUserEmail());

    samService.dumpRoleBindings(
        SamResource.WORKSPACE,
        workspaceUuid.toString(),
        userAccessUtils.defaultUserAuthRequest().getRequiredToken());

    // Create a GCP context as default user
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUser().getAuthenticatedRequest();
    String makeContextJobId = UUID.randomUUID().toString();
    SpendProfile spendProfile =
        spendProfileService.authorizeLinking(
            DEFAULT_SPEND_PROFILE_ID, features.isBpmGcpEnabled(), userRequest);

    workspaceService.createCloudContext(
        workspace, CloudPlatform.GCP, spendProfile, makeContextJobId, userRequest, null);
    jobService.waitForJob(makeContextJobId);
    JobApiUtils.AsyncJobResult<CloudContext> createContextJobResult =
        jobApiUtils.retrieveAsyncJobResult(makeContextJobId, CloudContext.class);
    assertEquals(StatusEnum.SUCCEEDED, createContextJobResult.getJobReport().getStatus());
    GcpCloudContext cloudContext = createContextJobResult.getResult().castByEnum(CloudPlatform.GCP);

    // Create a private dataset for secondary user
    String datasetId = RandomStringUtils.randomAlphabetic(8);
    ControlledBigQueryDatasetResource privateDataset =
        buildPrivateDataset(workspaceUuid, datasetId, cloudContext.getGcpProjectId());
    assertNotNull(privateDataset);

    // Allow the secondary user to impersonate their pet SA.
    petSaService.enablePetServiceAccountImpersonation(
        workspaceUuid,
        userAccessUtils.getSecondUserEmail(),
        userAccessUtils.secondUserAuthRequest());
    String secondaryUserPetServiceEmail =
        petSaService
            .getUserPetSa(
                cloudContext.getGcpProjectId(),
                userAccessUtils.getSecondUserEmail(),
                userAccessUtils.secondUserAuthRequest())
            .get()
            .email();
    // Validate the secondary user can impersonate their pet SA directly.
    Iam secondaryUserIamClient = getIamClientForUser(userAccessUtils.secondUserAccessToken());
    assertTrue(
        canImpersonateSa(
            secondaryUserIamClient, cloudContext.getGcpProjectId(), secondaryUserPetServiceEmail));

    // Validate with Sam that secondary user can read their private resource
    assertTrue(
        samService.isAuthorized(
            userAccessUtils.secondUserAuthRequest(),
            privateDataset.getCategory().getSamResourceName(),
            privateDataset.getResourceId().toString(),
            SamControlledResourceActions.WRITE_ACTION));

    // Run the "removeUser" flight to the very end, then undo it, retrying steps along the way.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(ValidateUserRoleStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(RemoveUserFromSamStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        CheckWorkspaceUserActionsStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        ClaimUserPrivateResourcesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        RemovePrivateResourceAccessStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        MarkPrivateResourcesAbandonedStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        RevokePetUsagePermissionStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        ReleasePrivateResourceCleanupClaimsStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo failingDebugInfo =
        FlightDebugInfo.newBuilder().undoStepFailures(retrySteps).lastStepFailure(true).build();

    FlightMap inputParameters = new FlightMap();
    inputParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    inputParameters.put(
        WorkspaceFlightMapKeys.USER_TO_REMOVE, userAccessUtils.getSecondUserEmail());
    inputParameters.put(WorkspaceFlightMapKeys.ROLE_TO_REMOVE, WsmIamRole.WRITER.name());
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

    // Validate that secondary user is still a workspace writer, can still read their private
    // resource, and can still impersonate their pet SA.
    assertTrue(
        samService.isAuthorized(
            userAccessUtils.secondUserAuthRequest(),
            SamResource.WORKSPACE,
            workspaceUuid.toString(),
            SamWorkspaceAction.WRITE));
    assertTrue(
        samService.isAuthorized(
            userAccessUtils.secondUserAuthRequest(),
            privateDataset.getCategory().getSamResourceName(),
            privateDataset.getResourceId().toString(),
            SamControlledResourceActions.WRITE_ACTION));
    assertTrue(
        canImpersonateSa(
            secondaryUserIamClient, cloudContext.getGcpProjectId(), secondaryUserPetServiceEmail));

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

    // Verify the secondary user can no longer access the workspace, their private resource,
    // or impersonate their pet SA.
    assertFalse(
        samService.isAuthorized(
            userAccessUtils.secondUserAuthRequest(),
            SamResource.WORKSPACE,
            workspaceUuid.toString(),
            SamWorkspaceAction.WRITE));
    assertFalse(
        samService.isAuthorized(
            userAccessUtils.secondUserAuthRequest(),
            privateDataset.getCategory().getSamResourceName(),
            privateDataset.getResourceId().toString(),
            SamControlledResourceActions.WRITE_ACTION));
    // Permissions can take some time to propagate, retry until the user can no longer impersonate
    // their pet SA.
    assertTrue(
        GcpCloudUtils.getWithRetryOnException(
            () ->
                assertCannotImpersonateSa(
                    secondaryUserIamClient,
                    cloudContext.getGcpProjectId(),
                    secondaryUserPetServiceEmail)));

    // Cleanup
    workspaceService.deleteWorkspace(workspace, userAccessUtils.defaultUserAuthRequest());
  }

  private ControlledBigQueryDatasetResource buildPrivateDataset(
      UUID workspaceUuid, String datasetName, String projectId) {
    ControlledResourceFields commonFields =
        ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
            .workspaceUuid(workspaceUuid)
            .resourceId(UUID.randomUUID())
            .name(datasetName)
            .cloningInstructions(CloningInstructions.COPY_NOTHING)
            .assignedUser(userAccessUtils.getSecondUserEmail())
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .build();
    ControlledBigQueryDatasetResource datasetToCreate =
        ControlledBigQueryDatasetResource.builder()
            .common(commonFields)
            .datasetName(datasetName)
            .projectId(projectId)
            .build();
    ApiGcpBigQueryDatasetCreationParameters datasetCreationParameters =
        new ApiGcpBigQueryDatasetCreationParameters()
            .datasetId(datasetName)
            .location("us-central1");

    return controlledResourceService
        .createControlledResourceSync(
            datasetToCreate,
            ControlledResourceIamRole.EDITOR,
            userAccessUtils.secondUserAuthRequest(),
            datasetCreationParameters)
        .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
  }

  private Iam getIamClientForUser(AccessToken accessToken)
      throws GeneralSecurityException, IOException {
    return new Iam(
        GoogleNetHttpTransport.newTrustedTransport(),
        GsonFactory.getDefaultInstance(),
        new HttpCredentialsAdapter(new GoogleCredentials(accessToken)));
  }

  // Wrapper around canImpersonateSa for retries
  private boolean assertCannotImpersonateSa(Iam iamClient, String projectId, String petSaEmail)
      throws Exception {
    if (canImpersonateSa(iamClient, projectId, petSaEmail)) {
      throw new RuntimeException("Can still impersonate the pet SA");
    }
    return true;
  }

  // Call GCP IAM service directly to determine whether a user can impersonate the provided pet SA.
  private boolean canImpersonateSa(Iam iamClient, String projectId, String petSaEmail)
      throws Exception {
    String fullyQualifiedSaName =
        String.format("projects/%s/serviceAccounts/%s", projectId, petSaEmail);
    TestIamPermissionsRequest testIamRequest =
        new TestIamPermissionsRequest()
            .setPermissions(Collections.singletonList("iam.serviceAccounts.actAs"));
    TestIamPermissionsResponse response =
        iamClient
            .projects()
            .serviceAccounts()
            .testIamPermissions(fullyQualifiedSaName, testIamRequest)
            .execute();
    // When no permissions are active, the permissions field of the response is null instead of an
    // empty list. This is a quirk of the GCP client library.
    return response.getPermissions() != null
        && response.getPermissions().contains("iam.serviceAccounts.actAs");
  }
}
