package bio.terra.workspace.service.resource.controlled.cloud.azure;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.http.HttpClient;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.StorageAccount;
import com.azure.resourcemanager.storage.models.StorageAccountKey;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AzureControlledStorageResourceService {

  public record AzureSasBundle(String sasToken, String sasUrl) {}

  private final SamService samService;
  private final AzureCloudContextService azureCloudContextService;
  private final CrlService crlService;
  private final ControlledResourceService controlledResourceService;
  private final AzureConfiguration azureConfiguration;
  private final FeatureConfiguration features;

  @Autowired
  public AzureControlledStorageResourceService(
      SamService samService,
      AzureCloudContextService azureCloudContextService,
      CrlService crlService,
      ControlledResourceService controlledResourceService,
      AzureConfiguration azureConfiguration,
      FeatureConfiguration features) {
    this.samService = samService;
    this.azureCloudContextService = azureCloudContextService;
    this.crlService = crlService;
    this.controlledResourceService = controlledResourceService;
    this.azureConfiguration = azureConfiguration;
    this.features = features;
  }

  private BlobContainerSasPermission getSasTokenPermissions(
      AuthenticatedUserRequest userRequest, UUID storageContainerUuid) {
    final List<String> containerActions =
        SamRethrow.onInterrupted(
            () ->
                samService.listResourceActions(
                    userRequest,
                    SamConstants.SamResource.CONTROLLED_USER_SHARED,
                    storageContainerUuid.toString()),
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

  private StorageSharedKeyCredential getStorageAccountKey(
      UUID workspaceUuid, String storageAccountName) {
    AzureCloudContext azureCloudContext =
        azureCloudContextService.getRequiredAzureCloudContext(workspaceUuid);
    StorageManager storageManager =
        crlService.getStorageManager(azureCloudContext, azureConfiguration);
    StorageAccount storageAccount =
        storageManager
            .storageAccounts()
            .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), storageAccountName);

    StorageAccountKey key = storageAccount.getKeys().get(0);
    return new StorageSharedKeyCredential(storageAccountName, key.value());
  }

  public AzureSasBundle createAzureStorageContainerSasToken(
      UUID workspaceUuid,
      ControlledAzureStorageContainerResource storageContainerResource,
      ControlledAzureStorageResource storageAccountResource,
      OffsetDateTime startTime,
      OffsetDateTime expiryTime,
      AuthenticatedUserRequest userRequest,
      String sasIPRange) {
    features.azureEnabledCheck();

    BlobContainerSasPermission blobContainerSasPermission =
        getSasTokenPermissions(userRequest, storageContainerResource.getResourceId());

    String storageAccountName = storageAccountResource.getStorageAccountName();
    String endpoint =
        String.format(Locale.ROOT, "https://%s.blob.core.windows.net", storageAccountName);
    StorageSharedKeyCredential storageKey = getStorageAccountKey(workspaceUuid, storageAccountName);

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

    final var token = blobContainerClient.generateSas(sasValues);

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
