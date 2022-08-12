package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;

public class ControlledAzureDiskHandler implements WsmResourceHandler {
  private static ControlledAzureDiskHandler theHandler;

  public static ControlledAzureDiskHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAzureDiskHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAzureDiskAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAzureDiskAttributes.class);

    var resource =
        ControlledAzureDiskResource.builder()
            .diskName(attributes.getDiskName())
            .region(attributes.getRegion())
            .size(attributes.getSize())
            .common(new ControlledResourceFields(dbResource))
            .build();
    return resource;
  }

  public String generateCloudName(UUID workspaceUuid, String bucketName) {
    return "";
  }
}
