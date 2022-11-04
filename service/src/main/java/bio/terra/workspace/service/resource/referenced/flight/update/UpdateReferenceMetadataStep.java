package bio.terra.workspace.service.resource.referenced.flight.update;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** A step to update the resource reference's name, description, and/or attributes. */
public class UpdateReferenceMetadataStep implements Step {
  private final ResourceDao resourceDao;
  private final ReferencedResource referencedResource;
  private final UUID workspaceUuid;
  private final UUID resourceId;

  public UpdateReferenceMetadataStep(
      ResourceDao resourceDao, ReferencedResource referencedResource) {
    this.resourceDao = resourceDao;
    this.referencedResource = referencedResource;
    workspaceUuid = referencedResource.getWorkspaceId();
    resourceId = referencedResource.getResourceId();
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap inputParameters = flightContext.getInputParameters();
    final String resourceName = inputParameters.get(ResourceKeys.RESOURCE_NAME, String.class);
    final String resourceDescription =
        inputParameters.get(ResourceKeys.RESOURCE_DESCRIPTION, String.class);

    boolean updated =
        resourceDao.updateResource(
            workspaceUuid,
            resourceId,
            resourceName,
            resourceDescription,
            referencedResource.attributesToJson(),
            referencedResource.getCloningInstructions());
    FlightUtils.setResponse(flightContext, updated, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final String previousName = workingMap.get(ResourceKeys.PREVIOUS_RESOURCE_NAME, String.class);
    final String previousDescription =
        workingMap.get(ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION, String.class);
    final String previousAttributes =
        workingMap.get(ResourceKeys.PREVIOUS_ATTRIBUTES, String.class);
    final var previousCloningInstructions =
        workingMap.get(ResourceKeys.PREVIOUS_CLONING_INSTRUCTIONS, CloningInstructions.class);
    resourceDao.updateResource(
        workspaceUuid,
        resourceId,
        previousName,
        previousDescription,
        previousAttributes,
        previousCloningInstructions);
    return StepResult.getStepResultSuccess();
  }
}
