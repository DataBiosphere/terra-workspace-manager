package bio.terra.workspace.service.resource.referenced.flight.clone;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import org.springframework.http.HttpStatus;

public class CloneReferenceResourceStep implements Step {

  private final AuthenticatedUserRequest userRequest;
  private final ResourceDao resourceDao;
  private final ReferencedResource sourceResource;
  private final ReferencedResource destinationResource;

  public CloneReferenceResourceStep(
      AuthenticatedUserRequest userRequest,
      ResourceDao resourceDao,
      ReferencedResource sourceResource,
      ReferencedResource destinationResource) {
    this.userRequest = userRequest;
    this.resourceDao = resourceDao;
    this.sourceResource = sourceResource;
    this.destinationResource = destinationResource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    var createdResource =
        resourceDao.createResourceSuccess(destinationResource, context.getFlightId());
    FlightUtils.setResponse(context, createdResource, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    resourceDao.deleteReferencedResourceForResourceType(
        destinationResource.getWorkspaceId(),
        destinationResource.getResourceId(),
        destinationResource.getResourceType());

    return StepResult.getStepResultSuccess();
  }
}
