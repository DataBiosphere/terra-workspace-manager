package bio.terra.workspace.service.workspace.flight.azure;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step to delete all controlled Azure resources in a workspace. This reads the list of controlled
 * Azure resources in a workspace from the WSM database.
 */
public class DeleteControlledAzureResourcesStep implements Step {

  private final Logger logger = LoggerFactory.getLogger(DeleteControlledAzureResourcesStep.class);
  private final ControlledResourceService controlledResourceService;
  private final UUID workspaceUuid;
  private final AuthenticatedUserRequest userRequest;

  public DeleteControlledAzureResourcesStep(
      ControlledResourceService controlledResourceService,
      UUID workspaceUuid,
      AuthenticatedUserRequest userRequest) {
    this.controlledResourceService = controlledResourceService;
    this.workspaceUuid = workspaceUuid;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    List<ControlledResource> controlledResourceList;
    try {
      controlledResourceList =
          controlledResourceService.getControlledResourceWithAuthCheck(
              workspaceUuid, CloudPlatform.AZURE, userRequest);
    } catch (ForbiddenException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }

    // Delete VMs first because they use other resources like disks, networks, etc.
    controlledResourceList =
        controlledResourceService.deleteControlledResourceSyncOfType(
            workspaceUuid,
            controlledResourceList,
            WsmResourceType.CONTROLLED_AZURE_VM,
            userRequest);

    // Delete storage containers so that Sam resources are properly deleted (before storage accounts
    // are deleted).
    controlledResourceList =
        controlledResourceService.deleteControlledResourceSyncOfType(
            workspaceUuid,
            controlledResourceList,
            WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER,
            userRequest);

    // Delete all remaining resources
    for (ControlledResource resource : controlledResourceList) {
      controlledResourceService.deleteControlledResourceSync(
          workspaceUuid, resource.getResourceId(), userRequest);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Resource deletion can't be undone, so this just surfaces the error from the DO direction
    // instead.
    logger.error(
        "Unable to undo deletion of controlled Azure resources for workspace {}", workspaceUuid);
    return flightContext.getResult();
  }
}
