package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;

import bio.terra.stairway.*;
import bio.terra.stairway.exception.DuplicateFlightIdException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.stairway.exception.StairwayExecutionException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;

public class LaunchCreateControlledResourceFlight implements Step {
  private final ControlledAzureStorageContainerResource storageContainerResource;
  private final String subflightId;
  private final UUID destinationResourceId;

  public LaunchCreateControlledResourceFlight(
      ControlledAzureStorageContainerResource storageContainerResource,
      String subflightId,
      UUID destinationResourceId) {
    this.storageContainerResource = storageContainerResource;
    this.subflightId = subflightId;
    this.destinationResourceId = destinationResourceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntries(
        context.getInputParameters(),
        JobMapKeys.AUTH_USER_INFO.getKeyName(),
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID);

    var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    var destinationWorkspaceId =
        context
            .getInputParameters()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

    // build input parameter map
    var subflightInputParameters = new FlightMap();
    subflightInputParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    subflightInputParameters.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        destinationWorkspaceId);
    subflightInputParameters.put(
        WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, storageContainerResource);
    subflightInputParameters.put(
        JobMapKeys.DESCRIPTION.getKeyName(),
        String.format(
            "Creating Azure Controlled Resource %s",
            storageContainerResource.getResourceId().toString()));
    subflightInputParameters.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID,
        destinationResourceId);
    // Do not do the policy merge on the sub-object clone. Policies are propagated to the
    // destination workspace as a separate step during the workspace clone flight, so we do not
    // do a policy merge for individual resource clones within the workspace
    subflightInputParameters.put(WorkspaceFlightMapKeys.MERGE_POLICIES, false);

    // TODO: Additional inputs as needed to be added in
    // https://broadworkbench.atlassian.net/browse/WM-2349

    // launch the flight
    try {
      context
          .getStairway()
          .submit(subflightId, CreateControlledResourceFlight.class, subflightInputParameters);
    } catch (DuplicateFlightIdException unused) {
      return StepResult.getStepResultSuccess();
    } catch (StairwayExecutionException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo; can't undo a launch step
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
