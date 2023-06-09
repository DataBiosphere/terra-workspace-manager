package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import static bio.terra.workspace.common.testfixtures.ControlledAwsResourceFixtures.makeAwsSageMakerNotebookResourceBuilder;
import static bio.terra.workspace.common.testfixtures.ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static software.amazon.awssdk.services.sagemaker.model.InstanceType.ML_T2_MEDIUM;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.BaseAwsUnitTest;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.exception.InvalidNameException;
import org.junit.jupiter.api.Test;

public class ControlledAwsSageMakerNotebookResourceTest extends BaseAwsUnitTest {

  @Test
  public void validateResourceTest() {
    // success
    ControlledAwsSageMakerNotebookResource.Builder resourceBuilder =
        makeAwsSageMakerNotebookResourceBuilder("instance");
    assertDoesNotThrow(resourceBuilder::build);

    // invalid accessScope
    resourceBuilder =
        ControlledAwsSageMakerNotebookResource.builder()
            .common(makeDefaultControlledResourceFieldsBuilder().build())
            .instanceName("instanceName")
            .instanceType(ML_T2_MEDIUM.toString());
    Exception ex =
        assertThrows(
            BadRequestException.class,
            resourceBuilder::build,
            "validation fails with non-private accessScope");
    assertThat(ex.getMessage(), containsString("Access scope must be private"));

    // missing instanceName
    resourceBuilder = makeAwsSageMakerNotebookResourceBuilder(null);
    ex =
        assertThrows(
            MissingRequiredFieldException.class,
            resourceBuilder::build,
            "validation fails with empty instanceName");
    assertThat(ex.getMessage(), containsString("instanceName"));

    // missing instanceType
    resourceBuilder =
        ControlledAwsSageMakerNotebookResource.builder()
            .common(
                makeDefaultControlledResourceFieldsBuilder()
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
                makeDefaultControlledResourceFieldsBuilder()
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                    .region(null)
                    .build())
            .instanceName("instanceName")
            .instanceType(ML_T2_MEDIUM.toString());
    ex =
        assertThrows(
            MissingRequiredFieldException.class,
            resourceBuilder::build,
            "validation fails with empty region");
    assertThat(ex.getMessage(), containsString("region"));

    // invalid instanceName
    resourceBuilder = makeAwsSageMakerNotebookResourceBuilder("-instanceName-");
    ex =
        assertThrows(
            InvalidNameException.class,
            resourceBuilder::build,
            "validation fails with invalid sagemaker instance name");
    assertThat(
        ex.getMessage(),
        containsString(
            "SageMaker instance names must contain any sequence alphabets, numbers and dashes"));
  }
}
