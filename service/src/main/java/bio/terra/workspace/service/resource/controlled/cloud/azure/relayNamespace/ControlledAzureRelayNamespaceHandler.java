package bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace;

import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import javax.annotation.Nullable;

public class ControlledAzureRelayNamespaceHandler implements WsmResourceHandler {
  private static ControlledAzureRelayNamespaceHandler theHandler;

  public static ControlledAzureRelayNamespaceHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAzureRelayNamespaceHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAzureRelayNamespaceAttributes attributes =
        DbSerDes.fromJson(
            dbResource.getAttributes(), ControlledAzureRelayNamespaceAttributes.class);

    var resource =
        ControlledAzureRelayNamespaceResource.builder()
            .namespaceName(attributes.getNamespaceName())
            .common(new ControlledResourceFields(dbResource, attributes.getRegion()))
            .build();
    return resource;
  }

  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new FeatureNotSupportedException("This generate cloud name feature is not implement yet");
  }
}
