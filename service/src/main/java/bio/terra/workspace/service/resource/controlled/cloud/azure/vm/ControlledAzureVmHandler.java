package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import javax.annotation.Nullable;

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

    return ControlledAzureVmResource.builder()
        .vmName(attributes.getVmName())
        .vmSize(attributes.getVmSize())
        .vmImage(attributes.getVmImage())
        .diskId(attributes.getDiskId())
        .common(new ControlledResourceFields(dbResource, attributes.getRegion()))
        .build();
  }

  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new FeatureNotSupportedException("Generate cloud name feature is not implemented yet");
  }
}
