package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Gets an Azure Managed Identity that exists in a workspace. Failure behavior is configured via the
 * MissingIdentityBehavior parameter; if the identity is not found and the behavior is
 * ALLOW_MISSING, the step will succeed. This helps in deletion scenarios when the managed identity
 * may have already been deleted out of band but consuming flights want to proceed.
 *
 * <p>This implements the marker interface DeleteControlledResourceStep, in order to indicate that
 * it is also used when deleting the resource
 */
public class GetWorkspaceManagedIdentityStep
    implements DeleteControlledResourceStep, GetManagedIdentityStep {
  private final UUID workspaceId;
  private final String managedIdentityName;
  private final MissingIdentityBehavior missingIdentityBehavior;
  private final ManagedIdentityHelper managedIdentityHelper;

  public GetWorkspaceManagedIdentityStep(
      UUID workspaceId,
      String managedIdentityName,
      MissingIdentityBehavior failOnMissing,
      ManagedIdentityHelper managedIdentityHelper) {
    this.workspaceId = workspaceId;
    this.managedIdentityName = managedIdentityName;
    this.missingIdentityBehavior = failOnMissing;

    this.managedIdentityHelper = managedIdentityHelper;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    try {
      var uami =
          managedIdentityHelper.getManagedIdentity(
              azureCloudContext, workspaceId, managedIdentityName);
      if (uami.isPresent()) {
        putManagedIdentityInContext(context, uami.get());
        return StepResult.getStepResultSuccess();
      } else if (missingIdentityBehavior == MissingIdentityBehavior.ALLOW_MISSING) {
        return StepResult.getStepResultSuccess();
      } else {
        return new StepResult(
            StepStatus.STEP_RESULT_FAILURE_FATAL,
            new ResourceNotFoundException(
                String.format(
                    "Could not find managed identity %s in workspace %s",
                    managedIdentityName, workspaceId)));
      }
    } catch (ManagementException e) {
      var is4xxError =
          AzureManagementExceptionUtils.getHttpStatus(e)
              .map(HttpStatus::is4xxClientError)
              .orElse(false);
      if (is4xxError && missingIdentityBehavior == MissingIdentityBehavior.ALLOW_MISSING) {
        return StepResult.getStepResultSuccess();
      } else if (is4xxError) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
      } else {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      }
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo
    return StepResult.getStepResultSuccess();
  }
}
