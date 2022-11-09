package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static java.util.stream.Collectors.groupingBy;

import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.SyncPoller;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobCopyInfo;
import com.azure.storage.blob.models.BlobItem;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlobCopier {
  private final Logger logger = LoggerFactory.getLogger(BlobCopier.class);
  private final AzureStorageAccessService storageAccessService;
  private final AuthenticatedUserRequest userRequest;

  private static final Duration MAX_POLL_TIMEOUT = Duration.ofMinutes(10);

  public BlobCopier(
      AzureStorageAccessService storageAccessService, AuthenticatedUserRequest userRequest) {
    this.storageAccessService = storageAccessService;
    this.userRequest = userRequest;
  }

  /**
   * Attempts to copy all blobs from the source container to the destination container. The calling
   * principal must have READ-level access on the storage container resource.
   *
   * @param sourceStorageAccount Azure storage account for the source container
   * @param destinationStorageAccount Azure storage account for the destination container
   * @param sourceContainer Azure storage container containing the blobs to be copied
   * @param destinationContainer Azure storage container that will receive the copied blobs
   * @return BlobCopyResult containing the status of each copy operation
   */
  public BlobCopierResult copyBlobs(
      ControlledAzureStorageResource sourceStorageAccount,
      ControlledAzureStorageResource destinationStorageAccount,
      ControlledAzureStorageContainerResource sourceContainer,
      ControlledAzureStorageContainerResource destinationContainer) {
    var sourceBlobContainerClient =
        storageAccessService.buildBlobContainerClient(sourceContainer, sourceStorageAccount);
    var destinationBlobCOntainerClient =
        storageAccessService.buildBlobContainerClient(
            destinationContainer, destinationStorageAccount);

    // filter out any zero-length blobs, these are not copyable
    var blobItems =
        sourceBlobContainerClient.listBlobs().stream()
            .filter(blobItem -> blobItem.getProperties().getContentLength() > 0);

    var blobPollers =
        blobItems.map(
            sourceBlobItem ->
                beginCopyBlob(
                    storageAccessService,
                    sourceContainer,
                    sourceStorageAccount,
                    userRequest,
                    sourceBlobItem,
                    sourceBlobContainerClient,
                    destinationBlobCOntainerClient));

    logger.info(
        "Copying blobs [source_container_id = {}, source_workspace_id = {}, destination_container_id = {}, destination_workspace_id={}]",
        sourceContainer.getResourceId(),
        sourceContainer.getWorkspaceId(),
        destinationContainer.getResourceId(),
        destinationContainer.getWorkspaceId());
    var pollResults = blobPollers.map(blobPoller -> blobPoller.waitForCompletion(MAX_POLL_TIMEOUT));
    logger.info(
        "Finished copying blobs [source_container_id = {}, source_workspace_id = {}, destination_container_id = {}, destination_workspace_id={}]",
        sourceContainer.getResourceId(),
        sourceContainer.getWorkspaceId(),
        destinationContainer.getResourceId(),
        destinationContainer.getWorkspaceId());

    var resultsByStatus = pollResults.collect(groupingBy(PollResponse::getStatus));
    return new BlobCopierResult(resultsByStatus);
  }

  private SyncPoller<BlobCopyInfo, Void> beginCopyBlob(
      AzureStorageAccessService storageAccessService,
      ControlledAzureStorageContainerResource sourceContainer,
      ControlledAzureStorageResource sourceStorageAccount,
      AuthenticatedUserRequest userRequest,
      BlobItem sourceBlobItem,
      BlobContainerClient sourceBlobContainerClient,
      BlobContainerClient destBlobContainerClient) {
    var sasBundle =
        storageAccessService.createAzureStorageContainerSasToken(
            sourceContainer.getWorkspaceId(),
            sourceContainer,
            sourceStorageAccount,
            userRequest,
            null,
            sourceBlobItem.getName(),
            "r");
    var sourceBlobClient = sourceBlobContainerClient.getBlobClient(sourceBlobItem.getName());
    var destinationBlobClient = destBlobContainerClient.getBlobClient(sourceBlobItem.getName());
    var sourceBlobUrl = sourceBlobClient.getBlobUrl() + "?" + sasBundle.sasToken();

    return destinationBlobClient.beginCopy(sourceBlobUrl, Duration.ofSeconds(1));
  }
}
