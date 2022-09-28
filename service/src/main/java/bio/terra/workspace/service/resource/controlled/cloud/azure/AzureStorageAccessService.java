package bio.terra.workspace.service.resource.controlled.cloud.azure;

import bio.terra.common.exception.ForbiddenException;
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
import java.util.Optional;
import java.util.UUID;
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

  public record AzureSasBundle(String sasToken, String sasUrl) {}

  private final SamService samService;
  private final FeatureConfiguration features;
  private final StorageAccountKeyProvider storageAccountKeyProvider;

  @Autowired
  public AzureStorageAccessService(
      SamService samService,
      StorageAccountKeyProvider storageAccountKeyProvider,
      FeatureConfiguration features) {
    this.samService = samService;
    this.features = features;
    this.storageAccountKeyProvider = storageAccountKeyProvider;
  }

  private BlobContainerSasPermission getSasTokenPermissions(
      AuthenticatedUserRequest userRequest, UUID storageContainerUuid, String samResourceName) {
    final List<String> containerActions =
        SamRethrow.onInterrupted(
            () ->
                samService.listResourceActions(
                    userRequest, samResourceName, storageContainerUuid.toString()),
            "listResourceActions");

    String tokenPermissions = "";
    for (String action : containerActions) {
      if (action.equals(SamConstants.SamControlledResourceActions.READ_ACTION)) {
        tokenPermissions += "rl";
      } else if (action.equals(SamConstants.SamControlledResourceActions.WRITE_ACTION)) {
        tokenPermissions += "acwd";
      }
    }

    if (tokenPermissions.isEmpty()) {
      throw new ForbiddenException(
          String.format(
              "User is not authorized to get a SAS token for container %s",
              storageContainerUuid.toString()));
    }

    return BlobContainerSasPermission.parse(tokenPermissions);
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
   * @param sasIPRange (optional) IP address or range of IPs from which the Azure APIs will accept
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
      String sasIPRange,
      Optional<String> blobPrefix) {
    features.azureEnabledCheck();

    BlobContainerSasPermission blobContainerSasPermission =
        getSasTokenPermissions(
            userRequest,
            storageContainerResource.getResourceId(),
            storageContainerResource.getCategory().getSamResourceName());

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
    if (sasIPRange != null) {
      sasValues.setSasIpRange(SasIpRange.parse(sasIPRange));
    }

    String token;
    if (blobPrefix.isPresent()) {
      var blobClient = blobContainerClient.getBlobClient(blobPrefix.get());
      token = blobClient.generateSas(sasValues);
    } else {
      token = blobContainerClient.generateSas(sasValues);
    }

    return new AzureSasBundle(
        token,
        String.format(
            Locale.ROOT,
            "https://%s.blob.core.windows.net/%s?%s",
            storageAccountName,
            storageContainerResource.getStorageContainerName(),
            token));
  }
}
