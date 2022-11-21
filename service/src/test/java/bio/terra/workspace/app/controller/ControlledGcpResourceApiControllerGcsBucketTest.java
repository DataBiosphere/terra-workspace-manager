package bio.terra.workspace.app.controller;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertMapToApiProperties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.GcpCloudUtils;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpGcsBucketResult;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiResourceLineage;
import bio.terra.workspace.generated.model.ApiResourceLineageEntry;
import bio.terra.workspace.generated.model.ApiResourceMetadata;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.job.JobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Connected tests for controlled GCS buckets. */
// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.
@TestInstance(Lifecycle.PER_CLASS)
public class ControlledGcpResourceApiControllerGcsBucketTest extends BaseConnectedTest {

  // Use instructions that are only valid for controlled resource, to make sure we set instructions
  // appropriately for referenced resource.
  private static final ApiCloningInstructionsEnum BUCKET_CLONING_INSTRUCTIONS =
      ApiCloningInstructionsEnum.DEFINITION;

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired JobService jobService;
  @Autowired GcpCloudUtils cloudUtils;
  @Autowired FeatureConfiguration features;
  @Autowired CrlService crlService;

  private UUID workspaceId;
  private UUID workspaceId2;
  private ApiCreatedControlledGcpGcsBucket sourceBucket;

  @BeforeAll
  public void setup() throws Exception {
    workspaceId =
        mockMvcUtils
            .createWorkspaceWithCloudContext(userAccessUtils.defaultUserAuthRequest())
            .getId();
    workspaceId2 =
        mockMvcUtils
            .createWorkspaceWithCloudContext(userAccessUtils.defaultUserAuthRequest())
            .getId();

    sourceBucket =
        mockMvcUtils.createControlledGcsBucket(
            userAccessUtils.defaultUserAuthRequest(), workspaceId);
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    StairwayTestUtils.enumerateJobsDump(
        jobService, workspaceId, userAccessUtils.defaultUserAuthRequest());
    StairwayTestUtils.enumerateJobsDump(
        jobService, workspaceId2, userAccessUtils.defaultUserAuthRequest());
    jobService.setFlightDebugInfoForTest(null);
  }

  @AfterAll
  public void cleanup() throws Exception {
    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
  }

