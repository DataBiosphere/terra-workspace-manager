package bio.terra.workspace.service.resource;

import static bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties.FOLDER_ID_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.app.configuration.external.GitRepoReferencedResourceConfiguration;
import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.exception.FieldSizeExceededException;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmImage;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceContainerImage;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceVmImage;
import bio.terra.workspace.service.resource.controlled.exception.RegionNotAllowedException;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.exception.InvalidNameException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import com.azure.core.management.Region;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class ResourceValidationUtilsTest extends BaseSpringBootUnitTest {

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
  @MockBean private ResourceDao mockResourceDao;

  @BeforeEach
  public void setup() {
    gitRepoReferencedResourceConfiguration.setAllowListedGitRepoHostNames(
        List.of("github.com", "gitlab.com", "bitbucket.org", "dev.azure.com", "ssh.dev.azure.com"));
    validationUtils = new ResourceValidationUtils(gitRepoReferencedResourceConfiguration);
  }

  @Test
  public void aiNotebookInstanceName() {
    GcpResourceValidationUtils.validateAiNotebookInstanceId("valid-instance-id-0");
    assertThrows(
        InvalidReferenceException.class,
        () -> GcpResourceValidationUtils.validateAiNotebookInstanceId("1number-first"));
    assertThrows(
        InvalidReferenceException.class,
        () -> GcpResourceValidationUtils.validateAiNotebookInstanceId("-dash-first"));
    assertThrows(
        InvalidReferenceException.class,
        () -> GcpResourceValidationUtils.validateAiNotebookInstanceId("dash-last-"));
    assertThrows(
        InvalidReferenceException.class,
        () -> GcpResourceValidationUtils.validateAiNotebookInstanceId("white space"));
    assertThrows(
        InvalidReferenceException.class,
        () -> GcpResourceValidationUtils.validateAiNotebookInstanceId("other-symbols^&)"));
    assertThrows(
        InvalidReferenceException.class,
        () -> GcpResourceValidationUtils.validateAiNotebookInstanceId("unicode-\\u00C6"));
    assertThrows(
        InvalidReferenceException.class,
        () ->
            GcpResourceValidationUtils.validateAiNotebookInstanceId(
                "more-than-63-chars111111111111111111111111111111111111111111111111111"));
  }

  @Test
  public void notebookCreationParametersExactlyOneOfVmImageOrContainerImage() {
    // Nothing throws on successful validation.
    GcpResourceValidationUtils.validate(
        ControlledGcpResourceFixtures.defaultNotebookCreationParameters());

    // Neither vmImage nor containerImage.
    assertThrows(
        InconsistentFieldsException.class,
        () ->
            GcpResourceValidationUtils.validate(
                ControlledGcpResourceFixtures.defaultNotebookCreationParameters()
                    .containerImage(null)
                    .vmImage(null)));
    // Both vmImage and containerImage.
    assertThrows(
        InconsistentFieldsException.class,
        () ->
            GcpResourceValidationUtils.validate(
                ControlledGcpResourceFixtures.defaultNotebookCreationParameters()
                    .containerImage(new ApiGcpAiNotebookInstanceContainerImage())
                    .vmImage(new ApiGcpAiNotebookInstanceVmImage())));
    // Valid containerImage.
    GcpResourceValidationUtils.validate(
        ControlledGcpResourceFixtures.defaultNotebookCreationParameters()
            .vmImage(null)
            .containerImage(
                new ApiGcpAiNotebookInstanceContainerImage().repository("my-repository")));
    // Valid vmImage.
    GcpResourceValidationUtils.validate(
        ControlledGcpResourceFixtures.defaultNotebookCreationParameters()
            .vmImage(new ApiGcpAiNotebookInstanceVmImage().imageName("image-name"))
            .containerImage(null));
    GcpResourceValidationUtils.validate(
        ControlledGcpResourceFixtures.defaultNotebookCreationParameters()
            .vmImage(new ApiGcpAiNotebookInstanceVmImage().imageFamily("image-family"))
            .containerImage(null));
    // Neither vmImage.imageName nor vmImage.familyName
    assertThrows(
        InconsistentFieldsException.class,
        () ->
            GcpResourceValidationUtils.validate(
                ControlledGcpResourceFixtures.defaultNotebookCreationParameters()
                    .vmImage(new ApiGcpAiNotebookInstanceVmImage())
                    .containerImage(null)));
    // Both vmImage.imageName and vmImage.familyName
    assertThrows(
        InconsistentFieldsException.class,
        () ->
            GcpResourceValidationUtils.validate(
                ControlledGcpResourceFixtures.defaultNotebookCreationParameters()
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
        () -> GcpResourceValidationUtils.validateGcsBucketNameAllowsUnderscore(INVALID_STRING));
  }

  @Test
  public void validateReferencedBucketName_nameHas63Character_OK() {
    GcpResourceValidationUtils.validateGcsBucketNameAllowsUnderscore(MAX_VALID_STRING);
  }

  @Test
  public void validateReferencedBucketName_nameHas2Character_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () -> GcpResourceValidationUtils.validateGcsBucketNameAllowsUnderscore("aa"));
  }

  @Test
  public void validateReferencedBucketName_nameHas3Character_OK() {
    GcpResourceValidationUtils.validateGcsBucketNameAllowsUnderscore("123");
  }

  @Test
  public void validateReferencedBucketName_nameHas222CharacterWithDotSeparator_OK() {
    GcpResourceValidationUtils.validateGcsBucketNameAllowsUnderscore(MAX_VALID_STRING_WITH_DOTS);
  }

  @Test
  public void
      validateReferencedBucketName_nameWithDotSeparatorButOneSubstringExceedsLimit_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () ->
            GcpResourceValidationUtils.validateGcsBucketNameAllowsUnderscore(
                INVALID_STRING + "." + MAX_VALID_STRING));
  }

  @Test
  public void validateReferencedBucketName_nameStartAndEndWithNumber_OK() {
    GcpResourceValidationUtils.validateGcsBucketNameAllowsUnderscore("1-bucket-1");
  }

  @Test
  public void validateReferencedBucketName_nameStartAndEndWithDot_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () -> GcpResourceValidationUtils.validateGcsBucketNameAllowsUnderscore(".bucket-name."));
  }

  @Test
  public void validateReferencedBucketName_nameWithGoogPrefix_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () ->
            GcpResourceValidationUtils.validateGcsBucketNameAllowsUnderscore("goog-bucket-name1"));
  }

  @Test
  public void validateReferencedBucketName_nameContainsGoogle_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () ->
            GcpResourceValidationUtils.validateGcsBucketNameAllowsUnderscore("bucket-google-name"));
  }

  @Test
  public void validateReferencedBucketName_nameContainsG00gle_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () ->
            GcpResourceValidationUtils.validateGcsBucketNameAllowsUnderscore("bucket-g00gle-name"));
  }

  @Test
  public void validateReferencedBucketName_nameContainsUnderscore_OK() {
    GcpResourceValidationUtils.validateGcsBucketNameAllowsUnderscore("bucket_name");
  }

  @Test
  public void validateControlledBucketName_nameContainsUnderscore_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () -> GcpResourceValidationUtils.validateGcsBucketNameDisallowUnderscore("bucket_name"));
  }

  @Test
  public void validateResourceDescription_nameTooLong_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () -> ResourceValidationUtils.validateResourceDescriptionName("a".repeat(2050)));
  }

  @Test
  public void validateResourceDescription_nameWith2048Char_validates() {
    ResourceValidationUtils.validateResourceDescriptionName("a".repeat(2048));
  }

  @Test
  public void validateBucketFileName_disallowName_throwException() {
    assertThrows(
        InvalidNameException.class, () -> GcpResourceValidationUtils.validateGcsObjectName("."));
  }

  @Test
  public void validateBucketFileName_emptyOrNullString_throwsException() {
    assertThrows(
        InvalidNameException.class, () -> GcpResourceValidationUtils.validateGcsObjectName(""));
  }

  @Test
  public void validateBucketFileName_startsWithAcmeChallengePrefix_throwsException() {
    String file = ".well-known/acme-challenge/hello.txt";

    assertThrows(
        InvalidNameException.class, () -> GcpResourceValidationUtils.validateGcsObjectName(file));
  }

  @Test
  public void validateBucketFileName_fileNameTooLong_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () ->
            GcpResourceValidationUtils.validateGcsObjectName(
                RandomStringUtils.random(1025, /* letters= */ true, /* numbers= */ true)));
  }

  @Test
  public void validateBucketFileName_legalFileName_validate() {
    GcpResourceValidationUtils.validateGcsObjectName("hello.txt");
    GcpResourceValidationUtils.validateGcsObjectName(
        RandomStringUtils.random(1024, /* letters= */ true, /* numbers= */ true));
    GcpResourceValidationUtils.validateGcsObjectName("你好.png");
  }

  @Test
  public void validateBqDataTableName() {
    GcpResourceValidationUtils.validateBqDataTableName("00_お客様");
    GcpResourceValidationUtils.validateBqDataTableName("table 01");
    GcpResourceValidationUtils.validateBqDataTableName("ग्राहक");
    GcpResourceValidationUtils.validateBqDataTableName("étudiant-01");
  }

  @Test
  public void validateBqDataTableName_invalidName_throwsException() {
    assertThrows(
        InvalidNameException.class,
        () -> GcpResourceValidationUtils.validateBqDataTableName("00_お客様*"));
    assertThrows(
        InvalidNameException.class, () -> GcpResourceValidationUtils.validateBqDataTableName(""));
  }

  @Test
  public void validateGitHubRepoUrl() {
    validationUtils.validateGitRepoUri("https://github.com/path/to/project.git");
    validationUtils.validateGitRepoUri("https://github.com/yuhuyoyo/testrepo.git");
    validationUtils.validateGitRepoUri("git@github.com:DataBiosphere/terra-workspace-manager.git");
    validationUtils.validateGitRepoUri("ssh://git@github.com/path/to/project.git");
    validationUtils.validateGitRepoUri(
        "https://username:password@github.com/username/repository.git");
  }

  @Test
  public void validateBitbucketRepoUrl() {
    validationUtils.validateGitRepoUri("git@bitbucket.org:path/to/project.git");
    validationUtils.validateGitRepoUri("https://yuhuyoyo-admin@bitbucket.org/path/to/project.git");
  }

  @Test
  public void validateGitLabRepoUrl() {
    validationUtils.validateGitRepoUri("git@gitlab.com:path/to/project.git");
    validationUtils.validateGitRepoUri("https://gitlab.com/path/to/project.git");
  }

  @Test
  public void validateAzureDevRepoUrl() {
    validationUtils.validateGitRepoUri(
        "https://yuhuyoyo@dev.azure.com/yuhuyoyo/yutestdemo/_git/yutestdemo");
    validationUtils.validateGitRepoUri("git@ssh.dev.azure.com:v3/yuhuyoyo/yutestdemo/yutestdemo");
  }

  @Test
  public void validateAwsCodeCommitRepoUrl() {
    validationUtils.validateGitRepoUri(
        "https://git-codecommit.us-east-2.amazonaws.com/v1/repos/MyDemoRepo");
    validationUtils.validateGitRepoUri(
        "ssh://git-codecommit.us-east-2.amazonaws.com/v1/repos/MyDemoRepo");
    validationUtils.validateGitRepoUri(
        "ssh://your-ssh-key-id@git-codecommit.us-east-2.amazonaws.com/v1/repos/MyDemoRepo");
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
  public void validateVmCreatePayload_missedVmImageParameters_throwsException() {
    var apiVmCreationParameters =
        new ApiAzureVmCreationParameters()
            .vmImage(new ApiAzureVmImage().uri("").publisher("").offer("").sku("").version(""));

    assertThrows(
        MissingRequiredFieldException.class,
        () -> AzureResourceValidationUtils.validateAzureVmImage(apiVmCreationParameters));
  }

  @Test
  public void validateVmCreatePayload_missedMarketplaceImageParameters_throwsException() {
    var apiVmCreationParameters =
        new ApiAzureVmCreationParameters()
            .vmImage(
                new ApiAzureVmImage().publisher("").offer("ubuntu").sku("gen2").version("latest"));

    assertThrows(
        MissingRequiredFieldException.class,
        () -> AzureResourceValidationUtils.validateAzureVmImage(apiVmCreationParameters));
  }

  @Test
  public void validateVmCreatePayload_missedMarketplaceImageVersionParameters_throwsException() {
    var apiVmCreationParameters =
        new ApiAzureVmCreationParameters()
            .vmImage(
                new ApiAzureVmImage()
                    .publisher("microsoft")
                    .offer("ubuntu")
                    .sku("gen2")
                    .version(""));

    assertThrows(
        MissingRequiredFieldException.class,
        () -> AzureResourceValidationUtils.validateAzureVmImage(apiVmCreationParameters));
  }

  @Test
  public void validateVmCreatePayload_missedVmUser_throwsException() {
    var apiVmCreationParameters =
        new ApiAzureVmCreationParameters()
            .vmImage(
                new ApiAzureVmImage()
                    .publisher("microsoft")
                    .offer("ubuntu")
                    .sku("gen2")
                    .version("latest"));

    assertThrows(
        MissingRequiredFieldException.class,
        () -> AzureResourceValidationUtils.validateAzureVmImage(apiVmCreationParameters));
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
  }

  @Test
  public void validateProperties_folderIdNotUuid_throwsBadRequestException() {
    assertThrows(
        BadRequestException.class,
        () -> ResourceValidationUtils.validateProperties(Map.of(FOLDER_ID_KEY, "root")));
  }

  @Test
  public void validateProperties_folderIdIsUuid_validates() {
    ResourceValidationUtils.validateProperties(Map.of(FOLDER_ID_KEY, UUID.randomUUID().toString()));
  }

  @Test
  public void validateFlexResourceDataSize_throwsFieldSizeExceededException() {
    byte[] largeData = new byte[5050];
    Arrays.fill(largeData, (byte) 'a');
    String decodedData = new String(largeData, StandardCharsets.UTF_8);

    assertThrows(
        FieldSizeExceededException.class,
        () -> ResourceValidationUtils.validateFlexResourceDataSize(decodedData));
  }

  @Test
  public void validateRegionGCP() throws Exception {
    var testRegions = List.of("us", "us-central1", "us-east1-a");
    UUID workspaceId = UUID.randomUUID();

    CloudPlatform cloudPlatform = CloudPlatform.GCP;
    when(mockTpsApiDispatch().listValidRegions(workspaceId, cloudPlatform))
        .thenReturn(List.of("US", "us-central1", "us-east1"));

    for (var region : testRegions) {
      // these validations should not throw an exception
      ResourceValidationUtils.validateRegionAgainstPolicy(
          mockTpsApiDispatch(), workspaceId, region, cloudPlatform);
      ResourceValidationUtils.validateRegionAgainstPolicy(
          mockTpsApiDispatch(), workspaceId, region.toUpperCase(Locale.ROOT), cloudPlatform);
    }
  }

  @Test
  public void validateRegionAzure() throws Exception {
    var testRegions = List.of(Region.US_EAST, Region.US_EAST2);
    UUID workspaceId = UUID.randomUUID();

    CloudPlatform cloudPlatform = CloudPlatform.AZURE;
    when(mockTpsApiDispatch().listValidRegions(workspaceId, cloudPlatform))
        .thenReturn(testRegions.stream().map(Region::name).toList());

    for (var region : testRegions) {
      // these validations should not throw an exception
      ResourceValidationUtils.validateRegionAgainstPolicy(
          mockTpsApiDispatch(), workspaceId, region.name(), cloudPlatform);
      ResourceValidationUtils.validateRegionAgainstPolicy(
          mockTpsApiDispatch(), workspaceId, region.name().toUpperCase(Locale.ROOT), cloudPlatform);
    }
  }

  @Test
  public void validateRegion_invalid_throws() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    when(mockTpsApiDispatch().listValidRegions(workspaceId, CloudPlatform.GCP))
        .thenReturn(List.of("us-central1", "us-east1"));

    assertThrows(
        RegionNotAllowedException.class,
        () ->
            ResourceValidationUtils.validateRegionAgainstPolicy(
                mockTpsApiDispatch(), workspaceId, "badregion", CloudPlatform.GCP));

    assertThrows(
        RegionNotAllowedException.class,
        () ->
            ResourceValidationUtils.validateRegionAgainstPolicy(
                mockTpsApiDispatch(), workspaceId, "badregion", CloudPlatform.AZURE));
  }

  @Test
  void validateExistingResourceRegions_valid() {
    String validRegion = "validRegion";
    UUID workspaceId = UUID.randomUUID();
    CloudPlatform cloudPlatform = CloudPlatform.AZURE;
    ControlledResource mockControlledResource = mock(ControlledResource.class);

    when(mockControlledResource.getName()).thenReturn("res name");
    when(mockControlledResource.getRegion()).thenReturn(validRegion);
    when(mockResourceDao.listControlledResources(workspaceId, cloudPlatform))
        .thenReturn(List.of(mockControlledResource));

    var result =
        ResourceValidationUtils.validateExistingResourceRegions(
            workspaceId, List.of(validRegion), cloudPlatform, mockResourceDao);

    assertTrue(result.isEmpty());
  }

  @Test
  void validateExistingResourceRegions_invalid() {
    String validRegion = "validRegion";
    UUID workspaceId = UUID.randomUUID();
    CloudPlatform cloudPlatform = CloudPlatform.AZURE;
    ControlledResource mockControlledResource = mock(ControlledResource.class);

    when(mockControlledResource.getName()).thenReturn("res name");
    when(mockControlledResource.getRegion()).thenReturn("invalidRegion");
    when(mockResourceDao.listControlledResources(workspaceId, cloudPlatform))
        .thenReturn(List.of(mockControlledResource));

    var result =
        ResourceValidationUtils.validateExistingResourceRegions(
            workspaceId, List.of(validRegion), cloudPlatform, mockResourceDao);

    assertEquals(1, result.size());
  }

  @Test
  void validateAzureDiskName_invalid() {
    assertThrows(
        InvalidReferenceException.class,
        () -> AzureResourceValidationUtils.validateAzureDiskName("$invalidDiskName$"));
  }

  @Test
  void validateAzureVmUserAssignedIdentities_valid() {
    AzureResourceValidationUtils.validateAzureVmUserAssignedIdentities(
        List.of("ident1", "ident2"), ManagedByType.MANAGED_BY_APPLICATION);
  }

  @Test
  void validateAzureVmUserAssignedIdentities_valid_empty() {
    AzureResourceValidationUtils.validateAzureVmUserAssignedIdentities(
        List.of(), ManagedByType.MANAGED_BY_USER);
    AzureResourceValidationUtils.validateAzureVmUserAssignedIdentities(
        List.of(), ManagedByType.MANAGED_BY_APPLICATION);
  }

  @Test
  void validateAzureVmUserAssignedIdentities_invalid() {
    assertThrows(
        ValidationException.class,
        () ->
            AzureResourceValidationUtils.validateAzureVmUserAssignedIdentities(
                List.of("ident1", "ident2"), ManagedByType.MANAGED_BY_USER));
  }
}
