package bio.terra.workspace.service.resource.controlled.cloud.aws.s3storageFolder;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import com.google.common.base.CharMatcher;
import java.util.UUID;
import javax.ws.rs.BadRequestException;
import org.jetbrains.annotations.Nullable;

public class ControlledAwsS3StorageFolderHandler implements WsmResourceHandler {

  private static final int MAX_FOLDER_NAME_LENGTH = 1024;
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
   * Generate controlled AWS S3 Storage folder cloud name that meets the requirements for a valid
   * name.
   *
   * <p>Alphanumeric characters and certain special characters can be safely used in valid names For
   * details, see https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-keys.html
   */
  @Override
  public String generateCloudName(@Nullable UUID workspaceUuid, String storageFolderName) {
    String generatedName =
        storageFolderName.length() > MAX_FOLDER_NAME_LENGTH
            ? storageFolderName.substring(0, MAX_FOLDER_NAME_LENGTH)
            : storageFolderName;

    // The regular expression only allow legal character combinations containing
    // alphanumeric characters and one or more of "!-_.*'()". It trims any other combinations.
    generatedName =
        CharMatcher.inRange('0', '9')
            .or(CharMatcher.inRange('A', 'z'))
            .or(CharMatcher.anyOf("!-_.*'()"))
            .retainFrom(generatedName);

    if (generatedName.length() == 0) {
      throw new BadRequestException(
          String.format(
              "Cannot generate a valid s3 storage folder name from %s, it must contain"
                  + " alphanumerical characters.",
              storageFolderName));
    }
    return generatedName;
  }
}
