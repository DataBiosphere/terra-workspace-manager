package bio.terra.workspace.service.resource.reference.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.DuplicateDataReferenceException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.reference.ReferenceResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/** Stairway step to persist a data reference in WM's database. */
public class CreateReferenceMetadataStep implements Step {

  private final ResourceDao resourceDao;

  public CreateReferenceMetadataStep(ResourceDao resourceDao) {
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();

    WsmResourceType resourceType =
            inputMap.get(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_TYPE, WsmResourceType.class);

    // Use the resource type to deserialize the right class
    ReferenceResource referenceResource =
            inputMap.get(JobMapKeys.REQUEST.getKeyName(), resourceType.getReferenceClass());

    try {
      resourceDao.createReferenceResource(referenceResource);
    } catch (DuplicateDataReferenceException e) {
      // Stairway can call the same step multiple times as part of a flight, so finding a duplicate
      // reference here is fine.
    }

    FlightUtils.setResponse(flightContext, referenceResource.getResourceId(), HttpStatus.OK);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();
    FlightMap workingMap = flightContext.getWorkingMap();

    UUID resourceId = workingMap.get(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_ID, UUID.class);
    UUID workspaceId = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);

    // Ignore return value, as we don't care whether a reference was deleted or just not found.
    resourceDao.deleteResource(workspaceId, resourceId);

    return StepResult.getStepResultSuccess();
  }



}
