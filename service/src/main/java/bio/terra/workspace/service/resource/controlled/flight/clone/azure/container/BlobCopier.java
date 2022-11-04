package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import static java.util.stream.Collectors.groupingBy;

import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureStorageAccessService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.StorageAccountKeyProvider;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import com.azure.core.http.HttpClient;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.PollerFlux;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobCopyInfo;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.common.StorageSharedKeyCredential;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlobCopier {
  private final Logger logger = LoggerFactory.getLogger(BlobCopier.class);

  private final AzureStorageAccessService storageAccessService;
  private final ControlledAzureStorageResource sourceStorageAccount;
  private final ControlledAzureStorageResource destinationStorageAccount;
  private final ControlledAzureStorageContainerResource sourceContainer;
  private final AuthenticatedUserRequest userRequest;
  private final StorageAccountKeyProvider storageAccountKeyProvider;

  public BlobCopier(
      AzureStorageAccessService storageAccessService,
      StorageAccountKeyProvider storageAccountKeyProvider,
      ControlledAzureStorageResource sourceStorageAccount,
      ControlledAzureStorageResource destinationStorageAccount,
      ControlledAzureStorageContainerResource sourceContainer,
      AuthenticatedUserRequest userRequest) {
    this.storageAccountKeyProvider = storageAccountKeyProvider;
    this.storageAccessService = storageAccessService;
    this.sourceContainer = sourceContainer;
    this.sourceStorageAccount = sourceStorageAccount;
    this.destinationStorageAccount = destinationStorageAccount;
    this.userRequest = userRequest;
  }

  public Map<BlobCopyStatus, List<PollResponse<BlobCopyInfo>>> copyBlobs() {
    StorageSharedKeyCredential sourceStorageAccountKey =
        storageAccountKeyProvider.getStorageAccountKey(
            sourceContainer.getWorkspaceId(), sourceStorageAccount.getStorageAccountName());
    BlobContainerClient sourceBlobContainerClient =
        new BlobContainerClientBuilder()
            .credential(sourceStorageAccountKey)
            .endpoint(sourceStorageAccount.getStorageAccountEndpoint())
            .httpClient(HttpClient.createDefault())
            .containerName(sourceContainer.getStorageContainerName())
            .buildClient();

    BlobContainerAsyncClient destinationAsyncBlobContainerClient =
        new BlobContainerClientBuilder()
            .credential(sourceStorageAccountKey)
            .endpoint(destinationStorageAccount.getStorageAccountEndpoint())
            .httpClient(HttpClient.createDefault())
            .containerName(sourceContainer.getStorageContainerName())
            .buildAsyncClient();

    var blobItems =
        sourceBlobContainerClient.listBlobs().stream()
            .filter(blobItem -> blobItem.getProperties().getContentLength() > 0);

    var blobPollers =
        blobItems.map(
            sourceBlobItem ->
                beginCopyBlob(
                    sourceBlobItem,
                    sourceBlobContainerClient,
                    destinationAsyncBlobContainerClient));

    logger.info("Copying blobs from source container");
    var pollResults = blobPollers.map(blobPoller -> blobPoller.getSyncPoller().waitForCompletion());
    logger.info("Blob copy complete.");

    return pollResults.collect(
        groupingBy(
            pollResponse -> {
              if (pollResponse.getStatus().equals(LongRunningOperationStatus.FAILED)
                  || pollResponse.getStatus().equals(LongRunningOperationStatus.NOT_STARTED)
                  || pollResponse.getStatus().equals(LongRunningOperationStatus.USER_CANCELLED)
                  || pollResponse.getStatus().equals(LongRunningOperationStatus.IN_PROGRESS)) {
                return BlobCopyStatus.ERROR;
              } else if (pollResponse
                  .getStatus()
                  .equals(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED)) {
                return BlobCopyStatus.SUCCESS;
              }
              throw new RuntimeException(
                  "Unknown operation status type " + pollResponse.getStatus());
            }));
  }

  private PollerFlux<BlobCopyInfo, Void> beginCopyBlob(
      BlobItem sourceBlobItem,
      BlobContainerClient sourceBlobContainerClient,
      BlobContainerAsyncClient destBlobContainerClient) {
    var sasBundle =
        this.storageAccessService.createAzureStorageContainerSasToken(
            this.sourceContainer.getWorkspaceId(),
            this.sourceContainer,
            this.sourceStorageAccount,
            OffsetDateTime.now().minusMinutes(15),
            OffsetDateTime.now().plusMinutes(15),
            userRequest,
            null,
            sourceBlobItem.getName(),
            "r");
    var sourceBlobClient = sourceBlobContainerClient.getBlobClient(sourceBlobItem.getName());
    var destinationBlobClient =
        destBlobContainerClient.getBlobAsyncClient(sourceBlobItem.getName());
    return destinationBlobClient.beginCopy(
        sourceBlobClient.getBlobUrl() + "?" + sasBundle.sasToken(), Duration.ofSeconds(1));
  }
}
