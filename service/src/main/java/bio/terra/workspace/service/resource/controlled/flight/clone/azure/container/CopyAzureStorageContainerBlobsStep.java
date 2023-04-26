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
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import org.springframework.http.HttpStatus;

public class CopyAzureStorageContainerBlobsStep implements Step {
  private final ResourceDao resourceDao;
  private final ControlledAzureStorageContainerResource sourceContainer;
  private final AzureStorageAccessService azureStorageAccessService;
  private final AuthenticatedUserRequest userRequest;
  private final BlobCopier blobCopier;

  public CopyAzureStorageContainerBlobsStep(
      AzureStorageAccessService azureStorageAccessService,
      ControlledAzureStorageContainerResource sourceContainer,
      ResourceDao resourceDao,
      AuthenticatedUserRequest userRequest,
      BlobCopier blobCopier) {
    this.azureStorageAccessService = azureStorageAccessService;
    this.resourceDao = resourceDao;
    this.sourceContainer = sourceContainer;
    this.userRequest = userRequest;
    this.blobCopier = blobCopier;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    var inputParameters = flightContext.getInputParameters();

    FlightUtils.validateRequiredEntries(
        inputParameters, WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID);
    FlightUtils.validateRequiredEntries(
        flightContext.getWorkingMap(),
        WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE_DEFINITION);

    ControlledAzureStorageContainerResource destinationContainer =
        flightContext
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
                ControlledAzureStorageContainerResource.class);

    List<String> prefixesToClone =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.PREFIXES_TO_CLONE,
            new TypeReference<>() {});

    var sourceStorageData =
        azureStorageAccessService.getStorageAccountData(
            sourceContainer.getWorkspaceId(), sourceContainer.getResourceId(), userRequest);
    var destStorageData =
        azureStorageAccessService.getStorageAccountData(
            destinationContainer.getWorkspaceId(),
            destinationContainer.getResourceId(),
            userRequest);

    var results = blobCopier.copyBlobs(sourceStorageData, destStorageData, prefixesToClone);
    if (results.anyFailures()) {
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
