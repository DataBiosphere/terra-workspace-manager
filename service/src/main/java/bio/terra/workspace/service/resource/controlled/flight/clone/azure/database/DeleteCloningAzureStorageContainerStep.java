package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import bio.terra.stairway.*;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delete the storage container used in cloning. General storage container deletion is done in
 * bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.DeleteAzureStorageContainerStep
 */
public class DeleteCloningAzureStorageContainerStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(DeleteCloningAzureStorageContainerStep.class);
  private final ControlledResourceService controlledResourceService;
  private final String storageContainerName;
  private final UUID resourceId;

  public DeleteCloningAzureStorageContainerStep(
      String storageContainerName,
      UUID resourceId,
      ControlledResourceService controlledResourceService) {
    this.controlledResourceService = controlledResourceService;
    this.storageContainerName = storageContainerName;
    this.resourceId = resourceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // this is an unusual usage of the .undoStep method. ideally, storage container
    // creation/deletion would be abstracted into a service-level method.
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
