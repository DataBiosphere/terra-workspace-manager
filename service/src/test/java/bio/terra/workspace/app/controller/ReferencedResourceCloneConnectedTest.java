package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.RESOURCE_DESCRIPTION;
import static bio.terra.workspace.common.fixtures.PolicyFixtures.IOWA_REGION;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_SPEND_PROFILE_NAME;
import static bio.terra.workspace.common.mocks.MockGcpApi.CREATE_REFERENCED_GCP_GCS_BUCKETS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockWorkspaceV1Api.WORKSPACES_V1_CLONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.PolicyFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.mocks.MockGcpApi;
import bio.terra.workspace.common.mocks.MockMvcUtils;
import bio.terra.workspace.common.mocks.MockWorkspaceV1Api;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCloneWorkspaceRequest;
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
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpStatus;
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
@Tag("connectedPlus")
public class ReferencedResourceCloneConnectedTest extends BaseConnectedTest {
  private static final Logger logger =
      LoggerFactory.getLogger(ReferencedResourceCloneConnectedTest.class);

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired MockWorkspaceV1Api mockWorkspaceV1Api;
  @Autowired MockGcpApi mockGcpApi;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired FeatureConfiguration features;
  private final ApiWsmPolicyInput wsmTestGroup = makeGroupPolicyInput(PolicyFixtures.DEFAULT_GROUP);
  private final String sourceResourceName = TestUtils.appendRandomNumber("source-resource-name");
  private final String sourceBucketName = TestUtils.appendRandomNumber("source-bucket-name");

  private UUID sourceWorkspaceId;
  private UUID destinationWorkspaceId;
  private ApiGcpGcsBucketResource sourceResource;

  @BeforeEach
  public void setup() {
    sourceWorkspaceId = null;
    destinationWorkspaceId = null;
  }

