package bio.terra.workspace.app.configuration.external.controller;

import static bio.terra.workspace.common.utils.MockMvcUtils.CLONE_CONTROLLED_GCP_GCS_BUCKET_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.CLONE_RESULT_CONTROLLED_GCP_GCS_BUCKET_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpGcsBucketRequest;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpGcsBucketResult;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpBigQueryDataset;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport.StatusEnum;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Use this instead of ControlledGcpResourceApiTest, if you want to talk to real GCP. */
// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.
@TestInstance(Lifecycle.PER_CLASS)
public class ControlledGcpResourceApiControllerConnectedTest extends BaseConnectedTest {
  private static final Logger logger =
      LoggerFactory.getLogger(ControlledGcpResourceApiControllerConnectedTest.class);

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessUtils userAccessUtils;

  private ApiWorkspaceDescription workspace;
  private ApiCreatedControlledGcpGcsBucket originalControlledBucket;

  @BeforeAll
  public void setup() throws Exception {
    workspace =
        mockMvcUtils.createWorkspaceWithCloudContext(userAccessUtils.defaultUserAuthRequest());
    originalControlledBucket =
        mockMvcUtils.createGcsBucket(userAccessUtils.defaultUserAuthRequest(), workspace.getId());
  }

  @AfterAll
  public void cleanup() throws Exception {
    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspace.getId());
  }

  @Test
  public void createControlledBigQueryDataset_createdResourceEqualsGotResource() throws Exception {
    ApiCreatedControlledGcpBigQueryDataset bqDataset =
        mockMvcUtils.createBigQueryDataset(
            userAccessUtils.defaultUserAuthRequest(), workspace.getId());

    ApiGcpBigQueryDatasetResource retrievedResource =
        mockMvcUtils.getBigQueryDataset(
            userAccessUtils.defaultUserAuthRequest(), workspace.getId(), bqDataset.getResourceId());
    assertEquals(bqDataset.getBigQueryDataset(), retrievedResource);
  }

  @Test
  public void createControlledGcsBucket_createdResourceEqualsGotResource() throws Exception {
    ApiGcpGcsBucketResource retrievedResource =
        mockMvcUtils.getGcsBucket(
            userAccessUtils.defaultUserAuthRequest(),
            workspace.getId(),
            originalControlledBucket.getResourceId());
    assertEquals(originalControlledBucket.getGcpBucket(), retrievedResource);
  }

  @Test
  public void cloneControlledGcsBucket_copyNothing() throws Exception {
    ApiCloneControlledGcpGcsBucketResult cloneResult =
        cloneControlledGcsBucket(
            /*sourceWorkspaceId=*/ workspace.getId(),
            originalControlledBucket.getResourceId(),
            /*destWorkspaceId=*/ workspace.getId(),
            ApiCloningInstructionsEnum.NOTHING);

    // Assert clone result has no CreatedControlledGcpGcsBucket
    assertNull(cloneResult.getBucket().getBucket());
  }

  @Test
  public void cloneControlledGcsBucket_copyDefinition() throws Exception {
    ApiCloneControlledGcpGcsBucketResult cloneResult =
        cloneControlledGcsBucket(
            /*sourceWorkspaceId=*/ workspace.getId(),
            originalControlledBucket.getResourceId(),
            /*destWorkspaceId=*/ workspace.getId(),
            ApiCloningInstructionsEnum.DEFINITION);

    // Assert bucket in clone result
    ApiGcpGcsBucketResource cloneResultBucket = cloneResult.getBucket().getBucket().getGcpBucket();
    String cloneBucketName = cloneResultBucket.getAttributes().getBucketName();
    assertBucket(cloneResultBucket, ApiStewardshipType.CONTROLLED, cloneBucketName);

    // Assert bucket returned by calling ControlledGcpResource.getBucket
    ApiGcpGcsBucketResource gotBucket =
        mockMvcUtils.getGcsBucket(
            userAccessUtils.defaultUserAuthRequest(),
            workspace.getId(),
            cloneResultBucket.getMetadata().getResourceId());
    assertBucket(gotBucket, ApiStewardshipType.CONTROLLED, cloneBucketName);
  }

  @Disabled("PF-1930: Enable when feature is implemented")
  @Test
  public void cloneControlledGcsBucket_copyReference() throws Exception {
    ApiCloneControlledGcpGcsBucketResult cloneResult =
        cloneControlledGcsBucket(
            /*sourceWorkspaceId=*/ workspace.getId(),
            originalControlledBucket.getResourceId(),
            /*destWorkspaceId=*/ workspace.getId(),
            ApiCloningInstructionsEnum.REFERENCE);

    // Assert bucket in clone result
    ApiGcpGcsBucketResource cloneResultBucket = cloneResult.getBucket().getBucket().getGcpBucket();
    String cloneBucketName = cloneResultBucket.getAttributes().getBucketName();
    assertBucket(cloneResultBucket, ApiStewardshipType.REFERENCED, cloneBucketName);

    // Assert bucket returned by calling ControlledGcpResource.getBucket
    ApiGcpGcsBucketResource gotBucket =
        mockMvcUtils.getGcsBucket(
            userAccessUtils.defaultUserAuthRequest(),
            workspace.getId(),
            cloneResultBucket.getMetadata().getResourceId());
    assertBucket(gotBucket, ApiStewardshipType.REFERENCED, cloneBucketName);
  }

  /** Clones controlled bucket and waits for flight to finish. */
  private ApiCloneControlledGcpGcsBucketResult cloneControlledGcsBucket(
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions)
      throws Exception {
    ApiCloneControlledGcpGcsBucketResult result =
        startCloneControlledGcsBucketFlight(
            sourceWorkspaceId, sourceResourceId, destWorkspaceId, cloningInstructions);
    UUID jobId = UUID.fromString(result.getJobReport().getId());
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      Thread.sleep(/*millis=*/ 5000);
      result = getCloneControlledGcsBucketResult(destWorkspaceId, jobId);
    }
    assertEquals(StatusEnum.SUCCEEDED, result.getJobReport().getStatus());
    logger.info(
        "Controlled GCS bucket clone of resource %s completed. ".formatted(sourceResourceId));
    return result;
  }

  private ApiCloneControlledGcpGcsBucketResult startCloneControlledGcsBucketFlight(
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions)
      throws Exception {
    ApiCloneControlledGcpGcsBucketRequest request =
        new ApiCloneControlledGcpGcsBucketRequest()
            .destinationWorkspaceId(destWorkspaceId)
            .cloningInstructions(cloningInstructions)
            .name(TestUtils.appendRandomNumber("bucket-clone"))
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(CLONE_CONTROLLED_GCP_GCS_BUCKET_FORMAT.formatted(
                                sourceWorkspaceId, sourceResourceId))
                            .content(objectMapper.writeValueAsString(request)),
                        userAccessUtils.defaultUserAuthRequest())))
            .andExpect(status().is(HttpStatus.SC_ACCEPTED))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiCloneControlledGcpGcsBucketResult.class);
  }

  private ApiCloneControlledGcpGcsBucketResult getCloneControlledGcsBucketResult(
      UUID workspaceId, UUID jobId) throws Exception {
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        get(
                            CLONE_RESULT_CONTROLLED_GCP_GCS_BUCKET_FORMAT.formatted(
                                workspaceId.toString(), jobId.toString())),
                        userAccessUtils.defaultUserAuthRequest())))
            // Returns 200 if flight is done, 202 if flight is running.
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiCloneControlledGcpGcsBucketResult.class);
  }

  private static void assertBucket(
      ApiGcpGcsBucketResource actualBucket,
      ApiStewardshipType expectedStewardshipType,
      String expectedBucketName) {
    assertEquals(expectedStewardshipType, actualBucket.getMetadata().getStewardshipType());
    assertEquals(expectedBucketName, actualBucket.getAttributes().getBucketName());
  }
}
