package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemaker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.common.BaseAwsUnitTest;
import bio.terra.workspace.service.resource.controlled.cloud.aws.AwsResourceConstants;
import bio.terra.workspace.service.resource.controlled.cloud.aws.s3storageFolder.ControlledAwsS3StorageFolderHandler;
import bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakerNotebook.ControlledAwsSagemakerNotebookHandler;
import liquibase.util.StringUtil;
import org.junit.jupiter.api.Test;

public class ControlledAwsSagemakerNotebookHandlerTest extends BaseAwsUnitTest {

  @Test
  public void generateFolderName() {
    String workspaceUserFacingId = "workspaceUserFacingId";
    String resourceName = "resource";
    String generatedName = "resource-workspaceUserFacingId";

    assertEquals(
        generatedName,
        ControlledAwsSagemakerNotebookHandler.getHandler()
            .generateCloudName(workspaceUserFacingId, resourceName),
        "resource name expected without changes");

    assertEquals(
        generatedName,
        ControlledAwsSagemakerNotebookHandler.getHandler()
            .generateCloudName(workspaceUserFacingId + "--", "--" + resourceName),
        "resource name expected with leading & trailing dashes removed");

    assertEquals(
        generatedName,
        ControlledAwsSagemakerNotebookHandler.getHandler()
            .generateCloudName(workspaceUserFacingId, resourceName + "._(){}^%`<>~#|@*+[]'\"\\"),
            //.generateCloudName(workspaceUserFacingId, resourceName + ".!_(){}^%`<>~#|@*+[]'\"\\"),
        "resource name expected with all non-alphanumeric characters & non-dashes removed");

    resourceName =
        StringUtil.repeat(
            "a", AwsResourceConstants.MAX_SAGEMAKER_NOTEBOOK_INSTANCE_NAME_LENGTH + 1);
    assertEquals(
        AwsResourceConstants.MAX_SAGEMAKER_NOTEBOOK_INSTANCE_NAME_LENGTH,
        ControlledAwsSagemakerNotebookHandler.getHandler()
            .generateCloudName(workspaceUserFacingId, resourceName)
            .length(),
        "resource name expected to be trimmed to max length");
  }
}
