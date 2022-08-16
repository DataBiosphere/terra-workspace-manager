package bio.terra.workspace.service.resource.controlled.cloud.azure.ip;

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

    var resource =
        ControlledAzureIpResource.builder()
            .ipName(attributes.getIpName())
            .region(attributes.getRegion())
            .common(new ControlledResourceFields(dbResource))
            .build();
    return resource;
  }

  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    return "";
  }
}
