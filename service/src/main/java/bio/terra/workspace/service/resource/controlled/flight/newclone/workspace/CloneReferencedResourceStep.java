package bio.terra.workspace.service.resource.controlled.flight.newclone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;

/**
 * All we do for cloning a referenced resource is write the metadata.
 * This does not require a flight.
 */
public class CloneReferencedResourceStep implements Step {
  private final ReferencedResource resource;
  private final ResourceDao resourceDao;

  public CloneReferencedResourceStep(ResourceDao resourceDao, ReferencedResource resource) {
    this.resource = resource;
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    resourceDao.createReferencedResource(resource);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    resourceDao.deleteResource(resource.getWorkspaceId(), resource.getResourceId());
    return StepResult.getStepResultSuccess();
  }
}
