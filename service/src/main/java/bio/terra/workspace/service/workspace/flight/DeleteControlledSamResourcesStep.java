package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step to delete all controlled resources in a workspace from Sam, potentially limited to a
 * single cloud platform. This reads the list of controlled resources in a workspace from the WSM
 * database. We use the WSM SA to delete the controlled resources, since it is the only actor that
 * has DELETE permission on all controlled resources. Note that this is the one case where we delete
 * resources out from under applications. In some distant future, we may have a method of requesting
 * the application to clean up.
 */
public class DeleteControlledSamResourcesStep implements Step {

  private final Logger logger = LoggerFactory.getLogger(DeleteControlledSamResourcesStep.class);
  private final SamService samService;
  private final ResourceDao resourceDao;
  private final UUID workspaceUuid;
  private final CloudPlatform cloudPlatform;

  public DeleteControlledSamResourcesStep(
      SamService samService,
      ResourceDao resourceDao,
      UUID workspaceUuid,
      CloudPlatform cloudPlatform) {
    this.samService = samService;
    this.resourceDao = resourceDao;
    this.workspaceUuid = workspaceUuid;
    this.cloudPlatform = cloudPlatform;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {

    List<ControlledResource> controlledResourceList =
        resourceDao.listControlledResources(workspaceUuid, cloudPlatform);

    for (ControlledResource resource : controlledResourceList) {
      samService.deleteControlledResource(resource, samService.getWsmServiceAccountToken());
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Resource deletion can't be undone, so this just surfaces the error from the DO direction
    // instead.
    logger.error(
        "Unable to undo deletion of controlled resources in Sam for workspace {}", workspaceUuid);
    return flightContext.getResult();
  }
}
