package bio.terra.workspace.service.resource.referenced.flight.clone;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.workspace.model.OperationType;
import org.springframework.http.HttpStatus;

public class CreateResourceInDbFinishStep implements Step {
  private final ResourceDao resourceDao;
  private final ReferencedResource destinationResource;

  private final ReferencedResource sourceResource;

  private final AuthenticatedUserRequest userRequest;

  private final WorkspaceActivityLogService workspaceActivityLogService;

  public CreateResourceInDbFinishStep(
      ResourceDao resourceDao,
      ReferencedResource destinationResource,
      ReferencedResource sourceResource,
      AuthenticatedUserRequest userRequest,
      WorkspaceActivityLogService workspaceActivityLogService) {
    this.resourceDao = resourceDao;
    this.destinationResource = destinationResource;
    this.sourceResource = sourceResource;
    this.userRequest = userRequest;
    this.workspaceActivityLogService = workspaceActivityLogService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    var createdResource =
        resourceDao.createResourceSuccess(destinationResource, context.getFlightId());
    FlightUtils.setResponse(context, createdResource, HttpStatus.OK);
    workspaceActivityLogService.writeActivity(
        userRequest,
        // the id of the workspace the resource is cloned to.
        destinationResource.getWorkspaceId(),
        OperationType.CLONE,
        // the id of the resource that is being cloned. we can track the lineage this way.
        sourceResource.getResourceId().toString(),
        sourceResource.getResourceType().getActivityLogChangedTarget());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
