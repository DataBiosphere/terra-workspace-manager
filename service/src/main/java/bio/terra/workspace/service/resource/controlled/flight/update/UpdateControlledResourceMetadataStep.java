package bio.terra.workspace.service.resource.controlled.flight.update;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;

public class UpdateControlledResourceMetadataStep implements Step {

  private final ResourceDao resourceDao;
  private final ControlledResourceMetadataManager controlledResourceMetadataManager;
  private ControlledResource resource;

  public UpdateControlledResourceMetadataStep(
      ControlledResourceMetadataManager controlledResourceMetadataManager,
      ResourceDao resourceDao,
      ControlledResource resource) {
    this.resourceDao = resourceDao;
    this.controlledResourceMetadataManager = controlledResourceMetadataManager;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap inputParameters = flightContext.getInputParameters();
    final String resourceName = inputParameters.get(ResourceKeys.RESOURCE_NAME, String.class);
    final String resourceDescription =
        inputParameters.get(ResourceKeys.RESOURCE_DESCRIPTION, String.class);
    final AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    controlledResourceMetadataManager.updateControlledResourceMetadata(
        resource.getWorkspaceId(),
        resource.getResourceId(),
        resourceName,
        resourceDescription,
        resource.getCloningInstructions(),
        userRequest);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final String previousName = workingMap.get(ResourceKeys.PREVIOUS_RESOURCE_NAME, String.class);
    final String previousDescription =
        workingMap.get(ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION, String.class);
    resourceDao.updateResource(
        resource.getWorkspaceId(), resource.getResourceId(), previousName, previousDescription);
    return StepResult.getStepResultSuccess();
  }
}
