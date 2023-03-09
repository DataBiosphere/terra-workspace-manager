package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.RESOURCE_DESCRIPTION;
import static bio.terra.workspace.common.utils.MockMvcUtils.REFERENCED_GCP_GCS_BUCKETS_V1_PATH_FORMAT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.PolicyFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCreateGcpGcsBucketReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketAttributes;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiReferenceResourceCommonFields;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.generated.model.ApiWsmPolicyInput;
import bio.terra.workspace.generated.model.ApiWsmPolicyInputs;
import bio.terra.workspace.generated.model.ApiWsmPolicyPair;
import bio.terra.workspace.generated.model.ApiWsmPolicyUpdateResult;
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

/**
 * Test reference clone cases These tests are made to ensure that we are doing the right policy
 * connecting when cloning a resource or a workspace containing a resource given different cloning
 * instructions: COPY_REFERENCE vs LINK_REFERENCE.
 */
@Tag("connected")
public class ReferencedResourceCloneTest extends BaseConnectedTest {
  private static final Logger logger = LoggerFactory.getLogger(ReferencedResourceCloneTest.class);

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired FeatureConfiguration features;

  private final ApiWsmPolicyInput gcpUS = makeRegionPolicyInput("usa");
  private final ApiWsmPolicyInput gcpIowa = makeRegionPolicyInput("iowa");
  private final String sourceResourceName = TestUtils.appendRandomNumber("source-resource-name");
  private final String sourceBucketName = TestUtils.appendRandomNumber("source-bucket-name");

  private UUID workspaceId;
  private UUID workspaceId2;
  private ApiGcpGcsBucketResource sourceResource;

  @BeforeEach
  public void setup() throws Exception {
    workspaceId = null;
    workspaceId2 = null;
  }

  @AfterEach
  public void cleanup() throws Exception {
    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
  }

  @Test
  public void cloneResourceMerge() throws Exception {
    // When we copy, the destination does not track the source policy
    testResourceClone(ApiCloningInstructionsEnum.REFERENCE, "usa");
  }

  @Test
  public void cloneResourceLink() throws Exception {
    // When we link, the destination tracks the source policy
    testResourceClone(ApiCloningInstructionsEnum.LINK_REFERENCE, "iowa");
  }

  @Test
  public void cloneWorkspaceMerge() throws Exception {
    // When we copy, the destination does not track the source policy
    testWorkspaceClone(ApiCloningInstructionsEnum.REFERENCE, "usa");
  }

  @Test
  public void cloneWorkspaceLink() throws Exception {
    // When we link, the destination tracks the source policy
    testWorkspaceClone(ApiCloningInstructionsEnum.LINK_REFERENCE, "iowa");
  }

  private void testResourceClone(
      ApiCloningInstructionsEnum cloningInstruction, String expectedDestinationRegion)
      throws Exception {
    resourceSetup();

    logger.info("Test workspaceId {}  workspaceId2 {}", workspaceId, workspaceId2);

    mockMvcUtils.cloneReferencedGcsBucket(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        sourceResource.getMetadata().getResourceId(),
        workspaceId2,
        cloningInstruction,
        null);

    checkRegionPolicy(workspaceId2, "usa");
    updatePolicies();
    checkRegionPolicy(workspaceId, "iowa");
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

  private void resourceSetup() throws Exception {
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

  private void testWorkspaceClone(
      ApiCloningInstructionsEnum cloningInstructions, String expectedDestinationRegion)
      throws Exception {
    workspaceSetup(cloningInstructions);

    workspaceId2 = UUID.randomUUID();
    mockMvcUtils.cloneWorkspace(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        "wm-default-spend-profile",
        workspaceId2);

    checkRegionPolicy(workspaceId2, "usa");
    updatePolicies();
    checkRegionPolicy(workspaceId, "iowa");
    checkRegionPolicy(workspaceId2, expectedDestinationRegion);
  }

  private void workspaceSetup(ApiCloningInstructionsEnum cloningInstructions) throws Exception {
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

    // Create the bucket with the test cloning instructions
    ApiGcpGcsBucketAttributes creationParameters =
        new ApiGcpGcsBucketAttributes().bucketName(sourceBucketName);
    ApiCreateGcpGcsBucketReferenceRequestBody request =
        new ApiCreateGcpGcsBucketReferenceRequestBody()
            .metadata(
                new ApiReferenceResourceCommonFields()
                    .name(sourceResourceName)
                    .description(RESOURCE_DESCRIPTION)
                    .cloningInstructions(cloningInstructions))
            .bucket(creationParameters);
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForPost(
            userAccessUtils.defaultUserAuthRequest(),
            REFERENCED_GCP_GCS_BUCKETS_V1_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    sourceResource = objectMapper.readValue(serializedResponse, ApiGcpGcsBucketResource.class);
  }

  private void updatePolicies() {
    try {
      // Update the source workspace policy
      ApiWsmPolicyUpdateResult result =
          mockMvcUtils.updatePolicies(
              userAccessUtils.defaultUserAuthRequest(),
              workspaceId,
              /*policiesToAdd*/ List.of(gcpIowa),
              /*policiesToRemove*/ List.of(gcpUS));
      assertTrue(result.getConflicts().isEmpty());
    } catch (Exception e) {
      logger.info("Update failed with exception", e);
    }
  }
}
