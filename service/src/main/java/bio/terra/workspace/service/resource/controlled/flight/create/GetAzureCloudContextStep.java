package bio.terra.workspace.service.resource.controlled.flight.create;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.util.UUID;

/**
 * Retrieve the Azure cloud context, if applicable, and store it in the working map. Since this step
 * only reads data, it is idempotent
 */
public class GetAzureCloudContextStep implements Step {

  private final UUID workspaceUuid;
  private final AzureCloudContextService azureCloudContextService;

  public GetAzureCloudContextStep(
      UUID workspaceUuid, AzureCloudContextService azureCloudContextService) {
    this.workspaceUuid = workspaceUuid;
    this.azureCloudContextService = azureCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {

    FlightMap workingMap = flightContext.getWorkingMap();
    if (workingMap.get(AZURE_CLOUD_CONTEXT, AzureCloudContext.class) == null) {
      workingMap.put(
          AZURE_CLOUD_CONTEXT,
          azureCloudContextService.getRequiredAzureCloudContext(workspaceUuid));
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
