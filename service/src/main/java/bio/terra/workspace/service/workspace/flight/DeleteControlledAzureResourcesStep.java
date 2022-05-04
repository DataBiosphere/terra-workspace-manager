package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * A step to delete all controlled Azure resources resources in a workspace. This reads the list of
 * controlled Azure resources in a workspace from the WSM database.
 */
public class DeleteControlledAzureResourcesStep implements Step {

  private final Logger logger = LoggerFactory.getLogger(DeleteControlledAzureResourcesStep.class);
  private final ResourceDao resourceDao;
  private final ControlledResourceService controlledResourceService;
  private final UUID workspaceUuid;
  private final AuthenticatedUserRequest userRequest;

  public DeleteControlledAzureResourcesStep(
      ResourceDao resourceDao,
      ControlledResourceService controlledResourceService,
      UUID workspaceUuid,
      AuthenticatedUserRequest userRequest) {
    this.resourceDao = resourceDao;
    this.controlledResourceService = controlledResourceService;
    this.workspaceUuid = workspaceUuid;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {

    List<ControlledResource> controlledResourceList =
        resourceDao.listControlledResources(workspaceUuid, CloudPlatform.AZURE);

    /**
     * TODO: does this need to be done in any particular order? Most resources are likely fine, but
     * Storage accounts and containers will both be modeled as controlled resources yet I imagine
     * containers will need to be deleted first or not at all
     */
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
