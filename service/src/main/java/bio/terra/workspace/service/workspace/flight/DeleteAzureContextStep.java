package bio.terra.workspace.service.workspace.flight;

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
  private final UUID workspaceId;

  protected DeleteAzureContextStep(
      AzureCloudContextService azureCloudContextService, UUID workspaceId) {
    this.azureCloudContextService = azureCloudContextService;
    this.workspaceId = workspaceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    azureCloudContextService.deleteAzureCloudContext(workspaceId);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    logger.error("Unable to undo DAO deletion of azure context [{}]", workspaceId);
    return context.getResult();
  }
}
