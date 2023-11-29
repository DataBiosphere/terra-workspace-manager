package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import bio.terra.stairway.*;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteAzureStorageContainerStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(DeleteAzureStorageContainerStep.class);
  private final ControlledResourceService controlledResourceService;
  private final String storageContainerName;
  private final UUID resourceId;

  public DeleteAzureStorageContainerStep(
      String storageContainerName,
      UUID resourceId,
      ControlledResourceService controlledResourceService) {
    this.controlledResourceService = controlledResourceService;
    this.storageContainerName = storageContainerName;
    this.resourceId = resourceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    return new CreateAzureStorageContainerStep(
            storageContainerName, resourceId, controlledResourceService)
        .undoStep(context);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    logger.error("Cannot undo deletion of Azure Storage Container resource {}.", resourceId);
    return StepResult.getStepResultSuccess();
  }
}
