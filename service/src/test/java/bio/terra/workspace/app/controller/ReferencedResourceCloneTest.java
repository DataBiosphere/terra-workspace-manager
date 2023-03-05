package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.utils.MockMvcUtils.CLONE_REFERENCED_GCP_GCS_BUCKET_V1_PATH_FORMAT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.PolicyFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCloneReferencedResourceRequestBody;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.generated.model.ApiWsmPolicyInput;
import bio.terra.workspace.generated.model.ApiWsmPolicyInputs;
import bio.terra.workspace.generated.model.ApiWsmPolicyPair;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Test reference clone cases */
@Tag("connected")
public class ReferencedResourceCloneTest extends BaseConnectedTest {
  private static final Logger logger = LoggerFactory.getLogger(ReferencedResourceCloneTest.class);

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired FeatureConfiguration features;

  private final ApiWsmPolicyInput gcpUS = makeRegionPolicyInput("gcp.US");
  private final ApiWsmPolicyInput gcpIowa = makeRegionPolicyInput("gcp.Iowa");
  private final String sourceResourceName = TestUtils.appendRandomNumber("source-resource-name");
  private final String sourceBucketName = TestUtils.appendRandomNumber("source-bucket-name");

  private UUID workspaceId;
  private UUID workspaceId2;
  private ApiGcpGcsBucketResource sourceResource;

  @BeforeEach
  public void setup() throws Exception {
    workspaceId = UUID.randomUUID();
    var workspaceRequest =
        new ApiCreateWorkspaceRequestBody()
            .id(workspaceId)
            .displayName("clone source")
            .userFacingId(WorkspaceFixtures.getUserFacingId(workspaceId))
            .stage(ApiWorkspaceStageModel.MC_WORKSPACE)
            .spendProfile("wm-default-spend-profile")
            .policies(new ApiWsmPolicyInputs().addInputsItem(gcpUS));

    mockMvcUtils.createdWorkspaceWithoutCloudContext(
        userAccessUtils.defaultUserAuthRequest(), workspaceRequest);

    workspaceId2 = UUID.randomUUID();
    var workspace2Request =
        new ApiCreateWorkspaceRequestBody()
            .id(workspaceId2)
            .displayName("clone destination")
            .userFacingId(WorkspaceFixtures.getUserFacingId(workspaceId2))
            .stage(ApiWorkspaceStageModel.MC_WORKSPACE)
            .spendProfile("wm-default-spend-profile")
            .policies(null);

    mockMvcUtils.createdWorkspaceWithoutCloudContext(
        userAccessUtils.defaultUserAuthRequest(), workspace2Request);

    sourceResource =
        mockMvcUtils.createReferencedGcsBucket(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            sourceResourceName,
            sourceBucketName);
  }

  @AfterEach
  public void cleanup() throws Exception {
    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
  }

  @Test
  public void cloneMerge() throws Exception {
    // When we copy, the destination does not track the source policy
    testClone(ApiCloningInstructionsEnum.COPY_REFERENCE, "gcp.US");
  }

  @Test
  public void cloneLink() throws Exception {
    // When we link, the destination tracks the source policy
    testClone(ApiCloningInstructionsEnum.LINK_REFERENCE, "gcp.Iowa");
  }

  private void testClone(
      ApiCloningInstructionsEnum cloningInstruction, String expectedDestinationRegion)
      throws Exception {
    logger.info("Test workspaceId {}  workspaceId2 {}", workspaceId, workspaceId2);

    ApiCloneReferencedResourceRequestBody request =
        new ApiCloneReferencedResourceRequestBody()
            .destinationWorkspaceId(workspaceId2)
            .name(TestUtils.appendRandomNumber("dest-resource-name"))
            .cloningInstructions(cloningInstruction);

    var response =
        mockMvc
            .perform(
                MockMvcUtils.addJsonContentType(
                    MockMvcUtils.addAuth(
                        post(CLONE_REFERENCED_GCP_GCS_BUCKET_V1_PATH_FORMAT.formatted(
                                workspaceId, sourceResource.getMetadata().getResourceId()))
                            .content(objectMapper.writeValueAsString(request)),
                        userAccessUtils.defaultUserAuthRequest())))
            .andReturn()
            .getResponse();

    logger.info("Response: {}", response);
    String serializedResponse = response.getContentAsString();

    if (response.getStatus() >= 300) {
      var errorReport = objectMapper.readValue(serializedResponse, ApiErrorReport.class);
      logger.info("Error report: {}", errorReport);
      fail();
    }

    // Check that the destination receives the policy
    checkRegionPolicy(workspaceId2, "gcp.US");

    try {
      // Update the source workspace policy
      mockMvcUtils.updatePolicies(
          userAccessUtils.defaultUserAuthRequest(),
          workspaceId,
          /*policiesToAdd*/ List.of(gcpIowa),
          /*policiesToRemove*/ List.of(gcpUS));
    } catch (Exception e) {
      logger.info("Update failed with exception", e);
    }
    // Check that the source got updated and the destination did not change
    checkRegionPolicy(workspaceId, "gcp.Iowa");
    checkRegionPolicy(workspaceId2, expectedDestinationRegion);
  }

  private void checkRegionPolicy(UUID workspaceUuid, String expectedRegion) throws Exception {
    ApiWorkspaceDescription workspaceDescription =
        mockMvcUtils.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceUuid);

    List<ApiWsmPolicyInput> policies = workspaceDescription.getPolicies();
    assertEquals(1, policies.size());

    ApiWsmPolicyInput regionPolicy = policies.get(0);
    assertEquals(PolicyFixtures.REGION_CONSTRAINT, regionPolicy.getName());
    assertEquals(1, regionPolicy.getAdditionalData().size());

    ApiWsmPolicyPair region = regionPolicy.getAdditionalData().get(0);
    assertEquals(PolicyFixtures.REGION, region.getKey());
    assertEquals(expectedRegion, region.getValue());
  }

  private ApiWsmPolicyInput makeRegionPolicyInput(String region) {
    return new ApiWsmPolicyInput()
        .namespace(PolicyFixtures.NAMESPACE)
        .name(PolicyFixtures.REGION_CONSTRAINT)
        .addAdditionalDataItem(new ApiWsmPolicyPair().key(PolicyFixtures.REGION).value(region));
  }
}
