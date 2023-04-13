package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

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
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAwsS3StorageFolderAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAwsS3StorageFolderAttributes.class);

    return new ControlledAwsS3StorageFolderResource(
        dbResource, attributes.getS3BucketName(), attributes.getPrefix());
  }

  @Override
  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new FeatureNotSupportedException("Generate cloud name feature is not implemented yet");
  }
}
