package bio.terra.workspace.service.resource.controlled.cloud.azure.storage;

import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import javax.annotation.Nullable;

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
        .common(new ControlledResourceFields(dbResource, attributes.getRegion()))
        .build();
  }

  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new FeatureNotSupportedException("This generate cloud name feature is not implement yet");
  }
}
