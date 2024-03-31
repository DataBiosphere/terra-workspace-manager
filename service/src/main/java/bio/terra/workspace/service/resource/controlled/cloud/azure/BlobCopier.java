package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static bio.terra.workspace.common.utils.FlightUtils.CLONE_SUBFLIGHT_TOTAL_DURATION;
import static java.util.stream.Collectors.groupingBy;

import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.SyncPoller;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobCopyInfo;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.options.BlobBeginCopyOptions;
import java.util.List;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlobCopier {
  private final Logger logger = LoggerFactory.getLogger(BlobCopier.class);
  private final AzureStorageAccessService storageAccessService;
  private final AuthenticatedUserRequest userRequest;

  public BlobCopier(
      AzureStorageAccessService storageAccessService, AuthenticatedUserRequest userRequest) {
    this.storageAccessService = storageAccessService;
    this.userRequest = userRequest;
  }

  /**
   * Attempts to copy all blobs from the source container to the destination container. The calling
   * principal must have READ-level access on the storage container resource.
   *
   * @param sourceStorageData Azure storage container containing the blobs to be copied
   * @param destStorageData Azure storage container that will receive the copied blobs
   * @param prefixesToCopy optional array of prefixes that will filter which blobs to copy
   * @return BlobCopyResult containing the status of each copy operation
   */
  public BlobCopierResult copyBlobs(
      StorageData sourceStorageData,
      StorageData destStorageData,
      @Nullable List<String> prefixesToCopy) {
    var sourceBlobContainerClient =
        storageAccessService.buildBlobContainerClient(sourceStorageData);
    var destinationBlobContainerClient =
        storageAccessService.buildBlobContainerClient(destStorageData);

    // Directories are presented as zero-length blobs, filter these out as they are not
    // copy-able. Also filter out blobs that should not be copied if `prefixToCopy` was specified.
    var copyAllBlobs = prefixesToCopy == null || prefixesToCopy.size() == 0;
    var blobItems =
        sourceBlobContainerClient.listBlobs().stream()
            .filter(
                blobItem ->
                    blobItem.getProperties().getContentLength() > 0
                        && (copyAllBlobs
                            || prefixesToCopy.stream()
                                .anyMatch(prefix -> blobItem.getName().startsWith(prefix))));

    var blobPollers =
        blobItems.map(
            sourceBlobItem ->
                beginCopyBlob(
                    storageAccessService,
                    sourceStorageData.storageContainerResource(),
                    userRequest,
                    sourceBlobItem,
                    sourceBlobContainerClient,
                    destinationBlobContainerClient));

    logger.info(
        "Copying blobs [source_container_id = {}, source_workspace_id = {}, destination_container_id = {}, destination_workspace_id={}]",
        sourceStorageData.storageContainerResource().getResourceId(),
        sourceStorageData.storageContainerResource().getWorkspaceId(),
        destStorageData.storageContainerResource().getResourceId(),
        destStorageData.storageContainerResource().getWorkspaceId());
    var pollResults =
        blobPollers.map(blobPoller -> blobPoller.waitForCompletion(CLONE_SUBFLIGHT_TOTAL_DURATION));
    logger.info(
        "Finished copying blobs [source_container_id = {}, source_workspace_id = {}, destination_container_id = {}, destination_workspace_id={}]",
        sourceStorageData.storageContainerResource().getResourceId(),
        sourceStorageData.storageContainerResource().getWorkspaceId(),
        destStorageData.storageContainerResource().getResourceId(),
        destStorageData.storageContainerResource().getWorkspaceId());

    var resultsByStatus = pollResults.collect(groupingBy(PollResponse::getStatus));
    return new BlobCopierResult(resultsByStatus);
  }

  private SyncPoller<BlobCopyInfo, Void> beginCopyBlob(
      AzureStorageAccessService storageAccessService,
      ControlledAzureStorageContainerResource sourceContainer,
      AuthenticatedUserRequest userRequest,
      BlobItem sourceBlobItem,
      BlobContainerClient sourceBlobContainerClient,
      BlobContainerClient destBlobContainerClient) {
    AzureSasBundle sasBundle =
        storageAccessService.createAzureStorageContainerSasToken(
            sourceContainer.getWorkspaceId(),
            sourceContainer,
            userRequest,
            null,
            sourceBlobItem.getName(),
            "r");
    var sourceBlobClient = sourceBlobContainerClient.getBlobClient(sourceBlobItem.getName());
    var destinationBlobClient = destBlobContainerClient.getBlobClient(sourceBlobItem.getName());
    var sourceBlobUrl = sourceBlobClient.getBlobUrl() + "?" + sasBundle.sasToken();

    return destinationBlobClient.beginCopy(new BlobBeginCopyOptions(sourceBlobUrl));
  }
}