  @Test
  public void createAiNotebookInstance_correctZone() throws Exception {
    // So we don't interfere with other tests by setting properties.
    final UUID workspaceId =
        mockMvcUtils
            .createWorkspaceWithCloudContext(userAccessUtils.defaultUserAuthRequest())
            .getId();

    mockMvcUtils.updateWorkspaceProperties(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        List.of(
            new ApiProperty()
                .key(WorkspaceConstants.Properties.DEFAULT_RESOURCE_LOCATION)
                .value("asia-east1")));

    ApiGcpAiNotebookInstanceResource notebook =
        mockMvcUtils
            .createAiNotebookInstance(userAccessUtils.defaultUserAuthRequest(), workspaceId, null)
            .getAiNotebookInstance();

    assertEquals("asia-east1-a", notebook.getAttributes().getLocation());

    mockMvcUtils.updateWorkspaceProperties(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        List.of(
            new ApiProperty()
                .key(WorkspaceConstants.Properties.DEFAULT_RESOURCE_LOCATION)
                .value("fake-region")));

    notebook =
        mockMvcUtils
            .createAiNotebookInstance(userAccessUtils.defaultUserAuthRequest(), workspaceId, null)
            .getAiNotebookInstance();

    assertEquals("us-central1-a", notebook.getAttributes().getLocation());

    notebook =
        mockMvcUtils
            .createAiNotebookInstance(
                userAccessUtils.defaultUserAuthRequest(), workspaceId, "europe-west1-b")
            .getAiNotebookInstance();

    assertEquals("europe-west1-b", notebook.getAttributes().getLocation());

    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId);
  }

  @Test
  public void create_createdResourceEqualsGotResource() throws Exception {
    ApiGcpGcsBucketResource retrievedResource =
        mockMvcUtils.getControlledGcsBucket(
            userAccessUtils.defaultUserAuthRequest(), workspaceId, sourceBucket.getResourceId());
    ApiGcpGcsBucketResource expectedBucket = sourceBucket.getGcpBucket();

    assertResourceMetadata(expectedBucket.getMetadata(), retrievedResource.getMetadata());
    assertEquals(
        expectedBucket.getAttributes().getBucketName(),
        retrievedResource.getAttributes().getBucketName());
  }

  @Test
  public void clone_copyNothing() throws Exception {
    ApiCloneControlledGcpGcsBucketResult cloneResult =
        mockMvcUtils.cloneControlledGcsBucket(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceBucket.getResourceId(),
            /*destWorkspaceId=*/ workspaceId,
            ApiCloningInstructionsEnum.NOTHING);

    // Assert clone result has no CreatedControlledGcpGcsBucket
    assertNull(cloneResult.getBucket().getBucket());
  }

  @Test
  public void clone_copyDefinition() throws Exception {
    ApiCloneControlledGcpGcsBucketResult cloneResult =
        mockMvcUtils.cloneControlledGcsBucket(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceBucket.getResourceId(),
            /*destWorkspaceId=*/ workspaceId,
            ApiCloningInstructionsEnum.DEFINITION);

    // Assert bucket in clone result
    ApiGcpGcsBucketResource cloneResultBucket = cloneResult.getBucket().getBucket().getGcpBucket();
    String cloneBucketName = cloneResultBucket.getAttributes().getBucketName();
    assertBucket(
        cloneResultBucket,
        ApiStewardshipType.CONTROLLED,
        cloneBucketName,
        BUCKET_CLONING_INSTRUCTIONS);

    // Assert bucket returned by calling ControlledGcpResource.getBucket
    ApiGcpGcsBucketResource gotBucket =
        mockMvcUtils.getControlledGcsBucket(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            cloneResultBucket.getMetadata().getResourceId());
    assertBucket(
        gotBucket, ApiStewardshipType.CONTROLLED, cloneBucketName, BUCKET_CLONING_INSTRUCTIONS);
  }

  @Test
  public void clone_copyReference() throws Exception {
    ApiCloneControlledGcpGcsBucketResult cloneResult =
        mockMvcUtils.cloneControlledGcsBucket(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceBucket.getResourceId(),
            /*destWorkspaceId=*/ workspaceId,
            ApiCloningInstructionsEnum.REFERENCE);

    // Assert bucket in clone result
    ApiGcpGcsBucketResource cloneResultBucket = cloneResult.getBucket().getBucket().getGcpBucket();
    // Source bucket's COPY_DEFINITION gets converted to COPY_REFERENCE, since referenced
    // resources can't have COPY_DEFINITION.
    ApiCloningInstructionsEnum expectedCloningInstructions = ApiCloningInstructionsEnum.REFERENCE;
    String sourceBucketName = sourceBucket.getGcpBucket().getAttributes().getBucketName();
    assertBucket(
        cloneResultBucket,
        ApiStewardshipType.REFERENCED,
        sourceBucketName,
        expectedCloningInstructions);

    // Assert bucket returned by calling ReferencedGcpResource.getBucket
    ApiGcpGcsBucketResource gotBucket =
        mockMvcUtils.getReferencedGcsBucket(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            cloneResultBucket.getMetadata().getResourceId());
    assertBucket(
        gotBucket, ApiStewardshipType.REFERENCED, sourceBucketName, expectedCloningInstructions);
  }

  private void assertBucket(
      ApiGcpGcsBucketResource actualBucket,
      ApiStewardshipType expectedStewardshipType,
      String expectedBucketName,
      ApiCloningInstructionsEnum expectedCloningInstructions) {
    assertEquals(workspaceId, actualBucket.getMetadata().getWorkspaceId());
    assertEquals(expectedBucketName, actualBucket.getAttributes().getBucketName());
    assertEquals(
        ControlledResourceFixtures.RESOURCE_DESCRIPTION,
        actualBucket.getMetadata().getDescription());
    assertEquals(ApiResourceType.GCS_BUCKET, actualBucket.getMetadata().getResourceType());
    assertEquals(expectedStewardshipType, actualBucket.getMetadata().getStewardshipType());
    assertEquals(ApiCloudPlatform.GCP, actualBucket.getMetadata().getCloudPlatform());
    assertEquals(expectedCloningInstructions, actualBucket.getMetadata().getCloningInstructions());

    ApiResourceLineage expectedResourceLineage = new ApiResourceLineage();
    expectedResourceLineage.add(
        new ApiResourceLineageEntry()
            .sourceWorkspaceId(workspaceId)
            .sourceResourceId(sourceBucket.getResourceId()));
    assertEquals(expectedResourceLineage, actualBucket.getMetadata().getResourceLineage());

    assertEquals(
        convertMapToApiProperties(ControlledResourceFixtures.DEFAULT_RESOURCE_PROPERTIES),
        actualBucket.getMetadata().getProperties());

    String actualBucketName = actualBucket.getAttributes().getBucketName();
    assertEquals(expectedBucketName, actualBucketName);
  }

  private static void assertResourceMetadata(
      ApiResourceMetadata expectedMetadata, ApiResourceMetadata actualMetadata) {
    assertEquals(expectedMetadata.getName(), actualMetadata.getName());
    assertEquals(expectedMetadata.getDescription(), actualMetadata.getDescription());
    assertEquals(
        expectedMetadata.getCloningInstructions(), actualMetadata.getCloningInstructions());
    assertEquals(expectedMetadata.getStewardshipType(), actualMetadata.getStewardshipType());
    assertEquals(expectedMetadata.getResourceType(), actualMetadata.getResourceType());
    assertEquals(expectedMetadata.getProperties(), actualMetadata.getProperties());
  }
}
