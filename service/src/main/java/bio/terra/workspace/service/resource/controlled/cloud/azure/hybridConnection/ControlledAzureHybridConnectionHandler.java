package bio.terra.workspace.service.resource.controlled.cloud.azure.hybridConnection;

import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import javax.annotation.Nullable;

public class ControlledAzureHybridConnectionHandler implements WsmResourceHandler {
  private static ControlledAzureHybridConnectionHandler theHandler;

  public static ControlledAzureHybridConnectionHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAzureHybridConnectionHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAzureHybridConnectionAttributes attributes =
        DbSerDes.fromJson(
            dbResource.getAttributes(), ControlledAzureHybridConnectionAttributes.class);

    var resource =
        ControlledAzureHybridConnectionResource.builder()
            .hybridConnectionName(attributes.getHybridConnectionName())
            .common(new ControlledResourceFields(dbResource))
            .build();
    return resource;
  }

  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new FeatureNotSupportedException(
        "This generate cloud name feature is not implemented yet");
  }
}
