package bio.terra.workspace.service.workspace.flight.cloud.aws;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step to delete all controlled AWS resources in a workspace. This reads the list of controlled
 * AWS resources in a workspace from the WSM database.
 */
public class DeleteControlledAwsResourcesStep implements Step {

  private final Logger logger = LoggerFactory.getLogger(DeleteControlledAwsResourcesStep.class);
  private final ResourceDao resourceDao;
  private final ControlledResourceService controlledResourceService;
  private final SamService samService;
  private final UUID workspaceUuid;
  private final AuthenticatedUserRequest userRequest;

  public DeleteControlledAwsResourcesStep(
      ResourceDao resourceDao,
      ControlledResourceService controlledResourceService,
      SamService samService,
      UUID workspaceUuid,
      AuthenticatedUserRequest userRequest) {
    this.resourceDao = resourceDao;
    this.controlledResourceService = controlledResourceService;
    this.samService = samService;
    this.workspaceUuid = workspaceUuid;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    List<ControlledResource> controlledResourceList =
        resourceDao.listControlledResources(workspaceUuid, CloudPlatform.AWS);
    for (ControlledResource resource : controlledResourceList) {
      if (!samService.isAuthorized(
          userRequest,
          resource.getCategory().getSamResourceName(),
          resource.getResourceId().toString(),
          SamConstants.SamControlledResourceActions.DELETE_ACTION)) {
        return new StepResult(
            StepStatus.STEP_RESULT_FAILURE_FATAL,
            new ForbiddenException(
                String.format(
                    "User %s is not authorized to perform action %s on %s %s",
                    userRequest.getEmail(),
                    SamConstants.SamControlledResourceActions.DELETE_ACTION,
                    resource.getCategory().getSamResourceName(),
                    resource.getResourceId().toString())));
      }
    }

    // Delete all resources
    for (ControlledResource resource : controlledResourceList) {
      controlledResourceService.deleteControlledResourceSync(
          workspaceUuid, resource.getResourceId(), /* forceDelete= */ true, userRequest);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return new StepResult(
        StepStatus.STEP_RESULT_FAILURE_FATAL,
        new InternalLogicException(
            "Unable to undo deletion of controlled AWS resources for workspace " + workspaceUuid));
  }
}
