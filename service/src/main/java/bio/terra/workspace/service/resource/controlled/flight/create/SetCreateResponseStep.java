package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import bio.terra.workspace.service.workspace.model.OperationType;

public class SetCreateResponseStep implements Step {
  private final ControlledResource resource;
  private final ResourceDao resourceDao;
  private final WsmResourceStateRule resourceStateRule;

  private final AuthenticatedUserRequest userRequest;
  private final WorkspaceActivityLogService workspaceActivityLogService;
  /**
   * This step retrieves the created resource from the database and returns it as the response.
   *
   * @param resource resource being created
   * @param resourceDao DAO for lookup
   */
  public SetCreateResponseStep(
      ControlledResource resource,
      ResourceDao resourceDao,
      WsmResourceStateRule resourceStateRule,
      AuthenticatedUserRequest userRequest,
      WorkspaceActivityLogService workspaceActivityLogService) {
    this.resource = resource;
    this.resourceDao = resourceDao;
    this.resourceStateRule = resourceStateRule;
    this.userRequest = userRequest;
    this.workspaceActivityLogService = workspaceActivityLogService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    WsmResource responseResource =
        resourceDao.createResourceSuccess(resource, flightContext.getFlightId());
    flightContext.getWorkingMap().put(JobMapKeys.RESPONSE.getKeyName(), responseResource);
    workspaceActivityLogService.writeActivity(
        userRequest,
        resource.getWorkspaceId(),
        OperationType.CREATE,
        resource.getResourceId().toString(),
        resource.getResourceType().getActivityLogChangedTarget());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
