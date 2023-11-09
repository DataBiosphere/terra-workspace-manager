package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.*;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.DuplicateFlightIdException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.stairway.exception.StairwayExecutionException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.database.CloneControlledAzureDatabaseResourceFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;

import java.util.UUID;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;

public class LaunchCloneControlledAzureDatabaseResourceFlightStep implements Step {

    private final ControlledAzureDatabaseResource sourceResource;
    private final String subflightId;
    private final UUID destinationResourceId;

    public LaunchCloneControlledAzureDatabaseResourceFlightStep(ControlledAzureDatabaseResource sourceResource, String subflightId, UUID destinationResourceId) {
        this.sourceResource = sourceResource;
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
                        .get(WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

        // build input parameter map
        var subflightInputParameters = new FlightMap();
        subflightInputParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
        subflightInputParameters.put(
                WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, destinationWorkspaceId);
        subflightInputParameters.put(
                WorkspaceFlightMapKeys.ResourceKeys.CLONING_INSTRUCTIONS, sourceResource.getCloningInstructions());
        subflightInputParameters.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, sourceResource);
        subflightInputParameters.put(
                JobMapKeys.DESCRIPTION.getKeyName(),
                String.format(
                        "Clone Azure Controlled Database %s", sourceResource.getResourceId().toString()));
        subflightInputParameters.put(
                WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID, destinationResourceId);
        // TODO: Is this needed?
        // Do not do the policy merge on the sub-object clone. Policies are propagated to the
        // destination workspace as a separate step during the workspace clone flight, so we do not
        // do a policy merge for individual resource clones within the workspace
        subflightInputParameters.put(WorkspaceFlightMapKeys.MERGE_POLICIES, false);

        // TODO: What other flight input parameters are needed?

        // launch the flight
        try {
            context
                    .getStairway()
                    .submit(
                            subflightId,
                            CloneControlledAzureDatabaseResourceFlight.class,
                            subflightInputParameters);
        } catch (DuplicateFlightIdException unused) {
            return StepResult.getStepResultSuccess();
        } catch (DatabaseOperationException | StairwayExecutionException e) {
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
