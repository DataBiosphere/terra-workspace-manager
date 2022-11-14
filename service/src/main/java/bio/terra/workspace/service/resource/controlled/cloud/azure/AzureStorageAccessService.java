package bio.terra.workspace.service.resource.controlled.cloud.azure;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.StorageAccountKeyProvider;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import com.azure.core.http.HttpClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.sas.BlobContainerSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.common.sas.SasIpRange;
import com.azure.storage.common.sas.SasProtocol;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Service for Azure storage access management.
 *
 * <p>Rather than providing direct access to Azure storage containers, storage access is
 * accomplished via the minting of shared access signatures (SAS).
 */
@Component
public class AzureStorageAccessService {
  private static final Logger logger = LoggerFactory.getLogger(AzureStorageAccessService.class);

  private final SamService samService;
  private final FeatureConfiguration features;
  private final StorageAccountKeyProvider storageAccountKeyProvider;
  private final AzureConfiguration azureConfiguration;

  @Autowired
  public AzureStorageAccessService(
      SamService samService,
      StorageAccountKeyProvider storageAccountKeyProvider,
      FeatureConfiguration features,
      AzureConfiguration azureConfiguration) {
    this.samService = samService;
    this.features = features;
    this.storageAccountKeyProvider = storageAccountKeyProvider;
    this.azureConfiguration = azureConfiguration;
  }

  private BlobContainerSasPermission getSasTokenPermissions(
      AuthenticatedUserRequest userRequest,
      UUID storageContainerUuid,
      String samResourceName,
      String desiredPermissions) {
    final List<String> containerActions =
        SamRethrow.onInterrupted(
            () ->
                samService.listResourceActions(
                    userRequest, samResourceName, storageContainerUuid.toString()),
            "listResourceActions");

    String possiblePermissions = "";
    for (String action : containerActions) {
      if (action.equals(SamConstants.SamControlledResourceActions.READ_ACTION)) {
        possiblePermissions += "rl";
      } else if (action.equals(SamConstants.SamControlledResourceActions.WRITE_ACTION)) {
        possiblePermissions += "acwd";
      }
    }

    if (possiblePermissions.isEmpty()) {
      throw new ForbiddenException(
          String.format(
              "User is not authorized to get a SAS token for container %s",
              storageContainerUuid.toString()));
    }

    // ensure the requested permissions, if present, are a subset of the max possible permissions
    String effectivePermissions;
    if (desiredPermissions != null) {
      var possiblePermissionsSet =
          SasPermissionsHelper.permissionStringToCharSet(possiblePermissions);
      var desiredPermissionsSet =
          SasPermissionsHelper.permissionStringToCharSet(desiredPermissions);

      if (!possiblePermissionsSet.containsAll(desiredPermissionsSet)) {
        throw new ForbiddenException("Not authorized");
      }
      effectivePermissions = desiredPermissions;
    } else {
      effectivePermissions = possiblePermissions;
    }

    return BlobContainerSasPermission.parse(effectivePermissions);
  }

  /**
   * Creates a SAS token with a default start and expiry (drawn from app-level config)
   *
   * @param workspaceUuid id of the workspace that owns the container resource
   * @param storageContainerResource storage container object we want to mint a SAS for
   * @param storageAccountResource storage account which owns the storage container object
   * @param userRequest The authenticated user's request
   * @param sasIpRange (optional) IP address or range of IPs from which the Azure APIs will accept
   *     requests for the token being minted.
   * @return A bundle of 1) a full Azure SAS URL, including the storage account hostname and 2) the
   *     token query param fragment
   */
  public AzureSasBundle createAzureStorageContainerSasToken(
      UUID workspaceUuid,
      ControlledAzureStorageContainerResource storageContainerResource,
      ControlledAzureStorageResource storageAccountResource,
      AuthenticatedUserRequest userRequest,
      String sasIpRange,
      String sasBlobName,
      String sasPermissions) {

    OffsetDateTime startTime =
        OffsetDateTime.now().minusMinutes(azureConfiguration.getSasTokenStartTimeMinutesOffset());
    long secondDuration = azureConfiguration.getSasTokenExpiryTimeMinutesOffset() * 60;
    OffsetDateTime expiryTime = OffsetDateTime.now().plusSeconds(secondDuration);

    return createAzureStorageContainerSasToken(
        workspaceUuid,
        storageContainerResource,
        storageAccountResource,
        startTime,
        expiryTime,
        userRequest,
        sasIpRange,
        sasBlobName,
        sasPermissions);
  }

