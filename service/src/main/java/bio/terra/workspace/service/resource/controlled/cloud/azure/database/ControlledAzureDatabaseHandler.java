package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import javax.annotation.Nullable;

public class ControlledAzureDatabaseHandler implements WsmResourceHandler {
  private static ControlledAzureDatabaseHandler theHandler;

  public static ControlledAzureDatabaseHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAzureDatabaseHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAzureDatabaseAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAzureDatabaseAttributes.class);

    return ControlledAzureDatabaseResource.builder()
        .databaseName(attributes.getDatabaseName())
        .databaseOwner(attributes.getDatabaseOwner())
        .allowAccessForAllWorkspaceUsers(attributes.getAllowAccessForAllWorkspaceUsers())
        .common(new ControlledResourceFields(dbResource))
        .build();
  }

  @Override
  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new FeatureNotSupportedException("Generate cloud name feature is not implemented yet");
  }
}
