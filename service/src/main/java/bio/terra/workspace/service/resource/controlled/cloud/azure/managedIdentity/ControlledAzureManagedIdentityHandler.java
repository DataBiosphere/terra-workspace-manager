package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import javax.annotation.Nullable;

public class ControlledAzureManagedIdentityHandler implements WsmResourceHandler {
  private static ControlledAzureManagedIdentityHandler theHandler;

  public static ControlledAzureManagedIdentityHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAzureManagedIdentityHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAzureManagedIdentityAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAzureManagedIdentityAttributes.class);

    return ControlledAzureManagedIdentityResource.builder()
        .managedIdentityName(attributes.getManagedIdentityName())
        .common(new ControlledResourceFields(dbResource, attributes.getRegion()))
        .build();
  }

  @Override
  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new FeatureNotSupportedException("Generate cloud name feature is not implemented yet");
  }
}
