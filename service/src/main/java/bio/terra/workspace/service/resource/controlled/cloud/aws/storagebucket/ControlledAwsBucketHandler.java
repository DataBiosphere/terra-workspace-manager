package bio.terra.workspace.service.resource.controlled.cloud.aws.storagebucket;

import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import javax.annotation.Nullable;

public class ControlledAwsBucketHandler implements WsmResourceHandler {
  private static ControlledAwsBucketHandler theHandler;

  public static ControlledAwsBucketHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAwsBucketHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAwsBucketAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAwsBucketAttributes.class);

    return ControlledAwsBucketResource.builder()
        .s3BucketName(attributes.getS3BucketName())
        .prefix(attributes.getPrefix())
        .common(new ControlledResourceFields(dbResource))
        .build();
  }

  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new FeatureNotSupportedException("This generate cloud name feature is not implement yet");
  }
}
