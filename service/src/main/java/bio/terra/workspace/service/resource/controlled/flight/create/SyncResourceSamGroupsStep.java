package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.HashMap;

/**
 * Step for syncing Sam policy groups on a resource to Google groups and storing them in the flight
 * working map.
 */
public class SyncResourceSamGroupsStep implements Step {

  private final SamService samService;
  private final ControlledResource resource;
  private final AuthenticatedUserRequest userReq;

  public SyncResourceSamGroupsStep(
      SamService samService, ControlledResource resource, AuthenticatedUserRequest userReq) {
    this.samService = samService;
    this.resource = resource;
    this.userReq = userReq;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    // This cannot be an ImmutableMap, as those do not deserialize properly with Jackson.
    var resourceRoleGroupMap = new HashMap<ControlledResourceIamRole, String>();
    resourceRoleGroupMap.put(
        ControlledResourceIamRole.EDITOR,
        samService.syncPrivateResourcePolicy(resource, ControlledResourceIamRole.EDITOR, userReq));
    resourceRoleGroupMap.put(
        ControlledResourceIamRole.WRITER,
        samService.syncPrivateResourcePolicy(resource, ControlledResourceIamRole.WRITER, userReq));
    resourceRoleGroupMap.put(
        ControlledResourceIamRole.READER,
        samService.syncPrivateResourcePolicy(resource, ControlledResourceIamRole.READER, userReq));

    FlightMap workingMap = flightContext.getWorkingMap();
    workingMap.put(ControlledResourceKeys.IAM_RESOURCE_GROUP_EMAIL_MAP, resourceRoleGroupMap);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Groups cannot be de-synced in Sam so there is nothing to undo.
    return StepResult.getStepResultSuccess();
  }
}
