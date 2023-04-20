package bio.terra.workspace.service.resource.controlled.cloud.aws.s3storageFolder;

import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public class ControlledAwsS3StorageFolderHandler implements WsmResourceHandler {

  private static ControlledAwsS3StorageFolderHandler theHandler;

  public static ControlledAwsS3StorageFolderHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAwsS3StorageFolderHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAwsS3StorageFolderAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAwsS3StorageFolderAttributes.class);

    return new ControlledAwsS3StorageFolderResource(
        dbResource, attributes.getBucketName(), attributes.getPrefix());
  }

  @Override
  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    // TODO(TERRA-504): Implement generateCloudName
    throw new FeatureNotSupportedException("Generate cloud name feature is not implemented yet");
  }
}