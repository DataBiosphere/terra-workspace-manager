package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import javax.annotation.Nullable;

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

    return ControlledAzureDiskResource.builder()
        .diskName(attributes.getDiskName())
        .size(attributes.getSize())
        .common(new ControlledResourceFields(dbResource, attributes.getRegion()))
        .build();
  }

  @Override
  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new FeatureNotSupportedException("Generate cloud name feature is not implemented yet");
  }
}
