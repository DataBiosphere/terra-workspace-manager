package bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool;

import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public class ControlledAzureBatchPoolHandler implements WsmResourceHandler {
  private static ControlledAzureBatchPoolHandler handler;

  public static ControlledAzureBatchPoolHandler getHandler() {
    if (handler == null) {
      handler = new ControlledAzureBatchPoolHandler();
    }
    return handler;
  }

  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAzureBatchPoolAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAzureBatchPoolAttributes.class);
    return ControlledAzureBatchPoolResource.builder()
        .id(attributes.getId())
        .vmSize(attributes.getVmSize())
        .common(new ControlledResourceFields(dbResource))
        .build();
  }

  @Override
  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new FeatureNotSupportedException("This generate cloud name feature is not implement yet");
  }
}
