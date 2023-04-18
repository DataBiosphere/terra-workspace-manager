package bio.terra.workspace.service.resource.controlled.cloud.aws.storageFolder;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public class ControlledAwsStorageFolderHandler implements WsmResourceHandler {

  private static ControlledAwsStorageFolderHandler theHandler;

  public static ControlledAwsStorageFolderHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAwsStorageFolderHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAwsStorageFolderAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAwsStorageFolderAttributes.class);

    return new ControlledAwsStorageFolderResource(
        dbResource, attributes.getBucketName(), attributes.getPrefix());
  }

  @Override
  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new BadRequestException(
        "generateCloudName not supported for AWS storage folder resources.");
  }
}
