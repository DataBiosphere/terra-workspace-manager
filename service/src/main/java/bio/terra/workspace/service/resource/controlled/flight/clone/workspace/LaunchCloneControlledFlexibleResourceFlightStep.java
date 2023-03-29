package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.DuplicateFlightIdException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.stairway.exception.StairwayExecutionException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.flexibleresource.CloneControlledFlexibleResourceFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;
import javax.annotation.Nullable;

public class LaunchCloneControlledFlexibleResourceFlightStep implements Step {
  private final ControlledFlexibleResource resource;
  private final String subFlightId;
  private final UUID destinationResourceId;
  private final UUID destinationFolderId;

  public LaunchCloneControlledFlexibleResourceFlightStep(
      ControlledFlexibleResource resource,
      String subFlightId,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId) {
    this.resource = resource;
    this.subFlightId = subFlightId;
    this.destinationResourceId = destinationResourceId;
    this.destinationFolderId = destinationFolderId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntries(
        context.getInputParameters(),
        JobMapKeys.AUTH_USER_INFO.getKeyName(),
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID);

    AuthenticatedUserRequest userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    UUID destinationWorkspaceId =
        context
            .getInputParameters()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

    // Gather input parameters for the flight.
    // Omit name, description, and cloning instructions to use the source values.
    FlightMap subFlightInputParameters = new FlightMap();
    subFlightInputParameters.put(
        JobMapKeys.DESCRIPTION.getKeyName(),
        String.format(
            "Clone controlled flex resource id %s; name %s",
            resource.getResourceId(), resource.getName()));

    subFlightInputParameters.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        destinationWorkspaceId);
    subFlightInputParameters.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID,
        destinationResourceId);
    subFlightInputParameters.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_FOLDER_ID, destinationFolderId);
    subFlightInputParameters.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, resource);
    // Do not do the policy merge on the sub-object clone
    subFlightInputParameters.put(WorkspaceFlightMapKeys.MERGE_POLICIES, false);
    subFlightInputParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    // Launch the flight.
    try {
      context
          .getStairway()
          .submit(
              subFlightId, CloneControlledFlexibleResourceFlight.class, subFlightInputParameters);
    } catch (DuplicateFlightIdException unused) {
      return StepResult.getStepResultSuccess();
    } catch (DatabaseOperationException | StairwayExecutionException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo; can't undo a launch step.
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
