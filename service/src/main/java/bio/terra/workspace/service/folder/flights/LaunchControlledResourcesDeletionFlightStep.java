package bio.terra.workspace.service.folder.flights;

import static bio.terra.workspace.common.utils.FlightUtils.getRequired;

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
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.UUID;

public class LaunchControlledResourcesDeletionFlightStep implements Step {

  private final UUID folderId;

  public LaunchControlledResourcesDeletionFlightStep(UUID folderId) {
    this.folderId = folderId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {

    var userRequest =
        getRequired(
            context.getInputParameters(),
            JobMapKeys.AUTH_USER_INFO.getKeyName(),
            AuthenticatedUserRequest.class);
    String subFlightId =
        getRequired(
            context.getWorkingMap(),
            ControlledResourceKeys.DELETE_RESOURCES_FLIGHT_ID,
            String.class);

    List<WsmResource> controlledResourcesToDelete =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.RESOURCES_TO_DELETE, new TypeReference<>() {});
    // build input parameter map. Leave out resource name, description, and dataset name so that
    // they will take values from the source dataset.
    var subflightInputParameters = new FlightMap();
    subflightInputParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    subflightInputParameters.put(
        ControlledResourceKeys.RESOURCES_TO_DELETE, controlledResourcesToDelete);
    subflightInputParameters.put(
        JobMapKeys.DESCRIPTION.getKeyName(),
        String.format("Delete controlled resources in folder %s", folderId));

    // launch the flight
    try {
      context
          .getStairway()
          .submit(subFlightId, DeleteControlledResourcesFlight.class, subflightInputParameters);
    } catch (DuplicateFlightIdException unused) {
      return StepResult.getStepResultSuccess();
    } catch (DatabaseOperationException | StairwayExecutionException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
