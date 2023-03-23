package bio.terra.workspace.service.resource.controlled.cloud.azure.ip;

import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import javax.annotation.Nullable;

public class ControlledAzureIpHandler implements WsmResourceHandler {
  private static ControlledAzureIpHandler theHandler;

  public static ControlledAzureIpHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAzureIpHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAzureIpAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAzureIpAttributes.class);

    return ControlledAzureIpResource.builder()
        .ipName(attributes.getIpName())
        .common(new ControlledResourceFields(dbResource, attributes.getRegion()))
        .build();
  }

  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new FeatureNotSupportedException("This generate cloud name feature is not implement yet");
  }
}
