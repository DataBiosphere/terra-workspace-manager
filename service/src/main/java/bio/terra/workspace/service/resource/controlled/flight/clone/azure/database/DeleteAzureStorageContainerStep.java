package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;

import bio.terra.stairway.*;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.IamRoleUtils;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.azure.core.management.exception.ManagementException;
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
    return new CreateAzureStorageContainerStep(storageContainerName, resourceId, controlledResourceService).undoStep(context);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    logger.error(
            "Cannot undo deletion of Azure Storage Container resource {}.",
            resourceId);
    return StepResult.getStepResultSuccess();
  }
}
