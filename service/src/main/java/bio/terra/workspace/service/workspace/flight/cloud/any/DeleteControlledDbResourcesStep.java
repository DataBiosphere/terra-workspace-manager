package bio.terra.workspace.service.workspace.flight.cloud.any;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A step to delete the controlled resources on a single cloud from a workspace. */
public class DeleteControlledDbResourcesStep implements Step {

  private final Logger logger = LoggerFactory.getLogger(DeleteControlledDbResourcesStep.class);

  private final ResourceDao resourceDao;
  private final UUID workspaceUuid;
  private final CloudPlatform cloudPlatform;

  public DeleteControlledDbResourcesStep(
      ResourceDao resourceDao, UUID workspaceUuid, CloudPlatform cloudPlatform) {
    this.resourceDao = resourceDao;
    this.workspaceUuid = workspaceUuid;
    this.cloudPlatform = cloudPlatform;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    resourceDao.deleteAllControlledResources(workspaceUuid, cloudPlatform);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Unable to undo deletion of controlled resources in workspace {}, cloud {}",
        workspaceUuid,
        cloudPlatform);
    return flightContext.getResult();
  }
}
