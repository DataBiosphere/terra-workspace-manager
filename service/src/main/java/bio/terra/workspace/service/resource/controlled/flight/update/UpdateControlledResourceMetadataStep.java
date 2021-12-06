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
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.UUID;

public class UpdateControlledResourceMetadataStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID resourceId;
  private final UUID workspaceId;
  private final ControlledResourceMetadataManager controlledResourceMetadataManager;

  public UpdateControlledResourceMetadataStep(
      ControlledResourceMetadataManager controlledResourceMetadataManager,
      ResourceDao resourceDao,
      UUID workspaceId,
      UUID resourceId) {
    this.resourceDao = resourceDao;
    this.resourceId = resourceId;
    this.workspaceId = workspaceId;
    this.controlledResourceMetadataManager = controlledResourceMetadataManager;
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
        workspaceId, resourceId, resourceName, resourceDescription, userRequest);
    return StepResult.getStepResultSuccess();
  }

  /**
   * Restore the previous values of the metadata.
   *
   * @param flightContext
   */
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final String previousName = workingMap.get(ResourceKeys.PREVIOUS_RESOURCE_NAME, String.class);
    final String previousDescription =
        workingMap.get(ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION, String.class);
    resourceDao.updateResource(workspaceId, resourceId, previousName, previousDescription);
    return StepResult.getStepResultSuccess();
  }
}
