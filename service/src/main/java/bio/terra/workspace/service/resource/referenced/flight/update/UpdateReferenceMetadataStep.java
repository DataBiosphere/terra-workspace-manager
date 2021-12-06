package bio.terra.workspace.service.resource.referenced.flight.update;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.referenced.ReferencedResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class UpdateReferenceMetadataStep implements Step {
  private final ResourceDao resourceDao;
  private final ReferencedResource referencedResource;
  private final UUID workspaceId;
  private final UUID resourceId;

  public UpdateReferenceMetadataStep(ResourceDao resourceDao, ReferencedResource referencedResource) {
    this.resourceDao = resourceDao;
    this.referencedResource = referencedResource;
    workspaceId = referencedResource.getWorkspaceId();
    resourceId = referencedResource.getResourceId();
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException, RetryException {
    final FlightMap inputParameters = flightContext.getInputParameters();
    final String resourceName =
        inputParameters.get(ResourceKeys.RESOURCE_NAME, String.class);
    final String resourceDescription =
        inputParameters.get(ResourceKeys.RESOURCE_DESCRIPTION, String.class);

    boolean updated = resourceDao.updateResource(workspaceId, resourceId,
        ResourceDao.getUpdateParams(resourceName, resourceDescription, referencedResource.attributesToJson()));
    FlightUtils.setResponse(flightContext, updated, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final String previousName =
        workingMap.get(ResourceKeys.PREVIOUS_RESOURCE_NAME, String.class);
    final String previousDescription =
        workingMap.get(ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION, String.class);
    final String previousAttributes =
        workingMap.get(ResourceKeys.PREVIOUS_ATTRIBUTES, String.class);

    resourceDao.updateResource(workspaceId, resourceId,
        ResourceDao.getUpdateParams(previousName, previousDescription, previousAttributes));
    return StepResult.getStepResultSuccess();
  }
}
