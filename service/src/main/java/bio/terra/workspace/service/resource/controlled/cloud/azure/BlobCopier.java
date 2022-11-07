package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static java.util.stream.Collectors.groupingBy;

import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.PollerFlux;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobCopyInfo;
import com.azure.storage.blob.models.BlobItem;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlobCopier {
  private final Logger logger = LoggerFactory.getLogger(BlobCopier.class);

  private final AzureStorageAccessService storageAccessService;
  private final ControlledAzureStorageResource sourceStorageAccount;
  private final ControlledAzureStorageResource destinationStorageAccount;
  private final ControlledAzureStorageContainerResource sourceContainer;
  private final ControlledAzureStorageContainerResource destinationContainer;
  private final AuthenticatedUserRequest userRequest;

  private static final Duration MAX_POLL_TIMEOUT = Duration.ofMinutes(10);
  private static final Set<LongRunningOperationStatus> ERROR_POLL_STATES =
      Set.of(
          LongRunningOperationStatus.FAILED,
          LongRunningOperationStatus.IN_PROGRESS,
          LongRunningOperationStatus.USER_CANCELLED,
          LongRunningOperationStatus.NOT_STARTED);

  public BlobCopier(
      AzureStorageAccessService storageAccessService,
      ControlledAzureStorageResource sourceStorageAccount,
      ControlledAzureStorageResource destinationStorageAccount,
      ControlledAzureStorageContainerResource sourceContainer,
      ControlledAzureStorageContainerResource destinationContainer,
      AuthenticatedUserRequest userRequest) {
    this.storageAccessService = storageAccessService;
    this.sourceContainer = sourceContainer;
    this.destinationContainer = destinationContainer;
    this.sourceStorageAccount = sourceStorageAccount;
    this.destinationStorageAccount = destinationStorageAccount;
    this.userRequest = userRequest;
  }

  /**
   * Attempts to copy blobs from the source container to the destination container.
   *
   * @return Map of BlobCopyStatus to poll responses; Any blob that results in a polling result
   *     other than LongRunningOperationStatus.SUCCESSFULLY_COMPLETED is considered failed.
   */
  public Map<BlobCopyStatus, List<PollResponse<BlobCopyInfo>>> copyBlobs() {
    var sourceBlobContainerClient =
        storageAccessService.buildBlobContainerClient(sourceContainer, sourceStorageAccount);
    var destinationAsyncBlobContainerClient =
        storageAccessService.buildBlobContainerAsyncClient(
            destinationContainer, destinationStorageAccount);

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

    logger.info(
        "Copying blobs [source_container_id = {}, source_workspace_id = {}, destination_container_id = {}, destination_workspace_id={}]",
        sourceContainer.getResourceId(),
        sourceContainer.getWorkspaceId(),
        destinationContainer.getResourceId(),
        destinationContainer.getWorkspaceId());
    var pollResults =
        blobPollers.map(
            blobPoller -> blobPoller.getSyncPoller().waitForCompletion(MAX_POLL_TIMEOUT));

    return pollResults.collect(
        groupingBy(
            pollResponse -> {
              if (pollResponse
                  .getStatus()
                  .equals(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED)) {
                return BlobCopyStatus.SUCCESS;
              } else if (ERROR_POLL_STATES.contains(pollResponse.getStatus())) {
                return BlobCopyStatus.ERROR;
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
