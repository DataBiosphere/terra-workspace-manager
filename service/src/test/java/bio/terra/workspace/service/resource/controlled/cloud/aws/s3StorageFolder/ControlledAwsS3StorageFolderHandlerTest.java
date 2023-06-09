package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.common.BaseAwsUnitTest;
import bio.terra.workspace.service.resource.controlled.cloud.aws.AwsResourceConstants;
import liquibase.util.StringUtil;
import org.junit.jupiter.api.Test;

public class ControlledAwsS3StorageFolderHandlerTest extends BaseAwsUnitTest {

  @Test
  public void generateFolderNameTest() {
    String workspaceUserFacingId = "workspaceUserFacingId";
    String resourceName = "resource";
    String generatedName = resourceName + '-' + workspaceUserFacingId;

    assertEquals(
        generatedName,
        ControlledAwsS3StorageFolderHandler.getHandler()
            .generateCloudName(workspaceUserFacingId, resourceName),
        "resource name expected without changes");

    assertEquals(
        generatedName,
        ControlledAwsS3StorageFolderHandler.getHandler()
            .generateCloudName(workspaceUserFacingId, resourceName + "{}^%`<>~#|@*+[]'\"\\"),
        "resource name expected with all invalid characters removed");

    assertEquals(
        ".-!_()" + generatedName,
        ControlledAwsS3StorageFolderHandler.getHandler()
            .generateCloudName(workspaceUserFacingId, ".-!_()" + resourceName),
        "resource name expected without changes with allowed characters");

    assertEquals(
        AwsResourceConstants.MAX_S3_STORAGE_FOLDER_NAME_LENGTH,
        ControlledAwsS3StorageFolderHandler.getHandler()
            .generateCloudName(
                workspaceUserFacingId,
                StringUtil.repeat("a", AwsResourceConstants.MAX_S3_STORAGE_FOLDER_NAME_LENGTH + 1))
            .length(),
        "resource name expected to be trimmed to max length");
  }
}
