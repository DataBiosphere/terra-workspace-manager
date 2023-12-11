package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.cloud.aws.AwsResourceConstants;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import com.google.common.base.Preconditions;
import jakarta.ws.rs.BadRequestException;
import org.apache.commons.lang3.StringUtils;
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

  /**
   * Generate controlled AWS S3 Storage Folder cloud name that meets the requirements for a valid
   * name.
   *
   * <p>Alphanumeric characters and certain special characters can be safely used in valid names.
   * For details, see https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-keys.html
   */
  @Override
  public String generateCloudName(
      @Nullable String workspaceUserFacingId, String storageFolderName) {
    Preconditions.checkArgument(StringUtils.isNotEmpty(workspaceUserFacingId));
    String generatedName = storageFolderName + "-" + workspaceUserFacingId;

    generatedName = generatedName.replaceAll("[^a-zA-Z0-9\\!\\-\\_\\.\\(\\)]+", "");

    if (generatedName.length() == 0) {
      throw new BadRequestException(
          String.format(
              "Cannot generate a valid s3 storage folder name from %s, it must contain"
                  + " alphanumerical characters.",
              storageFolderName));
    }
    return StringUtils.truncate(
        generatedName, AwsResourceConstants.MAX_S3_STORAGE_FOLDER_NAME_LENGTH);
  }
}
