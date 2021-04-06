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
    FlightMap workingMap = flightContext.getWorkingMap();
    workingMap.put(
        ControlledResourceKeys.IAM_RESOURCE_EDITOR_GROUP_EMAIL,
        samService.syncResourcePolicy(resource, ControlledResourceIamRole.EDITOR, userReq));
    workingMap.put(
        ControlledResourceKeys.IAM_RESOURCE_WRITER_GROUP_EMAIL,
        samService.syncResourcePolicy(resource, ControlledResourceIamRole.WRITER, userReq));
    workingMap.put(
        ControlledResourceKeys.IAM_RESOURCE_READER_GROUP_EMAIL,
        samService.syncResourcePolicy(resource, ControlledResourceIamRole.READER, userReq));
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Groups cannot be de-synced in Sam so there is nothing to undo.
    return StepResult.getStepResultSuccess();
  }
}
