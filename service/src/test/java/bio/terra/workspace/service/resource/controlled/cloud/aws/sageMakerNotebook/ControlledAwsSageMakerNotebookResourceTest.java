package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.SAGEMAKER_INSTANCE_TYPE;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.WORKSPACE_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.BaseAwsSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.exception.InvalidNameException;
import org.junit.jupiter.api.Test;

public class ControlledAwsSageMakerNotebookResourceTest extends BaseAwsSpringBootUnitTest {

  @Test
  void validateResourceTest() {
    // success
    ControlledAwsSageMakerNotebookResource.Builder resourceBuilder =
        ControlledAwsResourceFixtures.makeAwsSageMakerNotebookResourceBuilder(
            WORKSPACE_ID, "resource", "instance", DEFAULT_USER_EMAIL);
    assertDoesNotThrow(resourceBuilder::build);

    // invalid accessScope
    resourceBuilder =
        ControlledAwsSageMakerNotebookResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
                    .build())
            .instanceName("instanceName")
            .instanceType(SAGEMAKER_INSTANCE_TYPE);
    Exception ex =
        assertThrows(
            BadRequestException.class,
            resourceBuilder::build,
            "validation fails with non-private accessScope");
    assertThat(ex.getMessage(), containsString("Access scope must be private"));

    // missing instanceName
    ex =
        assertThrows(
            MissingRequiredFieldException.class,
            () ->
                ControlledAwsResourceFixtures.makeAwsSageMakerNotebookResourceBuilder(
                        WORKSPACE_ID, "resource", null, DEFAULT_USER_EMAIL)
                    .build(),
            "validation fails with empty instanceName");
    assertThat(ex.getMessage(), containsString("instanceName"));

    // missing instanceType
    resourceBuilder =
        ControlledAwsSageMakerNotebookResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                    .build())
            .instanceName("instanceName");
    ex =
        assertThrows(
            MissingRequiredFieldException.class,
            resourceBuilder::build,
            "validation fails with empty instanceType");
    assertThat(ex.getMessage(), containsString("instanceType"));

    // missing region
    resourceBuilder =
        ControlledAwsSageMakerNotebookResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                    .region(null)
                    .build())
            .instanceName("instanceName")
            .instanceType(SAGEMAKER_INSTANCE_TYPE);
    ex =
        assertThrows(
            MissingRequiredFieldException.class,
            resourceBuilder::build,
            "validation fails with empty region");
    assertThat(ex.getMessage(), containsString("region"));

    // invalid instanceName
    ex =
        assertThrows(
            InvalidNameException.class,
            () ->
                ControlledAwsResourceFixtures.makeAwsSageMakerNotebookResourceBuilder(
                        WORKSPACE_ID, "resource", "-instanceName-", DEFAULT_USER_EMAIL)
                    .build(),
            "validation fails with invalid sagemaker instance name");
    assertThat(
        ex.getMessage(),
        containsString(
            "SageMaker instance names must contain any sequence alphabets, numbers and dashes"));
  }
}
