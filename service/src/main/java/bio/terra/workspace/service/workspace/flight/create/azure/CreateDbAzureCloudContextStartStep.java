package bio.terra.workspace.service.workspace.flight.create.azure;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import java.util.UUID;

/**
 * Stores the previously generated Google Project Id in the {@link WorkspaceDao} as the Google cloud
 * context for the workspace.
 */
public class CreateDbAzureCloudContextStartStep implements Step {
  private final UUID workspaceId;
  private final AzureCloudContextService azureCloudContextService;

  public CreateDbAzureCloudContextStartStep(
      UUID workspaceId, AzureCloudContextService azureCloudContextService) {
    this.workspaceId = workspaceId;
    this.azureCloudContextService = azureCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    azureCloudContextService.createAzureCloudContextStart(workspaceId, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Delete the cloud context, but only if it is the one we created
    azureCloudContextService.deleteAzureCloudContextWithFlightIdValidation(
        workspaceId, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }
}
