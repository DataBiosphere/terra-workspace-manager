package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import bio.terra.workspace.common.BaseAwsSpringBootUnitTest;
import bio.terra.workspace.service.resource.controlled.cloud.aws.AwsResourceConstants;
import liquibase.util.StringUtil;
import org.junit.jupiter.api.Test;

public class ControlledAwsSageMakerNotebookHandlerTest extends BaseAwsSpringBootUnitTest {

  @Test
  void generateFolderNameTest() {
    String workspaceUserFacingId = "workspaceUserFacingId";
    String resourceName = "resource";
    String generatedName = resourceName + '-' + workspaceUserFacingId;

    assertEquals(
        generatedName,
        ControlledAwsSageMakerNotebookHandler.getHandler()
            .generateCloudName(workspaceUserFacingId, resourceName),
        "resource name expected without changes");

    assertEquals(
        resourceName + "-a-b-" + workspaceUserFacingId,
        ControlledAwsSageMakerNotebookHandler.getHandler()
            .generateCloudName("b_" + workspaceUserFacingId, resourceName + "_a"),
        "resource name expected with underscores replaced by dashes");

    assertEquals(
        generatedName,
        ControlledAwsSageMakerNotebookHandler.getHandler()
            .generateCloudName(workspaceUserFacingId + "--", "--" + resourceName),
        "resource name expected with leading & trailing dashes removed");

    assertEquals(
        generatedName,
        ControlledAwsSageMakerNotebookHandler.getHandler()
            .generateCloudName(workspaceUserFacingId, resourceName + ".!(){}^%`<>~#|@*+[]'\"\\"),
        "resource name expected with all non-alphanumeric characters & non-dashes removed");

    assertEquals(
        AwsResourceConstants.MAX_SAGEMAKER_NOTEBOOK_INSTANCE_NAME_LENGTH,
        ControlledAwsSageMakerNotebookHandler.getHandler()
            .generateCloudName(
                workspaceUserFacingId,
                StringUtil.repeat(
                    "a", AwsResourceConstants.MAX_SAGEMAKER_NOTEBOOK_INSTANCE_NAME_LENGTH + 1))
            .length(),
        "resource name expected to be trimmed to max length");

    int repeatLength =
        AwsResourceConstants.MAX_SAGEMAKER_NOTEBOOK_INSTANCE_NAME_LENGTH
            - workspaceUserFacingId.length()
            - 2;
    assertFalse(
        ControlledAwsSageMakerNotebookHandler.getHandler()
            .generateCloudName(
                workspaceUserFacingId + "-----", StringUtil.repeat("a", repeatLength))
            .endsWith("-"),
        "resource name expected not to have trailing dashes");
  }
}
