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
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.ControlledAzureManagedIdentityResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.managedIdentity.CloneControlledAzureManagedIdentityResourceFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.UUID;

public class LaunchCloneControlledAzureManagedIdentityResourceFlightStep implements Step {

  private final ControlledAzureManagedIdentityResource sourceResource;
  private final String subflightId;
  private final UUID destinationResourceId;

  public LaunchCloneControlledAzureManagedIdentityResourceFlightStep(
      ControlledAzureManagedIdentityResource sourceResource,
      String subflightId,
      UUID destinationResourceId) {
    this.sourceResource = sourceResource;
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

    // build input parameter map.
    var subflightInputParameters = new FlightMap();
    subflightInputParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    subflightInputParameters.put(
        ControlledResourceKeys.DESTINATION_WORKSPACE_ID, destinationWorkspaceId);
    subflightInputParameters.put(ResourceKeys.RESOURCE, sourceResource);
    subflightInputParameters.put(
        JobMapKeys.DESCRIPTION.getKeyName(),
        String.format(
            "Clone Azure Managed Identity %s", sourceResource.getResourceId().toString()));
    String destinationIdentityName = sourceResource.getName();
    subflightInputParameters.put(
        ResourceKeys.RESOURCE_NAME,
        destinationIdentityName.substring(0, Math.min(63, destinationIdentityName.length())));
    subflightInputParameters.put(
        ControlledResourceKeys.DESTINATION_RESOURCE_ID, destinationResourceId);
    // Do not do the policy merge on the sub-object clone. Policies are propagated to the
    // destination workspace as a separate step during the workspace clone flight, so we do not
    // do a policy merge for individual resource clones within the workspace
    subflightInputParameters.put(WorkspaceFlightMapKeys.MERGE_POLICIES, false);

    // launch the flight
    try {
      context
          .getStairway()
          .submit(
              subflightId,
              CloneControlledAzureManagedIdentityResourceFlight.class,
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
