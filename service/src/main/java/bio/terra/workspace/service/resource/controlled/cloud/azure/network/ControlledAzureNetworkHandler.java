package bio.terra.workspace.service.resource.controlled.cloud.azure.network;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;

public class ControlledAzureNetworkHandler implements WsmResourceHandler {
  private static ControlledAzureNetworkHandler theHandler;

  public static ControlledAzureNetworkHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAzureNetworkHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAzureNetworkAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAzureNetworkAttributes.class);

    var resource =
        ControlledAzureNetworkResource.builder()
            .common(new ControlledResourceFields(dbResource))
            .networkName(attributes.getNetworkName())
            .subnetName(attributes.getSubnetName())
            .addressSpaceCidr(attributes.getAddressSpaceCidr())
            .subnetAddressCidr(attributes.getSubnetAddressCidr())
            .region(attributes.getRegion())
            .build();
    return resource;
  }

  public String generateCloudName(UUID workspaceUuid, String resourceName) {
    return "";
  }
}
