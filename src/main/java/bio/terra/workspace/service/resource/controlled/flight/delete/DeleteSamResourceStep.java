package bio.terra.workspace.service.resource.controlled.flight.delete;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for deleting the Sam resource underlying a controlled resource. Once this step completes,
 */
public class DeleteSamResourceStep implements Step {

  private final ResourceDao resourceDao;
  private final SamService samService;
  private final UUID workspaceId;
  private final UUID resourceId;
  private final AuthenticatedUserRequest userRequest;

  private final Logger logger = LoggerFactory.getLogger(DeleteSamResourceStep.class);

  public DeleteSamResourceStep(
      ResourceDao resourceDao,
      SamService samService,
      UUID workspaceId,
      UUID resourceId,
      AuthenticatedUserRequest userRequest) {
    this.resourceDao = resourceDao;
    this.samService = samService;
    this.workspaceId = workspaceId;
    this.resourceId = resourceId;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    WsmResource wsmResource = resourceDao.getResource(workspaceId, resourceId);
    ControlledResource resource = wsmResource.castToControlledResource();
    // deleteControlledResource already handles duplicate deletion, so we do not need to explicitly
    // handle it inside this step.
    samService.deleteControlledResource(resource, userRequest);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // No undo for delete. There is no way to put it back.
    logger.error("Cannot undo delete of Sam resource {} in workspace {}.", resourceId, workspaceId);
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
