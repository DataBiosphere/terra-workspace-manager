package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.StorageAccountKeyProvider;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.azure.core.http.HttpClient;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.PollerFlux;
import com.azure.core.util.polling.SyncPoller;
import com.azure.storage.blob.BlobAsyncClient;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobCopyInfo;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// TODO WOR-591 This step will be responsible for copying files from the
// source container to the destination
public class CopyAzureStorageContainerBlobsStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(CopyAzureStorageContainerBlobsStep.class);
  private final StorageAccountKeyProvider storageAccountKeyProvider;
  private final ResourceDao resourceDao;
  private final ControlledAzureStorageContainerResource sourceContainer;

  public CopyAzureStorageContainerBlobsStep(
      ControlledAzureStorageContainerResource sourceContainer,
      StorageAccountKeyProvider storageAccountKeyProvider,
      ResourceDao resourceDao) {
    this.storageAccountKeyProvider = storageAccountKeyProvider;
    this.resourceDao = resourceDao;
    this.sourceContainer = sourceContainer;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    try {
      var inputParameters = flightContext.getInputParameters();

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
      var destStorageContainer =
          flightContext
              .getWorkingMap()
              .get(
                  WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
                  ControlledAzureStorageContainerResource.class);

      ControlledAzureStorageResource destinationStorageAccountResource =
          resourceDao
              .getResource(destinationWorkspaceId, destStorageAccountResourceId)
              .castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT);

      StorageSharedKeyCredential destStorageAcctKey =
          storageAccountKeyProvider.getStorageAccountKey(
              destinationWorkspaceId, destinationStorageAccountResource.getStorageAccountName());
      String endpoint = destinationStorageAccountResource.getStorageAccountEndpoint();

      ControlledAzureStorageResource sourceStorageAccount =
          resourceDao
              .getResource(sourceContainer.getWorkspaceId(), sourceContainer.getStorageAccountId())
              .castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT);
      String sourceStorageEndpoint = sourceStorageAccount.getStorageAccountEndpoint();

      StorageSharedKeyCredential sourceStorageAccountKey =
          storageAccountKeyProvider.getStorageAccountKey(
              sourceContainer.getWorkspaceId(), sourceStorageAccount.getStorageAccountName());
      BlobContainerClient sourceBlobContainerClient =
          new BlobContainerClientBuilder()
              .credential(sourceStorageAccountKey)
              .endpoint(sourceStorageEndpoint)
              .httpClient(HttpClient.createDefault())
              .containerName(sourceContainer.getStorageContainerName())
              .buildClient();

      BlobContainerClient destinationBlobContainerClient =
          new BlobContainerClientBuilder()
              .credential(sourceStorageAccountKey)
              .endpoint(sourceStorageEndpoint)
              .httpClient(HttpClient.createDefault())
              .containerName(sourceContainer.getStorageContainerName())
              .buildClient();

      BlobContainerAsyncClient destinationAsyncBlobContainerClient =
          new BlobContainerClientBuilder()
              .credential(sourceStorageAccountKey)
              .endpoint(sourceStorageEndpoint)
              .httpClient(HttpClient.createDefault())
              .containerName(sourceContainer.getStorageContainerName())
              .buildAsyncClient();

      var pollerStream =
          sourceBlobContainerClient.listBlobs().stream()
              .limit(100)
              .filter(blobItem -> blobItem.getProperties().getContentLength() > 0)
              .map(
                  blobItem -> {
                    var sourceBlobClient =
                        sourceBlobContainerClient.getBlobClient(blobItem.getName());
                    var blobSasToken =
                        sourceBlobClient.generateSas(
                            new BlobServiceSasSignatureValues(
                                OffsetDateTime.now().plusHours(24),
                                new BlobSasPermission().setReadPermission(true)));
                    return beginAsyncCopy(
                        sourceBlobClient.getBlobUrl(),
                        blobSasToken,
                        destinationAsyncBlobContainerClient.getBlobAsyncClient(blobItem.getName()));
                  });
      var sas =
          destinationBlobContainerClient.generateSas(
              new BlobServiceSasSignatureValues(
                  OffsetDateTime.now().plusHours(24),
                  new BlobSasPermission().setReadPermission(true).setListPermission(true)));

      logger.info("Polling on copy completion...");
      var pollResults =
          pollerStream
              .map(
                  poller -> {
                    logger.info("Poller done");
                    return poller.getSyncPoller().waitForCompletion();
                  })
              .toList();
      logger.info("Done.");
      var pollStatuses =
          pollResults.stream().map(PollResponse::getStatus).collect(Collectors.toSet());
      if (pollStatuses.equals(Set.of(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED))) {
        // we're good
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
      }
      for (var status :
          List.of(
              LongRunningOperationStatus.FAILED,
              LongRunningOperationStatus.USER_CANCELLED,
              LongRunningOperationStatus.IN_PROGRESS,
              LongRunningOperationStatus.NOT_STARTED)) {
        if (pollStatuses.contains(status)) {
          // whoops
          return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
        }
      }

    } catch (Exception e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
    }

    return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  private SyncPoller<BlobCopyInfo, Void> beginCopy(
      String blobUrl, String sasToken, BlobClient destinationBlobClient) {
    return destinationBlobClient.beginCopy(blobUrl + "?" + sasToken, Duration.ofMillis(500));
  }

  private PollerFlux<BlobCopyInfo, Void> beginAsyncCopy(
      String blobUrl, String sasToken, BlobAsyncClient destinationBlobClient) {
    return destinationBlobClient.beginCopy(blobUrl + "?" + sasToken, Duration.ofMillis(500));
  }
}
