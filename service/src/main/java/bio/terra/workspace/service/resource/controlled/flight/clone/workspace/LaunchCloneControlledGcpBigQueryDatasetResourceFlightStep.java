package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntriesNonNull;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.DuplicateFlightIdSubmittedException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.stairway.exception.StairwayExecutionException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.CloneControlledGcpBigQueryDatasetResourceFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.UUID;

public class LaunchCloneControlledGcpBigQueryDatasetResourceFlightStep implements Step {

  private final ControlledBigQueryDatasetResource resource;
  private final String subflightId;

  public LaunchCloneControlledGcpBigQueryDatasetResourceFlightStep(
      ControlledBigQueryDatasetResource resource, String subflightId) {
    this.resource = resource;
    this.subflightId = subflightId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntriesNonNull(context.getInputParameters(),
        JobMapKeys.AUTH_USER_INFO.getKeyName());
    validateRequiredEntriesNonNull(context.getWorkingMap(),
        ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        ControlledResourceKeys.LOCATION);

    final var userRequest = context.getInputParameters()
        .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    final var destinationWorkspaceId = context.getWorkingMap()
        .get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    final var location = context.getWorkingMap()
        .get(ControlledResourceKeys.LOCATION, String.class);

    // build input parameter map. Leave out resource name, description, and dataset name.
    final var subflightInputParameters = new FlightMap();
    subflightInputParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    subflightInputParameters.put(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, destinationWorkspaceId);
    subflightInputParameters.put(ControlledResourceKeys.LOCATION, location);
    subflightInputParameters.put(ControlledResourceKeys.CLONING_INSTRUCTIONS, resource.getCloningInstructions());
    subflightInputParameters.put(JobMapKeys.REQUEST.getKeyName(), resource);

    // launch the flight
    try {
      context.getStairway().submit(subflightId, CloneControlledGcpBigQueryDatasetResourceFlight.class, subflightInputParameters);
    } catch (DuplicateFlightIdSubmittedException unused) {
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
