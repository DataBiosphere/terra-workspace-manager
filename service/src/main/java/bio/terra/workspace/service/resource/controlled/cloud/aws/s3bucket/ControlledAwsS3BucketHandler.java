package bio.terra.workspace.service.resource.controlled.cloud.aws.s3bucket;

import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import javax.annotation.Nullable;

public class ControlledAwsS3BucketHandler implements WsmResourceHandler {
  private static ControlledAwsS3BucketHandler theHandler;

  public static ControlledAwsS3BucketHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAwsS3BucketHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAwsS3BucketAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAwsS3BucketAttributes.class);

    return ControlledAwsS3BucketResource.builder()
        .common(new ControlledResourceFields(dbResource))
        .s3BucketName(attributes.getS3BucketName())
        .prefix(attributes.getPrefix())
        .build();
  }

  // Naming rules: https://docs.aws.amazon.com/AmazonS3/latest/userguide/bucketnamingrules.html
  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new FeatureNotSupportedException("This generate cloud name feature is not implement yet");
  }
}
