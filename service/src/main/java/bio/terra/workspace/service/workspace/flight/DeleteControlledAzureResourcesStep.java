package bio.terra.workspace.service.workspace.flight;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step to delete all controlled Azure resources in a workspace. This reads the list of controlled
 * Azure resources in a workspace from the WSM database.
 */
public class DeleteControlledAzureResourcesStep implements Step {

  private final Logger logger = LoggerFactory.getLogger(DeleteControlledAzureResourcesStep.class);
  private final ResourceDao resourceDao;
  private final ControlledResourceService controlledResourceService;
  private final SamService samService;
  private final UUID workspaceUuid;
  private final AuthenticatedUserRequest userRequest;

  public DeleteControlledAzureResourcesStep(
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

  /** Delete all resources instances of the specified type, returning all remaining resources. */
  private List<ControlledResource> deleteResourcesOfType(
      List<ControlledResource> allResources, WsmResourceType type) {
    Map<Boolean, List<ControlledResource>> partitionedResources =
        allResources.stream()
            .collect(Collectors.partitioningBy(cr -> cr.getResourceType() == type));

    for (ControlledResource vm : partitionedResources.get(true)) {
      controlledResourceService.deleteControlledResourceSync(
          workspaceUuid, vm.getResourceId(), userRequest);
    }

    return partitionedResources.get(false);
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {

    List<ControlledResource> controlledResourceList =
        resourceDao.listControlledResources(workspaceUuid, CloudPlatform.AZURE);
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

    // Delete VMs first because they use other resources like disks, networks, etc.
    controlledResourceList =
        deleteResourcesOfType(controlledResourceList, WsmResourceType.CONTROLLED_AZURE_VM);

    // Delete storage containers so that Sam resources are properly deleted (before storage accounts
    // are deleted).
    controlledResourceList =
        deleteResourcesOfType(
            controlledResourceList, WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER);

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
