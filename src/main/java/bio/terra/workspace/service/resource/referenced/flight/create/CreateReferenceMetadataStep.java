package bio.terra.workspace.service.resource.referenced.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.ReferencedResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import org.springframework.http.HttpStatus;

/** Stairway step to persist a data reference in WSM's database. */
public class CreateReferenceMetadataStep implements Step {

  private final ResourceDao resourceDao;

  public CreateReferenceMetadataStep(ResourceDao resourceDao) {
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    ReferencedResource referenceResource = getReferenceResource(flightContext);
    resourceDao.createReferenceResource(referenceResource);
    FlightUtils.setResponse(flightContext, referenceResource.getResourceId(), HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    ReferencedResource referenceResource = getReferenceResource(flightContext);

    // Ignore return value, as we don't care whether a reference was deleted or just not found.
    resourceDao.deleteResource(
        referenceResource.getWorkspaceId(), referenceResource.getResourceId());

    return StepResult.getStepResultSuccess();
  }

  private ReferencedResource getReferenceResource(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();
    WsmResourceType resourceType =
        inputMap.get(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_TYPE, WsmResourceType.class);

    // Use the resource type to deserialize the right class
    return inputMap.get(JobMapKeys.REQUEST.getKeyName(), resourceType.getReferenceClass());
  }
}
