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
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.CloneControlledGcpBigQueryDatasetResourceFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.UUID;

public class LaunchCloneControlledGcpBigQueryDatasetResourceFlightStep implements Step {

  private final ControlledBigQueryDatasetResource resource;
  private final String subflightId;
  private final UUID destinationResourceId;

  public LaunchCloneControlledGcpBigQueryDatasetResourceFlightStep(
      ControlledBigQueryDatasetResource resource, String subflightId, UUID destinationResourceId) {
    this.resource = resource;
    this.subflightId = subflightId;
    this.destinationResourceId = destinationResourceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntries(
        context.getInputParameters(),
        JobMapKeys.AUTH_USER_INFO.getKeyName(),
        ControlledResourceKeys.DESTINATION_WORKSPACE_ID);

    var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    var destinationWorkspaceId =
        context
            .getInputParameters()
            .get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    var location = context.getInputParameters().get(ControlledResourceKeys.LOCATION, String.class);

    // build input parameter map. Leave out resource name, description, and dataset name so that
    // they will take values from the source dataset.
    var subflightInputParameters = new FlightMap();
    subflightInputParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    subflightInputParameters.put(
        ControlledResourceKeys.DESTINATION_WORKSPACE_ID, destinationWorkspaceId);
    subflightInputParameters.put(ControlledResourceKeys.LOCATION, location);
    subflightInputParameters.put(
        ControlledResourceKeys.CLONING_INSTRUCTIONS, resource.getCloningInstructions());
    subflightInputParameters.put(ResourceKeys.RESOURCE, resource);
    subflightInputParameters.put(ControlledResourceKeys.LOCATION, location);
    subflightInputParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, destinationWorkspaceId);
    subflightInputParameters.put(
        JobMapKeys.DESCRIPTION.getKeyName(),
        String.format("Clone BigQuery Dataset %s", resource.getResourceId().toString()));
    subflightInputParameters.put(
        ControlledResourceKeys.DESTINATION_RESOURCE_ID, destinationResourceId);
    // Do not do the policy merge on the sub-object clone
    subflightInputParameters.put(WorkspaceFlightMapKeys.MERGE_POLICIES, false);

    // launch the flight
    try {
      context
          .getStairway()
          .submit(
              subflightId,
              CloneControlledGcpBigQueryDatasetResourceFlight.class,
              subflightInputParameters);
    } catch (DuplicateFlightIdException unused) {
      return StepResult.getStepResultSuccess();
    } catch (DatabaseOperationException | StairwayExecutionException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
