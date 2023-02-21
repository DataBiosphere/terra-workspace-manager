package bio.terra.workspace.service.workspace.flight.azure;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteAzureContextStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(DeleteAzureContextStep.class);
  private final AzureCloudContextService azureCloudContextService;
  private final UUID workspaceUuid;

  public DeleteAzureContextStep(
      AzureCloudContextService azureCloudContextService, UUID workspaceUuid) {
    this.azureCloudContextService = azureCloudContextService;
    this.workspaceUuid = workspaceUuid;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    if (azureCloudContextService.getAzureCloudContext(workspaceUuid).isPresent()) {
      azureCloudContextService.deleteAzureCloudContext(workspaceUuid);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    logger.error("Unable to undo DAO deletion of azure context [{}]", workspaceUuid);
    return context.getResult();
  }
}