  /**
   * Mints a new SAS for an Azure storage container. Access control is mediated by SAM and mapped to
   * Azure permissions.
   *
   * @param workspaceUuid id of the workspace that owns the container resource
   * @param storageContainerResource storage container object we want to mint a SAS for
   * @param storageAccountResource storage account which owns the storage container object
   * @param startTime Time at which the SAS will become functional
   * @param expiryTime Time at which the SAS will expire
   * @param userRequest The authenticated user's request
   * @param sasIpRange (optional) IP address or range of IPs from which the Azure APIs will accept
   *     requests for the token being minted.
   * @return A bundle of 1) a full Azure SAS URL, including the storage account hostname and 2) the
   *     token query param fragment
   */
  public AzureSasBundle createAzureStorageContainerSasToken(
      UUID workspaceUuid,
      ControlledAzureStorageContainerResource storageContainerResource,
      ControlledAzureStorageResource storageAccountResource,
      OffsetDateTime startTime,
      OffsetDateTime expiryTime,
      AuthenticatedUserRequest userRequest,
      String sasIpRange,
      String sasBlobName,
      String sasPermissions) {
    features.azureEnabledCheck();

    logger.info(
        "user {} requesting SAS token for Azure storage container {} in workspace {}",
        userRequest.getEmail(),
        storageContainerResource.toString(),
        workspaceUuid.toString());

    BlobContainerSasPermission blobContainerSasPermission =
        getSasTokenPermissions(
            userRequest,
            storageContainerResource.getResourceId(),
            storageContainerResource.getCategory().getSamResourceName(),
            sasPermissions);

    String storageAccountName = storageAccountResource.getStorageAccountName();
    String endpoint = storageAccountResource.getStorageAccountEndpoint();
    StorageSharedKeyCredential storageKey =
        storageAccountKeyProvider.getStorageAccountKey(workspaceUuid, storageAccountName);

    BlobContainerClient blobContainerClient =
        new BlobContainerClientBuilder()
            .credential(storageKey)
            .endpoint(endpoint)
            .httpClient(HttpClient.createDefault())
            .containerName(storageContainerResource.getStorageContainerName())
            .buildClient();
    BlobServiceSasSignatureValues sasValues =
        new BlobServiceSasSignatureValues(expiryTime, blobContainerSasPermission)
            .setStartTime(startTime)
            .setProtocol(SasProtocol.HTTPS_ONLY);

    if (sasIpRange != null) {
      sasValues.setSasIpRange(SasIpRange.parse(sasIpRange));
    }

    String token;
    if (sasBlobName != null) {
      var blobClient = blobContainerClient.getBlobClient(sasBlobName);
      token = blobClient.generateSas(sasValues);
    } else {
      token = blobContainerClient.generateSas(sasValues);
    }

    logger.info(
        "SAS token with expiry time of {} generated for user {} on container {} in workspace {}",
        expiryTime,
        userRequest.getEmail(),
        storageContainerResource.getResourceId(),
        workspaceUuid);

    return new AzureSasBundle(
        token,
        String.format(
            Locale.ROOT,
            "https://%s.blob.core.windows.net/%s?%s",
            storageAccountName,
            storageContainerResource.getStorageContainerName(),
            token));
  }

  /**
   * Returns an Azure container client suitable for interacting with a storage container and its
   * constiuent blobs.
   *
   * @param containerResource The WSM container resource the client will operate on
   * @param storageAccountResource The parent storage account for the WSM container resource
   * @return An Azure blob container client
   */
  public BlobContainerClient buildBlobContainerClient(
      ControlledAzureStorageContainerResource containerResource,
      ControlledAzureStorageResource storageAccountResource) {
    StorageSharedKeyCredential storageAccountKey =
        storageAccountKeyProvider.getStorageAccountKey(
            containerResource.getWorkspaceId(), storageAccountResource.getStorageAccountName());

    return new BlobContainerClientBuilder()
        .credential(storageAccountKey)
        .endpoint(storageAccountResource.getStorageAccountEndpoint())
        .httpClient(HttpClient.createDefault())
        .containerName(containerResource.getStorageContainerName())
        .buildClient();
  }
}