  @AfterEach
  public void cleanup() throws Exception {
    mockWorkspaceV1Api.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), sourceWorkspaceId);
    if (destinationWorkspaceId != null) {
      mockWorkspaceV1Api.deleteWorkspace(
          userAccessUtils.defaultUserAuthRequest(), destinationWorkspaceId);
    }
  }

  @Test
  public void cloneResourceMerge_destResourceRegionIsNotUpdatedByTheSourceResource()
      throws Exception {
    // When we copy, the destination does not track the source policy
    testResourceClone(ApiCloningInstructionsEnum.REFERENCE);

    updateRegionPolicy(
        /* locationsToAdd= */ List.of(PolicyFixtures.REGION_POLICY_IOWA),
        /* locationsToRemove= */ List.of(PolicyFixtures.REGION_POLICY_USA));

    checkRegionPolicy(sourceWorkspaceId, List.of("iowa"));
    checkRegionPolicy(destinationWorkspaceId, List.of("usa"));
  }

  @Test
  public void cloneResourceLink_destResourceRegionIsUpdatedByTheSourceResource() throws Exception {
    // When we link, the destination tracks the source policy
    testResourceClone(ApiCloningInstructionsEnum.LINK_REFERENCE);

    updateRegionPolicy(
        /* locationsToAdd= */ List.of(PolicyFixtures.REGION_POLICY_IOWA),
        /* locationsToRemove= */ List.of(PolicyFixtures.REGION_POLICY_USA));

    checkRegionPolicy(sourceWorkspaceId, List.of("iowa"));
    checkRegionPolicy(destinationWorkspaceId, List.of("iowa"));
  }

  @Test
  public void cloneWorkspaceMerge_destWorkspaceRegionIsNotUpdatedByTheSourceResource()
      throws Exception {
    // When we copy, the destination does not track the source policy
    testWorkspaceClone(ApiCloningInstructionsEnum.REFERENCE);

    updateRegionPolicy(
        /* locationsToAdd= */ List.of(PolicyFixtures.REGION_POLICY_IOWA),
        /* locationsToRemove= */ List.of(PolicyFixtures.REGION_POLICY_USA));

    checkRegionPolicy(sourceWorkspaceId, List.of("iowa"));
    checkRegionPolicy(destinationWorkspaceId, List.of("usa"));
  }

  @Test
  public void cloneWorkspaceLink_destWorkspaceRegionIsUpdatedByTheSourceResource()
      throws Exception {
    // When we link, the destination tracks the source policy
    testWorkspaceClone(ApiCloningInstructionsEnum.LINK_REFERENCE);

    updateRegionPolicy(
        /* locationsToAdd= */ List.of(PolicyFixtures.REGION_POLICY_IOWA),
        /* locationsToRemove= */ List.of(PolicyFixtures.REGION_POLICY_USA));

    checkRegionPolicy(sourceWorkspaceId, List.of("iowa"));
    checkRegionPolicy(destinationWorkspaceId, List.of("iowa"));
  }

  @Test
  public void cloneWorkspaceLink_additionalRegionPolicy_changeToOregon_noUpdate() throws Exception {
    testWorkspaceCloneWithAdditionalPolicy(
        ApiCloningInstructionsEnum.LINK_REFERENCE,
        new ApiWsmPolicyInputs()
            .addInputsItem(wsmTestGroup)
            .addInputsItem(PolicyFixtures.REGION_POLICY_IOWA),
        List.of("iowa"));

    // When we link, the destination tracks the source policy
    updateRegionPolicyWithConflict(
        /* locationsToAdd= */ List.of(makeRegionPolicyInput("oregon")),
        /* locationsToRemove= */ List.of(PolicyFixtures.REGION_POLICY_USA),
        PolicyFixtures.REGION_CONSTRAINT);

    checkRegionPolicy(sourceWorkspaceId, List.of("usa"));
    checkRegionPolicy(destinationWorkspaceId, List.of("iowa"));
  }

  @Test
  public void cloneWorkspaceMerge_additionalRegionPolicy_changeToOregon_succeeds()
      throws Exception {
    testWorkspaceCloneWithAdditionalPolicy(
        ApiCloningInstructionsEnum.REFERENCE,
        new ApiWsmPolicyInputs()
            .addInputsItem(wsmTestGroup)
            .addInputsItem(PolicyFixtures.REGION_POLICY_IOWA),
        List.of("iowa"));

    // When we copy, the destination does not track the source policy
    updateRegionPolicy(
        /* locationsToAdd= */ List.of(makeRegionPolicyInput("oregon")),
        /* locationsToRemove= */ List.of(PolicyFixtures.REGION_POLICY_USA));

    checkRegionPolicy(sourceWorkspaceId, List.of("oregon"));
    checkRegionPolicy(destinationWorkspaceId, List.of("iowa"));
  }

  @Test
  public void cloneWorkspaceMerge_additionalPolicy_conflictRegionPolicy() throws Exception {
    workspaceSetup(ApiCloningInstructionsEnum.REFERENCE);
    ApiCloneWorkspaceRequest request =
        new ApiCloneWorkspaceRequest()
            .spendProfile(DEFAULT_SPEND_PROFILE_NAME)
            .additionalPolicies(
                new ApiWsmPolicyInputs().addInputsItem(makeRegionPolicyInput("asiapacific")));

    mockMvcUtils.postExpect(
        userAccessUtils.defaultUserAuthRequest(),
        objectMapper.writeValueAsString(request),
        WORKSPACES_V1_CLONE.formatted(sourceWorkspaceId.toString()),
        HttpStatus.SC_CONFLICT);
  }

  @Test
  public void cloneWorkspaceMerge_mergeCompatibleRegionPolicy() throws Exception {
    testWorkspaceCloneWithAdditionalPolicy(
        ApiCloningInstructionsEnum.REFERENCE,
        new ApiWsmPolicyInputs()
            .addInputsItem(wsmTestGroup)
            .addInputsItem(PolicyFixtures.REGION_POLICY_IOWA),
        List.of(IOWA_REGION));

    checkRegionPolicy(destinationWorkspaceId, List.of(IOWA_REGION));
    checkGroupPolicy(destinationWorkspaceId, List.of(PolicyFixtures.DEFAULT_GROUP));
  }

  private void testResourceClone(ApiCloningInstructionsEnum cloningInstruction) throws Exception {
    resourceSetup();

    logger.info("Test workspaceId {}  workspaceId2 {}", sourceWorkspaceId, destinationWorkspaceId);

    mockGcpApi.cloneReferencedGcsBucket(
        userAccessUtils.defaultUserAuthRequest(),
        sourceWorkspaceId,
        sourceResource.getMetadata().getResourceId(),
        destinationWorkspaceId,
        cloningInstruction,
        null);

    checkRegionPolicy(destinationWorkspaceId, List.of("usa"));
  }

  private void checkRegionPolicy(UUID workspaceUuid, List<String> expectedRegions)
      throws Exception {
    ApiWorkspaceDescription workspaceDescription =
        mockWorkspaceV1Api.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceUuid);

    List<ApiWsmPolicyInput> policies = workspaceDescription.getPolicies();
    ApiWsmPolicyInput regionPolicy =
        policies.stream()
            .filter(p -> p.getName().equals(PolicyFixtures.REGION_CONSTRAINT))
            .findAny()
            .get();
    assertEquals(PolicyFixtures.REGION_CONSTRAINT, regionPolicy.getName());
    assertEquals(expectedRegions.size(), regionPolicy.getAdditionalData().size());

    List<String> actualRegions =
        regionPolicy.getAdditionalData().stream()
            .filter(data -> data.getKey().equals(PolicyFixtures.REGION))
            .map(ApiWsmPolicyPair::getValue)
            .toList();
    assertEquals(expectedRegions, actualRegions);
  }

  private void checkGroupPolicy(UUID workspaceUuid, List<String> expectedGroups) throws Exception {
    ApiWorkspaceDescription workspaceDescription =
        mockWorkspaceV1Api.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceUuid);

    List<ApiWsmPolicyInput> policies = workspaceDescription.getPolicies();
    ApiWsmPolicyInput groupPolicy =
        policies.stream()
            .filter(
                p ->
                    p.getNamespace().equals(PolicyFixtures.NAMESPACE)
                        && p.getName().equals(PolicyFixtures.GROUP_CONSTRAINT))
            .findAny()
            .get();
    assertEquals(PolicyFixtures.GROUP_CONSTRAINT, groupPolicy.getName());
    assertEquals(expectedGroups.size(), groupPolicy.getAdditionalData().size());

    List<String> actualGroups =
        groupPolicy.getAdditionalData().stream()
            .filter(data -> data.getKey().equals(PolicyFixtures.GROUP))
            .map(ApiWsmPolicyPair::getValue)
            .toList();
    assertEquals(expectedGroups, actualGroups);
  }

  private ApiWsmPolicyInput makeRegionPolicyInput(String region) {
    return new ApiWsmPolicyInput()
        .namespace(PolicyFixtures.NAMESPACE)
        .name(PolicyFixtures.REGION_CONSTRAINT)
        .addAdditionalDataItem(new ApiWsmPolicyPair().key(PolicyFixtures.REGION).value(region));
  }

  private ApiWsmPolicyInput makeGroupPolicyInput(String group) {
    return new ApiWsmPolicyInput()
        .namespace(PolicyFixtures.NAMESPACE)
        .name(PolicyFixtures.GROUP_CONSTRAINT)
        .addAdditionalDataItem(new ApiWsmPolicyPair().key(PolicyFixtures.GROUP).value(group));
  }

  private void resourceSetup() throws Exception {
    sourceWorkspaceId = UUID.randomUUID();
    ApiCreateWorkspaceRequestBody workspaceRequest =
        new ApiCreateWorkspaceRequestBody()
            .id(sourceWorkspaceId)
            .displayName("clone source")
            .userFacingId(WorkspaceFixtures.getUserFacingId(sourceWorkspaceId))
            .stage(ApiWorkspaceStageModel.MC_WORKSPACE)
            .spendProfile("wm-default-spend-profile")
            .policies(new ApiWsmPolicyInputs().addInputsItem(PolicyFixtures.REGION_POLICY_USA));

    mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(
        userAccessUtils.defaultUserAuthRequest(), workspaceRequest);

    destinationWorkspaceId = UUID.randomUUID();
    ApiCreateWorkspaceRequestBody workspace2Request =
        new ApiCreateWorkspaceRequestBody()
            .id(destinationWorkspaceId)
            .displayName("clone destination")
            .userFacingId(WorkspaceFixtures.getUserFacingId(destinationWorkspaceId))
            .stage(ApiWorkspaceStageModel.MC_WORKSPACE)
            .spendProfile("wm-default-spend-profile")
            .policies(null);

    mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(
        userAccessUtils.defaultUserAuthRequest(), workspace2Request);

    sourceResource =
        mockGcpApi.createReferencedGcsBucket(
            userAccessUtils.defaultUserAuthRequest(),
            sourceWorkspaceId,
            sourceResourceName,
            sourceBucketName);
  }

  private void testWorkspaceClone(ApiCloningInstructionsEnum cloningInstructions) throws Exception {
    workspaceSetup(cloningInstructions);

    destinationWorkspaceId = UUID.randomUUID();
    mockWorkspaceV1Api.cloneWorkspace(
        userAccessUtils.defaultUserAuthRequest(),
        sourceWorkspaceId,
        "wm-default-spend-profile",
        null,
        destinationWorkspaceId);

    checkRegionPolicy(destinationWorkspaceId, List.of("usa"));
  }

  private void testWorkspaceCloneWithAdditionalPolicy(
      ApiCloningInstructionsEnum cloningInstructions,
      ApiWsmPolicyInputs additionalPolicies,
      List<String> expectedDestinationRegions)
      throws Exception {
    workspaceSetup(cloningInstructions);

    destinationWorkspaceId = UUID.randomUUID();
    mockWorkspaceV1Api.cloneWorkspace(
        userAccessUtils.defaultUserAuthRequest(),
        sourceWorkspaceId,
        "wm-default-spend-profile",
        additionalPolicies,
        destinationWorkspaceId);

    checkRegionPolicy(destinationWorkspaceId, expectedDestinationRegions);
  }

  private void workspaceSetup(ApiCloningInstructionsEnum cloningInstructions) throws Exception {
    sourceWorkspaceId = UUID.randomUUID();
    ApiCreateWorkspaceRequestBody workspaceRequest =
        new ApiCreateWorkspaceRequestBody()
            .id(sourceWorkspaceId)
            .displayName("clone source")
            .userFacingId(WorkspaceFixtures.getUserFacingId(sourceWorkspaceId))
            .stage(ApiWorkspaceStageModel.MC_WORKSPACE)
            .spendProfile("wm-default-spend-profile")
            .policies(new ApiWsmPolicyInputs().addInputsItem(PolicyFixtures.REGION_POLICY_USA));

    mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(
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
            CREATE_REFERENCED_GCP_GCS_BUCKETS_PATH_FORMAT,
            sourceWorkspaceId,
            objectMapper.writeValueAsString(request));
    sourceResource = objectMapper.readValue(serializedResponse, ApiGcpGcsBucketResource.class);
  }

  private void updateRegionPolicy(
      List<ApiWsmPolicyInput> locationsToAdd, List<ApiWsmPolicyInput> locationsToRemove) {
    try {
      // Update the source workspace policy
      ApiWsmPolicyUpdateResult result =
          mockWorkspaceV1Api.updatePolicies(
              userAccessUtils.defaultUserAuthRequest(),
              sourceWorkspaceId,
              /*policiesToAdd*/ locationsToAdd,
              /*policiesToRemove*/ locationsToRemove);
      assertTrue(result.getConflicts().isEmpty());
    } catch (Exception e) {
      logger.info("Update failed with exception", e);
    }
  }

  private void updateRegionPolicyWithConflict(
      List<ApiWsmPolicyInput> locationsToAdd,
      List<ApiWsmPolicyInput> locationsToRemove,
      String policyName) {
    try {
      // Update the source workspace policy
      ApiWsmPolicyUpdateResult result =
          mockWorkspaceV1Api.updatePoliciesAndExpect(
              userAccessUtils.defaultUserAuthRequest(),
              sourceWorkspaceId,
              /*policiesToAdd*/ locationsToAdd,
              /*policiesToRemove*/ locationsToRemove,
              HttpStatus.SC_CONFLICT);
      assertFalse(result.isUpdateApplied());
      assertFalse(
          result.getConflicts().stream()
              .filter(c -> c.getName().equals(policyName))
              .toList()
              .isEmpty());
    } catch (Exception e) {
      logger.info("Update failed with exception", e);
    }
  }
}
