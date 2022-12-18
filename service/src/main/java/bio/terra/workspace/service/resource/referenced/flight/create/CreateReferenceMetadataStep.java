package bio.terra.workspace.service.resource.referenced.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/** Stairway step to persist a data reference in WSM's database. */
public class CreateReferenceMetadataStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateReferenceMetadataStep.class);

  private final AuthenticatedUserRequest userRequest;
  private final ReferencedResourceService referencedResourceService;

  public CreateReferenceMetadataStep(
      AuthenticatedUserRequest userRequest, ReferencedResourceService referencedResourceService) {
    this.userRequest = userRequest;
    this.referencedResourceService = referencedResourceService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws RetryException, InterruptedException {
    WsmResource referencedResource = getReferencedResource(flightContext);
    if (referencedResource == null) {
      logger.warn("Fails to get referenced resource to create");
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
    }
    referencedResourceService.createReferenceResource(
        referencedResource.castToReferencedResource(), userRequest);
    FlightUtils.setResponse(flightContext, referencedResource.getResourceId(), HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    WsmResource referencedResource = getReferencedResource(flightContext);

    referencedResourceService.deleteReferenceResourceForResourceType(
        referencedResource.getWorkspaceId(),
        referencedResource.getResourceId(),
        referencedResource.getResourceType(),
        userRequest);

    return StepResult.getStepResultSuccess();
  }

  private WsmResource getReferencedResource(FlightContext flightContext) {
    FlightMap workingMap = flightContext.getWorkingMap();
    FlightMap inputParameters = flightContext.getInputParameters();

    // First check working map RESOURCE_TYPE. For cloning a controlled resource to referenced
    // resource, this step is used to create destination resource. Working map RESOURCE_TYPE has
    // referenced type, whereas input parameter RESOURCE_TYPE has controlled type.
    WsmResourceType resourceType =
        workingMap.containsKey(ResourceKeys.RESOURCE_TYPE)
            ? WsmResourceType.valueOf(workingMap.get(ResourceKeys.RESOURCE_TYPE, String.class))
            : WsmResourceType.valueOf(
                inputParameters.get(ResourceKeys.RESOURCE_TYPE, String.class));

    // First check working map DESTINATION_REFERENCED_RESOURCE. For cloning a controlled resource to
    // referenced resource, this step is used to create destination resource. Working map
    // DESTINATION_REFERENCED_RESOURCE has destination resource, whereas input parameter RESOURCE
    // has source resource.
    return workingMap.containsKey(ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE)
        ? workingMap.get(
            ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE, resourceType.getResourceClass())
        : inputParameters.get(ResourceKeys.RESOURCE, resourceType.getResourceClass());
  }
}
