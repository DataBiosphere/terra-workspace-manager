package bio.terra.workspace.app.controller;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertMapToApiProperties;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.defaultBigQueryDatasetCreationParameters;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi;
import static bio.terra.workspace.common.utils.MockMvcUtils.CONTROLLED_GCP_BIG_QUERY_DATASETS_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties.FOLDER_ID_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.app.controller.shared.PropertiesUtils;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpGcsBucketResult;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpBigQueryDataset;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiResourceLineage;
import bio.terra.workspace.generated.model.ApiResourceLineageEntry;
import bio.terra.workspace.generated.model.ApiResourceMetadata;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Use this instead of ControlledGcpResourceApiTest, if you want to talk to real GCP. */
// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.
@TestInstance(Lifecycle.PER_CLASS)
public class ControlledGcpResourceApiControllerConnectedTest extends BaseConnectedTest {
  private static final Logger logger =
      LoggerFactory.getLogger(ControlledGcpResourceApiControllerConnectedTest.class);

  // Use instructions that are only valid for controlled resource, to make sure we set instructions
  // appropriately for referenced resource.
  private static final ApiCloningInstructionsEnum BUCKET_CLONING_INSTRUCTIONS =
      ApiCloningInstructionsEnum.DEFINITION;

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessUtils userAccessUtils;

  private UUID workspaceId;
  private ApiCreatedControlledGcpGcsBucket sourceBucket;

  @BeforeAll
  public void setup() throws Exception {
    workspaceId =
        mockMvcUtils
            .createWorkspaceWithCloudContext(userAccessUtils.defaultUserAuthRequest())
            .getId();
    sourceBucket =
        mockMvcUtils.createControlledGcsBucket(
            userAccessUtils.defaultUserAuthRequest(), workspaceId);
  }

  @AfterAll
  public void cleanup() throws Exception {
    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId);
  }

  @Test
  public void createBigQueryDataset_createdResourceEqualsGotResource() throws Exception {
    ApiCreatedControlledGcpBigQueryDataset bqDataset =
        mockMvcUtils.createBigQueryDataset(userAccessUtils.defaultUserAuthRequest(), workspaceId);

    ApiGcpBigQueryDatasetResource actualBqDataset =
        mockMvcUtils.getBigQueryDataset(
            userAccessUtils.defaultUserAuthRequest(), workspaceId, bqDataset.getResourceId());
    ApiGcpBigQueryDatasetResource expectedBqDataset = bqDataset.getBigQueryDataset();

    assertResourceMetadata(expectedBqDataset.getMetadata(), actualBqDataset.getMetadata());
    assertEquals(
        expectedBqDataset.getAttributes().getDatasetId(),
        actualBqDataset.getAttributes().getDatasetId());
    assertEquals(
        expectedBqDataset.getAttributes().getProjectId(),
        actualBqDataset.getAttributes().getProjectId());
  }

  @Test
  public void createBigQueryDataset_resourceContainsInvalidFolderId_throws400() throws Exception {
    ApiCreateControlledGcpBigQueryDatasetRequestBody datasetCreationRequest =
        new ApiCreateControlledGcpBigQueryDatasetRequestBody()
            .common(
                makeDefaultControlledResourceFieldsApi()
                    .properties(
                        PropertiesUtils.convertMapToApiProperties(Map.of(FOLDER_ID_KEY, "root"))))
            .dataset(defaultBigQueryDatasetCreationParameters());

    mockMvc
        .perform(
            addAuth(
                post(String.format(
                        CONTROLLED_GCP_BIG_QUERY_DATASETS_V1_PATH_FORMAT, workspaceId.toString()))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(objectMapper.writeValueAsString(datasetCreationRequest)),
                userAccessUtils.defaultUserAuthRequest()))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void createBucket_createdResourceEqualsGotResource() throws Exception {
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
  public void cloneBucket_copyNothing() throws Exception {
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
  public void cloneBucket_copyDefinition() throws Exception {
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
  public void cloneBucket_copyReference() throws Exception {
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
