package bio.terra.workspace.service.workspace.flight.azure;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import java.util.UUID;

public class CreateDbAzureCloudContextStartStep implements Step {
  private final UUID workspaceUuid;
  private final AzureCloudContextService azureCloudContextService;

  public CreateDbAzureCloudContextStartStep(
      UUID workspaceUuid, AzureCloudContextService azureCloudContextService) {
    this.workspaceUuid = workspaceUuid;
    this.azureCloudContextService = azureCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    azureCloudContextService.createAzureCloudContextStart(
        workspaceUuid, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Delete the cloud context, but only if it is the one we created
    azureCloudContextService.deleteAzureCloudContextWithFlightIdValidation(
        workspaceUuid, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }
}
