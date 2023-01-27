package bio.terra.workspace.service.resource.controlled.flight.delete;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for deleting the Sam resource underlying a controlled resource. Once this step completes,
 */
public class DeleteSamResourceStep implements Step {

  private final ResourceDao resourceDao;
  private final SamService samService;
  private final UUID workspaceUuid;
  private final UUID resourceId;

  private final Logger logger = LoggerFactory.getLogger(DeleteSamResourceStep.class);

  public DeleteSamResourceStep(
      ResourceDao resourceDao, SamService samService, UUID workspaceUuid, UUID resourceId) {
    this.resourceDao = resourceDao;
    this.samService = samService;
    this.workspaceUuid = workspaceUuid;
    this.resourceId = resourceId;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    WsmResource wsmResource = resourceDao.getResource(workspaceUuid, resourceId);
    ControlledResource resource = wsmResource.castToControlledResource();
    // deleteControlledResource already handles duplicate deletion, so we do not need to explicitly
    // handle it inside this step.
    final AuthenticatedUserRequest userRequest =
        FlightUtils.getRequired(
            flightContext.getInputParameters(),
            JobMapKeys.AUTH_USER_INFO.getKeyName(),
            AuthenticatedUserRequest.class);
    samService.deleteControlledResource(resource);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // No undo for delete. There is no way to put it back.
    logger.error(
        "Cannot undo delete of Sam resource {} in workspace {}.", resourceId, workspaceUuid);
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
