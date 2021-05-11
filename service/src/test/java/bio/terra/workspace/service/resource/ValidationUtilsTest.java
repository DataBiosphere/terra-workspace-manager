package bio.terra.workspace.service.resource;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.defaultNotebookCreationParameters;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceContainerImage;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceVmImage;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import org.junit.jupiter.api.Test;

public class ValidationUtilsTest extends BaseUnitTest {
  @Test
  public void aiNotebookInstanceName() {
    ValidationUtils.validateAiNotebookInstanceId("valid-instance-id-0");
    assertThrows(
        InvalidReferenceException.class,
        () -> ValidationUtils.validateAiNotebookInstanceId("1number-first"));
    assertThrows(
        InvalidReferenceException.class,
        () -> ValidationUtils.validateAiNotebookInstanceId("-dash-first"));
    assertThrows(
        InvalidReferenceException.class,
        () -> ValidationUtils.validateAiNotebookInstanceId("dash-last-"));
    assertThrows(
        InvalidReferenceException.class,
        () -> ValidationUtils.validateAiNotebookInstanceId("white space"));
    assertThrows(
        InvalidReferenceException.class,
        () -> ValidationUtils.validateAiNotebookInstanceId("other-symbols^&)"));
    assertThrows(
        InvalidReferenceException.class,
        () -> ValidationUtils.validateAiNotebookInstanceId("unicode-\\u00C6"));
    assertThrows(
        InvalidReferenceException.class,
        () ->
            ValidationUtils.validateAiNotebookInstanceId(
                "more-than-63-chars111111111111111111111111111111111111111111111111111"));
  }

  @Test
  public void notebookCreationParametersExactlyOneOfVmImageOrContainerImage() {
    // Nothing throws on successful validation.
    ValidationUtils.validate(defaultNotebookCreationParameters());

    // Neither vmImage nor containerImage.
    assertThrows(
        InconsistentFieldsException.class,
        () ->
            ValidationUtils.validate(
                defaultNotebookCreationParameters().containerImage(null).vmImage(null)));
    // Both vmImage and containerImage.
    assertThrows(
        InconsistentFieldsException.class,
        () ->
            ValidationUtils.validate(
                defaultNotebookCreationParameters()
                    .containerImage(new ApiGcpAiNotebookInstanceContainerImage())
                    .vmImage(new ApiGcpAiNotebookInstanceVmImage())));
    // Valid containerImage.
    ValidationUtils.validate(
        defaultNotebookCreationParameters()
            .vmImage(null)
            .containerImage(
                new ApiGcpAiNotebookInstanceContainerImage().repository("my-repository")));
    // Valid vmImage.
    ValidationUtils.validate(
        defaultNotebookCreationParameters()
            .vmImage(new ApiGcpAiNotebookInstanceVmImage().imageName("image-name"))
            .containerImage(null));
    ValidationUtils.validate(
        defaultNotebookCreationParameters()
            .vmImage(new ApiGcpAiNotebookInstanceVmImage().imageFamily("image-family"))
            .containerImage(null));
    // Neither vmImage.imageName nor vmImage.familyName
    assertThrows(
        InconsistentFieldsException.class,
        () ->
            ValidationUtils.validate(
                defaultNotebookCreationParameters()
                    .vmImage(new ApiGcpAiNotebookInstanceVmImage())
                    .containerImage(null)));
    // Both vmImage.imageName and vmImage.familyName
    assertThrows(
        InconsistentFieldsException.class,
        () ->
            ValidationUtils.validate(
                defaultNotebookCreationParameters()
                    .vmImage(
                        new ApiGcpAiNotebookInstanceVmImage()
                            .imageName("image-name")
                            .imageFamily("image-family"))
                    .containerImage(null)));
  }
}
