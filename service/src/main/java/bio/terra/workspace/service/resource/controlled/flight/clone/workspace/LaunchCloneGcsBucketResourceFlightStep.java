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
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.CloneControlledGcsBucketResourceFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.UUID;
import javax.annotation.Nullable;

public class LaunchCloneGcsBucketResourceFlightStep implements Step {

  private final ControlledGcsBucketResource resource;
  private final String subflightId;
  private final UUID destinationResourceId;
  private final UUID destinationFolderId;

  public LaunchCloneGcsBucketResourceFlightStep(
      ControlledGcsBucketResource resource,
      String subflightId,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId) {
    this.resource = resource;
    this.subflightId = subflightId;
    this.destinationResourceId = destinationResourceId;
    this.destinationFolderId = destinationFolderId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntries(
        context.getInputParameters(),
        JobMapKeys.AUTH_USER_INFO.getKeyName(),
        ControlledResourceKeys.DESTINATION_WORKSPACE_ID);

    var location = context.getInputParameters().get(ControlledResourceKeys.LOCATION, String.class);
    var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    var destinationWorkspaceId =
        context
            .getInputParameters()
            .get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

    // Gather input parameters for flight. See
    // bio.terra.workspace.service.resource.controlled.ControlledResourceService#cloneGcsBucket.
    FlightMap subflightInputParameters = new FlightMap();
    subflightInputParameters.put(ControlledResourceKeys.LOCATION, location);
    subflightInputParameters.put(
        ControlledResourceKeys.DESTINATION_WORKSPACE_ID, destinationWorkspaceId);
    subflightInputParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    subflightInputParameters.put(ResourceKeys.RESOURCE, resource);
    subflightInputParameters.put(
        ResourceKeys.CLONING_INSTRUCTIONS, resource.getCloningInstructions());
    subflightInputParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, destinationWorkspaceId);
    subflightInputParameters.put(
        JobMapKeys.DESCRIPTION.getKeyName(),
        String.format("Clone GCS Bucket resource %s", resource.getResourceId().toString()));
    subflightInputParameters.put(
        ControlledResourceKeys.DESTINATION_RESOURCE_ID, destinationResourceId);
    subflightInputParameters.put(ControlledResourceKeys.DESTINATION_FOLDER_ID, destinationFolderId);
    // Do not do the policy merge on the sub-object clone
    subflightInputParameters.put(WorkspaceFlightMapKeys.MERGE_POLICIES, false);

    // submit flight
    try {
      context
          .getStairway()
          .submit(
              subflightId, CloneControlledGcsBucketResourceFlight.class, subflightInputParameters);
    } catch (DuplicateFlightIdException unused) {
      return StepResult.getStepResultSuccess();
    } catch (DatabaseOperationException | StairwayExecutionException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  // Can't undo a launch step.
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
