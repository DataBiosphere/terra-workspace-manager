package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.msi.models.Identity;
import java.util.Optional;
import java.util.UUID;

public class ManagedIdentityHelper {
  private final ResourceDao resourceDao;
  private final CrlService crlService;
  private final AzureConfiguration azureConfiguration;

  public ManagedIdentityHelper(
      ResourceDao resourceDao, CrlService crlService, AzureConfiguration azureConfiguration) {
    this.resourceDao = resourceDao;
    this.crlService = crlService;
    this.azureConfiguration = azureConfiguration;
  }

  public Optional<ControlledAzureManagedIdentityResource> getManagedIdentityResource(
      UUID workspaceId, String managedIdentityName) {
    try {
      return Optional.of(
          resourceDao
              .getResourceByName(workspaceId, managedIdentityName)
              .castByEnum(WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY));
    } catch (ResourceNotFoundException e) {
      // There are some older resources where the resource id was stored before we decided
      // storing the resource name would be better. This is a fallback to support those
      // resources. If the resource is not found by name and the name is an uuid, try by id.
      try {
        return Optional.of(
            resourceDao
                .getResource(workspaceId, UUID.fromString(managedIdentityName))
                .castByEnum(WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY));
      } catch (ResourceNotFoundException | IllegalArgumentException ex) {
        return Optional.empty();
      }
    }
  }

  public Identity getUamiName(AzureCloudContext azureCloudContext, String uamiName) {
    return crlService
        .getMsiManager(azureCloudContext, azureConfiguration)
        .identities()
        .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), uamiName);
  }
}
