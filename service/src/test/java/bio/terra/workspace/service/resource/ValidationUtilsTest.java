package bio.terra.workspace.service.resource;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.defaultNotebookCreationParameters;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.workspace.app.configuration.external.GitRepoReferencedResourceConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceContainerImage;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceVmImage;
import bio.terra.workspace.service.resource.exception.InvalidNameException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ValidationUtilsTest extends BaseUnitTest {

  private static final String MAX_VALID_STRING =
      "012345678901234567890123456789012345678901234567890123456789012";
  private static final String INVALID_STRING = MAX_VALID_STRING + "b";
  private static final String MAX_VALID_STRING_WITH_DOTS =
      MAX_VALID_STRING
          + "."
          + MAX_VALID_STRING
          + "."
          + MAX_VALID_STRING
          + "."
          + "012345678901234567890123456789";

  ResourceValidationUtils validationUtils;
  @Autowired GitRepoReferencedResourceConfiguration gitRepoReferencedResourceConfiguration;

  @BeforeEach
  public void setup() {
    gitRepoReferencedResourceConfiguration.setAllowListedGitRepoHostNames(
        List.of("github.com", "gitlab.com"));
    validationUtils = new ResourceValidationUtils(gitRepoReferencedResourceConfiguration);
  }

  @Test
  public void aiNotebookInstanceName() {
    ResourceValidationUtils.validateAiNotebookInstanceId("valid-instance-id-0");
    assertThrows(
        InvalidReferenceException.class,
        () -> ResourceValidationUtils.validateAiNotebookInstanceId("1number-first"));
    assertThrows(
        InvalidReferenceException.class,
        () -> ResourceValidationUtils.validateAiNotebookInstanceId("-dash-first"));
    assertThrows(
        InvalidReferenceException.class,
        () -> ResourceValidationUtils.validateAiNotebookInstanceId("dash-last-"));
    assertThrows(
        InvalidReferenceException.class,
        () -> ResourceValidationUtils.validateAiNotebookInstanceId("white space"));
    assertThrows(
        InvalidReferenceException.class,
        () -> ResourceValidationUtils.validateAiNotebookInstanceId("other-symbols^&)"));
    assertThrows(
        InvalidReferenceException.class,
        () -> ResourceValidationUtils.validateAiNotebookInstanceId("unicode-\\u00C6"));
    assertThrows(
        InvalidReferenceException.class,
        () ->
            ResourceValidationUtils.validateAiNotebookInstanceId(
                "more-than-63-chars111111111111111111111111111111111111111111111111111"));
  }

  @Test
  public void notebookCreationParametersExactlyOneOfVmImageOrContainerImage() {
    // Nothing throws on successful validation.
    ResourceValidationUtils.validate(defaultNotebookCreationParameters());

    // Neither vmImage nor containerImage.
    assertThrows(
        InconsistentFieldsException.class,
        () ->
            ResourceValidationUtils.validate(
                defaultNotebookCreationParameters().containerImage(null).vmImage(null)));
    // Both vmImage and containerImage.
    assertThrows(
        InconsistentFieldsException.class,
        () ->
            ResourceValidationUtils.validate(
                defaultNotebookCreationParameters()
                    .containerImage(new ApiGcpAiNotebookInstanceContainerImage())
                    .vmImage(new ApiGcpAiNotebookInstanceVmImage())));
    // Valid containerImage.
    ResourceValidationUtils.validate(
        defaultNotebookCreationParameters()
            .vmImage(null)
            .containerImage(
                new ApiGcpAiNotebookInstanceContainerImage().repository("my-repository")));
    // Valid vmImage.
    ResourceValidationUtils.validate(
        defaultNotebookCreationParameters()
            .vmImage(new ApiGcpAiNotebookInstanceVmImage().imageName("image-name"))
            .containerImage(null));
    ResourceValidationUtils.validate(
        defaultNotebookCreationParameters()
            .vmImage(new ApiGcpAiNotebookInstanceVmImage().imageFamily("image-family"))
            .containerImage(null));
    // Neither vmImage.imageName nor vmImage.familyName
    assertThrows(
        InconsistentFieldsException.class,
        () ->
            ResourceValidationUtils.validate(
                defaultNotebookCreationParameters()
                    .vmImage(new ApiGcpAiNotebookInstanceVmImage())
                    .containerImage(null)));
    // Both vmImage.imageName and vmImage.familyName
    assertThrows(
        InconsistentFieldsException.class,
        () ->
            ResourceValidationUtils.validate(
                defaultNotebookCreationParameters()
                    .vmImage(
                        new ApiGcpAiNotebookInstanceVmImage()
                            .imageName("image-name")
                            .imageFamily("image-family"))
                    .containerImage(null)));
  }

  @Test
  public void validateReferencedBucketName_nameHas64Character_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () -> ResourceValidationUtils.validateReferencedBucketName(INVALID_STRING));
  }

  @Test
  public void validateReferencedBucketName_nameHas63Character_OK() {
    ResourceValidationUtils.validateReferencedBucketName(MAX_VALID_STRING);
  }

  @Test
  public void validateReferencedBucketName_nameHas2Character_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () -> ResourceValidationUtils.validateReferencedBucketName("aa"));
  }

  @Test
  public void validateReferencedBucketName_nameHas3Character_OK() {
    ResourceValidationUtils.validateReferencedBucketName("123");
  }

  @Test
  public void validateReferencedBucketName_nameHas222CharacterWithDotSeparator_OK() {
    ResourceValidationUtils.validateReferencedBucketName(MAX_VALID_STRING_WITH_DOTS);
  }

  @Test
  public void
      validateReferencedBucketName_nameWithDotSeparatorButOneSubstringExceedsLimit_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () ->
            ResourceValidationUtils.validateReferencedBucketName(
                INVALID_STRING + "." + MAX_VALID_STRING));
  }

  @Test
  public void validateReferencedBucketName_nameStartAndEndWithNumber_OK() {
    ResourceValidationUtils.validateReferencedBucketName("1-bucket-1");
  }

  @Test
  public void validateReferencedBucketName_nameStartAndEndWithDot_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () -> ResourceValidationUtils.validateReferencedBucketName(".bucket-name."));
  }

  @Test
  public void validateReferencedBucketName_nameWithGoogPrefix_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () -> ResourceValidationUtils.validateReferencedBucketName("goog-bucket-name1"));
  }

  @Test
  public void validateReferencedBucketName_nameContainsGoogle_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () -> ResourceValidationUtils.validateReferencedBucketName("bucket-google-name"));
  }

  @Test
  public void validateReferencedBucketName_nameContainsG00gle_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () -> ResourceValidationUtils.validateReferencedBucketName("bucket-g00gle-name"));
  }

  @Test
  public void validateReferencedBucketName_nameContainsUnderscore_OK() {
    ResourceValidationUtils.validateReferencedBucketName("bucket_name");
  }

  @Test
  public void validateControlledBucketName_nameContainsUnderscore_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () -> ResourceValidationUtils.validateControlledBucketName("bucket_name"));
  }

  @Test
  public void validateResourceDescription_nameTooLong_throwsException() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 2050; i++) {
      sb.append("a");
    }
    assertThrows(
        InvalidNameException.class,
        () -> ResourceValidationUtils.validateResourceDescriptionName(sb.toString()));
  }

  @Test
  public void validateResourceDescription_nameWith2048Char_validates() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 2048; i++) {
      sb.append("a");
    }
    ResourceValidationUtils.validateResourceDescriptionName(sb.toString());
  }

  @Test
  public void validateBucketFileName_disallowName_throwException() {
    assertThrows(
        InvalidNameException.class, () -> ResourceValidationUtils.validateGcsObjectName("."));
  }

  @Test
  public void validateBucketFileName_emptyOrNullString_throwsException() {
    assertThrows(
        InvalidNameException.class, () -> ResourceValidationUtils.validateGcsObjectName(""));
  }

  @Test
  public void validateBucketFileName_startsWithAcmeChallengePrefix_throwsException() {
    String file = ".well-known/acme-challenge/hello.txt";

    assertThrows(
        InvalidNameException.class, () -> ResourceValidationUtils.validateGcsObjectName(file));
  }

  @Test
  public void validateBucketFileName_fileNameTooLong_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () ->
            ResourceValidationUtils.validateGcsObjectName(
                RandomStringUtils.random(1025, /*letters=*/ true, /*numbers=*/ true)));
  }

  @Test
  public void validateBucketFileName_legalFileName_validate() {
    ResourceValidationUtils.validateGcsObjectName("hello.txt");
    ResourceValidationUtils.validateGcsObjectName(
        RandomStringUtils.random(1024, /*letters=*/ true, /*numbers=*/ true));
    ResourceValidationUtils.validateGcsObjectName("你好.png");
  }

  @Test
  public void validateBqDataTableName() {
    ResourceValidationUtils.validateBqDataTableName("00_お客様");
    ResourceValidationUtils.validateBqDataTableName("table 01");
    ResourceValidationUtils.validateBqDataTableName("ग्राहक");
    ResourceValidationUtils.validateBqDataTableName("étudiant-01");
  }

  @Test
  public void validateBqDataTableName_invalidName_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () -> ResourceValidationUtils.validateBqDataTableName("00_お客様*"));
    assertThrows(
        InvalidNameException.class, () -> ResourceValidationUtils.validateBqDataTableName(""));
  }

  @Test
  public void validateGitRepoUrl() {
    validationUtils.validateGitRepoUri("https://github.com/path/to/project.git");
    validationUtils.validateGitRepoUri("https://github.com/yuhuyoyo/testrepo.git");
    validationUtils.validateGitRepoUri("git@github.com:DataBiosphere/terra-workspace-manager.git");
    validationUtils.validateGitRepoUri("ssh://git@github.com/path/to/project.git");
    validationUtils.validateGitRepoUri(
        "https://username:password@github.com/username/repository.git");
  }

  @Test
  public void validateGitRepoUrl_hostNameNotInAllowList_throwsException() {
    assertThrows(
        InvalidReferenceException.class,
        () ->
            validationUtils.validateGitRepoUri(
                "ssh://git@github.com:DataBiosphere/terra-workspace-manager.git"));
  }

  @Test
  public void validateGitRepoUrl_httpUrl_throwsException() {
    assertThrows(
        InvalidReferenceException.class,
        () ->
            validationUtils.validateGitRepoUri(
                "http://github.com/DataBiosphere/terra-workspace-manager.git"));
  }

  @Test
  public void validateGitRepoUrl_opaqueUrl_throwsException() {
    assertThrows(
        InvalidReferenceException.class,
        () -> validationUtils.validateGitRepoUri("mailto:java-net@java.sun.com"));
  }

  @Test
  public void validateGitRepoUrl_sshUrlsWithHttpsSchema_throwsException() {
    assertThrows(
        InvalidReferenceException.class,
        () ->
            validationUtils.validateGitRepoUri(
                "https://git@github.com:DataBiosphere/terra-workspace-manager.gits"));
    assertThrows(
        InvalidReferenceException.class,
        () ->
            validationUtils.validateGitRepoUri(
                "https://git@github.com:DataBiosphere/terra-workspace-manager"));
  }

  @Test
  public void validateCloningInstructions_invalidCombination_throwsException() {
    assertThrows(
        BadRequestException.class,
        () ->
            ResourceValidationUtils.validateCloningInstructions(
                StewardshipType.REFERENCED, CloningInstructions.COPY_RESOURCE));
    assertThrows(
        BadRequestException.class,
        () ->
            ResourceValidationUtils.validateCloningInstructions(
                StewardshipType.REFERENCED, CloningInstructions.COPY_DEFINITION));

    // This will be supported if we implement PF-812.
    assertThrows(
        BadRequestException.class,
        () ->
            ResourceValidationUtils.validateCloningInstructions(
                StewardshipType.CONTROLLED, CloningInstructions.COPY_REFERENCE));
  }
}
