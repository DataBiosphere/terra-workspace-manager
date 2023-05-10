package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakerNotebook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import bio.terra.workspace.common.BaseAwsUnitTest;
import bio.terra.workspace.service.resource.controlled.cloud.aws.AwsResourceConstants;
import liquibase.util.StringUtil;
import org.junit.jupiter.api.Test;

public class ControlledAwsSagemakerNotebookHandlerTest extends BaseAwsUnitTest {

  @Test
  public void generateCloudName() {
    String workspaceUserFacingId = "workspaceUserFacingId";
    String resourceName = "resource";
    String generatedName = resourceName + '-' + workspaceUserFacingId;

    assertEquals(
        generatedName,
        ControlledAwsSagemakerNotebookHandler.getHandler()
            .generateCloudName(workspaceUserFacingId, resourceName),
        "resource name expected without changes");

    assertEquals(
        resourceName + "-a-b-" + workspaceUserFacingId,
        ControlledAwsSagemakerNotebookHandler.getHandler()
            .generateCloudName("b_" + workspaceUserFacingId, resourceName + "_a"),
        "resource name expected with underscores replaced by dashes");

    assertEquals(
        generatedName,
        ControlledAwsSagemakerNotebookHandler.getHandler()
            .generateCloudName(workspaceUserFacingId + "--", "--" + resourceName),
        "resource name expected with leading & trailing dashes removed");

    assertEquals(
        generatedName,
        ControlledAwsSagemakerNotebookHandler.getHandler()
            .generateCloudName(workspaceUserFacingId, resourceName + ".!(){}^%`<>~#|@*+[]'\"\\"),
        "resource name expected with all non-alphanumeric characters & non-dashes removed");

    assertEquals(
        AwsResourceConstants.MAX_SAGEMAKER_NOTEBOOK_INSTANCE_NAME_LENGTH,
        ControlledAwsSagemakerNotebookHandler.getHandler()
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
        ControlledAwsSagemakerNotebookHandler.getHandler()
            .generateCloudName(
                workspaceUserFacingId + "-----", StringUtil.repeat("a", repeatLength))
            .endsWith("-"),
        "resource name expected not to have trailing dashes");
  }
}