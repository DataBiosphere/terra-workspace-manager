package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step to delete all controlled resources in a workspace from Sam, potentially limited to a
 * single cloud platform. This reads the list of controlled resources in a workspace from the WSM
 * database.
 */
public class DeleteControlledSamResourcesStep implements Step {

  private final Logger logger = LoggerFactory.getLogger(DeleteControlledSamResourcesStep.class);
  private final SamService samService;
  private final ResourceDao resourceDao;
  private final UUID workspaceId;
  private final CloudPlatform cloudPlatform;
  private final AuthenticatedUserRequest userRequest;

  public DeleteControlledSamResourcesStep(
      SamService samService,
      ResourceDao resourceDao,
      UUID workspaceId,
      CloudPlatform cloudPlatform,
      AuthenticatedUserRequest userRequest) {
    this.samService = samService;
    this.resourceDao = resourceDao;
    this.workspaceId = workspaceId;
    this.cloudPlatform = cloudPlatform;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {

    List<ControlledResource> controlledResourceList =
        resourceDao.listControlledResources(workspaceId, cloudPlatform);

    for (ControlledResource resource : controlledResourceList) {
      samService.deleteControlledResource(resource, userRequest);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Resource deletion can't be undone, so this just surfaces the error from the DO direction
    // instead.
    logger.error(
        "Unable to undo deletion of controlled resources in Sam for workspace {}", workspaceId);
    return flightContext.getResult();
  }
}
