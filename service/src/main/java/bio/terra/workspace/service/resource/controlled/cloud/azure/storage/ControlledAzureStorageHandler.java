package bio.terra.workspace.service.resource.controlled.cloud.azure.storage;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;

public class ControlledAzureStorageHandler implements WsmResourceHandler {
  private static ControlledAzureStorageHandler theHandler;

  public static ControlledAzureStorageHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAzureStorageHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAzureStorageAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAzureStorageAttributes.class);

    return ControlledAzureStorageResource.builder()
        .storageAccountName(attributes.getStorageAccountName())
        .region(attributes.getRegion())
        .common(new ControlledResourceFields(dbResource))
        .build();
  }

  public String generateCloudName(UUID workspaceUuid, String bucketName) {
    return "";
  }
}
