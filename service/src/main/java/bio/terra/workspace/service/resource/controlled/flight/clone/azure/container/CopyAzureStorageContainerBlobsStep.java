package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureStorageAccessService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.BlobCopier;
import bio.terra.workspace.service.resource.controlled.cloud.azure.BlobCopyStatus;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class CopyAzureStorageContainerBlobsStep implements Step {
  private final ResourceDao resourceDao;
  private final ControlledAzureStorageContainerResource sourceContainer;
  private final AzureStorageAccessService azureStorageAccessService;
  private final AuthenticatedUserRequest userRequest;

  public CopyAzureStorageContainerBlobsStep(
      AzureStorageAccessService azureStorageAccessService,
      ControlledAzureStorageContainerResource sourceContainer,
      ResourceDao resourceDao,
      AuthenticatedUserRequest userRequest) {
    this.azureStorageAccessService = azureStorageAccessService;
    this.resourceDao = resourceDao;
    this.sourceContainer = sourceContainer;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    var inputParameters = flightContext.getInputParameters();

    FlightUtils.validateRequiredEntries(
        inputParameters,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_STORAGE_ACCOUNT_RESOURCE_ID,
        WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE_DEFINITION);

    var destinationWorkspaceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

    var destStorageAccountResourceId =
        flightContext
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys
                    .DESTINATION_STORAGE_ACCOUNT_RESOURCE_ID,
                UUID.class);

    ControlledAzureStorageResource destinationStorageAccount =
        resourceDao
            .getResource(destinationWorkspaceId, destStorageAccountResourceId)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT);

    ControlledAzureStorageResource sourceStorageAccount =
        resourceDao
            .getResource(sourceContainer.getWorkspaceId(), sourceContainer.getStorageAccountId())
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT);

    ControlledAzureStorageContainerResource destinationContainer =
        flightContext
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
                ControlledAzureStorageContainerResource.class);

    BlobCopier copier =
        new BlobCopier(
            azureStorageAccessService,
            sourceStorageAccount,
            destinationStorageAccount,
            sourceContainer,
            destinationContainer,
            userRequest);
    var results = copier.copyBlobs();
    if (results.containsKey(BlobCopyStatus.ERROR)) {
      FlightUtils.setErrorResponse(
          flightContext, "Blobs failed to copy", HttpStatus.INTERNAL_SERVER_ERROR);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
