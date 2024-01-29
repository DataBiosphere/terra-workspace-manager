package bio.terra.workspace.service.workspace.flight.cloud.gcp;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GCP_PROJECT_ID;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.CloudContextCommonFields;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.GcpCloudContextFields;
import java.util.HashMap;
import java.util.UUID;

/**
 * A {@code Step} which synchronizes Sam policies with google groups and stores the group names in
 * the Stairway working map.
 */
public class SyncSamGroupsStep implements Step {

  private final SamService samService;
  private final UUID workspaceUuid;
  private final SpendProfile spendProfile;
  private final AuthenticatedUserRequest userRequest;

  public SyncSamGroupsStep(
      SamService samService,
      UUID workspaceUuid,
      SpendProfile spendProfile,
      AuthenticatedUserRequest userRequest) {
    this.samService = samService;
    this.workspaceUuid = workspaceUuid;
    this.spendProfile = spendProfile;
    this.userRequest = userRequest;
  }

  // Note that the SamService.syncWorkspacePolicy is already idempotent, so this doesn't need to
  // be explicitly handled here.
  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    // This cannot be an ImmutableMap, as those do not deserialize properly with Jackson.
    var workspaceRoleGroupMap = new HashMap<WsmIamRole, String>();
    workspaceRoleGroupMap.put(
        WsmIamRole.OWNER,
        samService.syncWorkspacePolicy(workspaceUuid, WsmIamRole.OWNER, userRequest));
    workspaceRoleGroupMap.put(
        WsmIamRole.APPLICATION,
        samService.syncWorkspacePolicy(workspaceUuid, WsmIamRole.APPLICATION, userRequest));
    workspaceRoleGroupMap.put(
        WsmIamRole.WRITER,
        samService.syncWorkspacePolicy(workspaceUuid, WsmIamRole.WRITER, userRequest));
    workspaceRoleGroupMap.put(
        WsmIamRole.READER,
        samService.syncWorkspacePolicy(workspaceUuid, WsmIamRole.READER, userRequest));

    // Do not sync discoverer policy. Discoverer roles are not attached to GCP resources; they are
    // only used by WSM internally.

    FlightMap workingMap = flightContext.getWorkingMap();
    workingMap.put(WorkspaceFlightMapKeys.IAM_GROUP_EMAIL_MAP, workspaceRoleGroupMap);

    // Build the cloud context and store it in the working map. It is used to update
    // the DB in the common end step of the flight.
    String projectId = flightContext.getWorkingMap().get(GCP_PROJECT_ID, String.class);
    GcpCloudContext context =
        new GcpCloudContext(
            new GcpCloudContextFields(
                projectId,
                workspaceRoleGroupMap.get(WsmIamRole.OWNER),
                workspaceRoleGroupMap.get(WsmIamRole.WRITER),
                workspaceRoleGroupMap.get(WsmIamRole.READER),
                workspaceRoleGroupMap.get(WsmIamRole.APPLICATION)),
            new CloudContextCommonFields(
                spendProfile.id(),
                WsmResourceState.CREATING,
                flightContext.getFlightId(),
                /* error= */ null));

    workingMap.put(WorkspaceFlightMapKeys.CLOUD_CONTEXT, context);

    return StepResult.getStepResultSuccess();
  }

  // Sam policies are never "de-synced" from google groups, so there's nothing to undo here.
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
