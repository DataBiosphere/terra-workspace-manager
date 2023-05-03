package bio.terra.workspace.service.resource.controlled.cloud.aws;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.resource.controlled.cloud.aws.s3storageFolder.ControlledAwsS3StorageFolderHandler;
import liquibase.util.StringUtil;
import org.junit.jupiter.api.Test;

public class ControlledAwsS3StorageFolderHandlerTest extends BaseUnitTest {

  @Test
  public void generateFolderName() {
    String workspaceUserFacingId = "workspaceUserFacingId";
    String resourceName = "resource";
    String generatedName = "resource-workspaceUserFacingId";

    assertEquals(
        generatedName,
        ControlledAwsS3StorageFolderHandler.getHandler()
            .generateCloudName(workspaceUserFacingId, resourceName),
        "resource name expected without changes");

    assertEquals(
        generatedName,
        ControlledAwsS3StorageFolderHandler.getHandler()
            .generateCloudName(workspaceUserFacingId, "[reso]ur*^ce"),
        "resource name expected with all invalid characters removed");

    resourceName = "re*-our(c)e";
    assertEquals(
        resourceName + "-" + workspaceUserFacingId,
        ControlledAwsS3StorageFolderHandler.getHandler()
            .generateCloudName(workspaceUserFacingId, resourceName),
        "resource name expected without changes with allowed characters");

    resourceName = StringUtil.repeat("a", AwsResourceConstants.MAX_S3_STORAGE_FOLDER_NAME_LENGTH);
    assertEquals(
        AwsResourceConstants.MAX_S3_STORAGE_FOLDER_NAME_LENGTH,
        ControlledAwsS3StorageFolderHandler.getHandler()
            .generateCloudName(workspaceUserFacingId, resourceName)
            .length(),
        "resource name trimmed");
  }
}
