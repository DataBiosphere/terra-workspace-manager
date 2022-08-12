package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;

public class ControlledAzureVmHandler implements WsmResourceHandler {
  private static ControlledAzureVmHandler theHandler;

  public static ControlledAzureVmHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAzureVmHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAzureVmAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAzureVmAttributes.class);

    var resource =
        ControlledAzureVmResource.builder()
            .vmName(attributes.getVmName())
            .region(attributes.getRegion())
            .vmSize(attributes.getVmSize())
            .vmImage(attributes.getVmImage())
            .ipId(attributes.getIpId())
            .networkId(attributes.getNetworkId())
            .diskId(attributes.getDiskId())
            .common(new ControlledResourceFields(dbResource))
            .build();
    return resource;
  }

  public String generateCloudName(UUID workspaceUuid, String bucketName) {
    return "";
  }
}
