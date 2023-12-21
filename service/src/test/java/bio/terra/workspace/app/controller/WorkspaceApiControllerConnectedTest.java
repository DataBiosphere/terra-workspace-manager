package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.SHORT_DESCRIPTION_PROPERTY;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.TYPE_PROPERTY;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.USER_SET_PROPERTY;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.VERSION_PROPERTY;
import static bio.terra.workspace.common.mocks.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.mocks.MockMvcUtils.addJsonContentType;
import static bio.terra.workspace.common.mocks.MockWorkspaceV1Api.WORKSPACES_V1;
import static bio.terra.workspace.common.mocks.MockWorkspaceV1Api.WORKSPACES_V1_BY_UFID;
import static bio.terra.workspace.common.mocks.MockWorkspaceV1Api.WORKSPACES_V1_CREATE;
import static bio.terra.workspace.common.mocks.MockWorkspaceV1Api.WORKSPACES_V1_POLICIES_EXPLAIN;
import static bio.terra.workspace.common.mocks.MockWorkspaceV1Api.WORKSPACES_V1_POLICIES_MERGE_CHECK;
import static bio.terra.workspace.common.mocks.MockWorkspaceV1Api.buildWsmRegionPolicyInput;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.PolicyFixtures;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.common.mocks.MockGcpApi;
import bio.terra.workspace.common.mocks.MockMvcUtils;
import bio.terra.workspace.common.mocks.MockWorkspaceV1Api;
import bio.terra.workspace.common.mocks.MockWorkspaceV2Api;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.generated.model.ApiIamRole;
import bio.terra.workspace.generated.model.ApiMergeCheckRequest;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.generated.model.ApiWorkspaceDescriptionList;
import bio.terra.workspace.generated.model.ApiWsmPolicyExplainResult;
import bio.terra.workspace.generated.model.ApiWsmPolicyMergeCheckResult;
import bio.terra.workspace.generated.model.ApiWsmPolicyObject;
import bio.terra.workspace.generated.model.ApiWsmPolicyUpdateMode;
import bio.terra.workspace.generated.model.ApiWsmPolicyUpdateResult;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Connected tests for WorkspaceApiController.
 *
 * <p>In general, we would like to move towards testing new endpoints via controller instead of
 * calling services directly like we have in the past. Although this test duplicates coverage
 * currently in WorkspaceServiceTest, it's intended as a proof-of-concept for future mockMvc-based
 * tests.
 *
 * <p>Use this instead of WorkspaceApiControllerTest, if you want to use real
 * bio.terra.workspace.service.iam.SamService.
 */

// TODO: PF-2694 authorization check of the billing profile moved to the controller
//  where it is supposed to be. We need a test that exercises the user with no access to
//  billing profile generates the correct error. That test is removed from
//  CreateContextGcpFlightTest since it makes a direct call to workspaceService. We
//  want to see a SpendUnauthorizedException (Forbidden)
@Tag("connected")
@TestInstance(Lifecycle.PER_CLASS)
public class WorkspaceApiControllerConnectedTest extends BaseConnectedTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MockMvcUtils mockMvcUtils;
  @Autowired MockWorkspaceV1Api mockWorkspaceV1Api;
  @Autowired MockWorkspaceV2Api mockWorkspaceV2Api;
  @Autowired private MockGcpApi mockGcpApi;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private SamService samService;

  private ApiCreatedWorkspace workspace;

  // See here for how to skip workspace creation for local runs:
  // https://github.com/DataBiosphere/terra-workspace-manager#for-local-runs-skip-workspacecontext-creation
  @BeforeAll
  public void setup() throws Exception {
    workspace =
        mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(
            userAccessUtils.defaultUserAuthRequest());
  }

  /** Clean up workspaces from Broad dev SAM. */
  @AfterAll
  public void cleanup() throws Exception {
    mockWorkspaceV2Api.deleteWorkspaceAndWait(
        userAccessUtils.defaultUserAuthRequest(), workspace.getId());
  }

  @Test
  public void getWorkspace_requesterIsOwner_returnsFullWorkspace() throws Exception {
    ApiWorkspaceDescription gotWorkspace =
        getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspace.getId());

    assertFullWorkspace(gotWorkspace);
  }

  @Test
  public void getWorkspace_requesterIsDiscoverer_requestMinHighestRoleNotSet_throws()
      throws Exception {
    mockWorkspaceV1Api.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    getWorkspaceExpectingError(
        userAccessUtils.secondUserAuthRequest(),
        workspace.getId(),
        /* minimumHighestRole= */ Optional.empty(),
        HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void getWorkspace_requesterIsDiscoverer_requestMinHighestRoleSetToReader_throws()
      throws Exception {
    mockWorkspaceV1Api.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    getWorkspaceExpectingError(
        userAccessUtils.secondUserAuthRequest(),
        workspace.getId(),
        /* minimumHighestRole= */ Optional.of(ApiIamRole.READER),
        HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void
      getWorkspace_requesterIsDiscoverer_requestMinHighestRoleSetToDiscoverer_returnsStrippedWorkspace()
          throws Exception {
    mockWorkspaceV1Api.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    ApiWorkspaceDescription gotWorkspace =
        getWorkspace(
            userAccessUtils.secondUserAuthRequest(),
            workspace.getId(),
            /* minimumHighestRole= */ Optional.of(ApiIamRole.DISCOVERER));

    assertStrippedWorkspace(gotWorkspace);
  }

  @Test
  public void getWorkspaceByUserFacingId_requesterIsOwner_returnsFullWorkspace() throws Exception {
    ApiWorkspaceDescription workspaceDescription =
        mockWorkspaceV1Api.getWorkspace(
            userAccessUtils.defaultUserAuthRequest(), workspace.getId());

    ApiWorkspaceDescription gotWorkspace =
        getWorkspaceByUserFacingId(
            userAccessUtils.defaultUserAuthRequest(), workspaceDescription.getUserFacingId());

    assertFullWorkspace(gotWorkspace);
  }

  @Test
  public void getWorkspaceByUserFacingId_requesterIsDiscoverer_requestMinHighestRoleNotSet_throws()
      throws Exception {
    ApiWorkspaceDescription workspaceDescription =
        mockWorkspaceV1Api.getWorkspace(
            userAccessUtils.defaultUserAuthRequest(), workspace.getId());

    mockWorkspaceV1Api.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    getWorkspaceByUserFacingIdExpectingError(
        userAccessUtils.secondUserAuthRequest(),
        workspaceDescription.getUserFacingId(),
        /* minimumHighestRole= */ Optional.empty(),
        HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void
      getWorkspaceByUserFacingId_requesterIsDiscoverer_requestMinHighestRoleSetToReader_throws()
          throws Exception {
    ApiWorkspaceDescription workspaceDescription =
        mockWorkspaceV1Api.getWorkspace(
            userAccessUtils.defaultUserAuthRequest(), workspace.getId());

    mockWorkspaceV1Api.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    getWorkspaceByUserFacingIdExpectingError(
        userAccessUtils.secondUserAuthRequest(),
        workspaceDescription.getUserFacingId(),
        /* minimumHighestRole= */ Optional.of(ApiIamRole.READER),
        HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void
      getWorkspaceByUserFacingId_requesterIsDiscoverer_requestMinHighestRoleSetToDiscoverer_returnsStrippedWorkspace()
          throws Exception {
    ApiWorkspaceDescription workspaceDescription =
        mockWorkspaceV1Api.getWorkspace(
            userAccessUtils.defaultUserAuthRequest(), workspace.getId());

    mockWorkspaceV1Api.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    ApiWorkspaceDescription gotWorkspace =
        getWorkspaceByUserFacingId(
            userAccessUtils.secondUserAuthRequest(),
            workspaceDescription.getUserFacingId(),
            /* minimumHighestRole= */ Optional.of(ApiIamRole.DISCOVERER));

    assertStrippedWorkspace(gotWorkspace);
  }

  @Test
  public void listWorkspaces_requesterIsOwner_returnsFullWorkspace() throws Exception {
    List<ApiWorkspaceDescription> listedWorkspaces =
        listWorkspaces(userAccessUtils.defaultUserAuthRequest());

    List<ApiWorkspaceDescription> matchedWorkspace =
        listedWorkspaces.stream().filter(l -> l.getId().equals(workspace.getId())).toList();

    assertEquals(1, matchedWorkspace.size(), "Did not find expected workspace");
    assertFullWorkspace(matchedWorkspace.get(0));
  }

  @Test
  public void listWorkspaces_requesterIsDiscoverer_requestMinHighestRoleNotSet_returnsNoWorkspaces()
      throws Exception {
    mockWorkspaceV1Api.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    List<ApiWorkspaceDescription> listedWorkspaces =
        listWorkspaces(userAccessUtils.secondUserAuthRequest());
    List<ApiWorkspaceDescription> matchedWorkspace =
        listedWorkspaces.stream().filter(l -> l.getId().equals(workspace.getId())).toList();
    assertTrue(matchedWorkspace.isEmpty());
  }

  @Test
  public void
      listWorkspaces_requesterIsDiscoverer_requestMinHighestRoleSetToReader_returnsNoWorkspaces()
          throws Exception {
    mockWorkspaceV1Api.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    List<ApiWorkspaceDescription> listedWorkspaces =
        listWorkspaces(userAccessUtils.secondUserAuthRequest(), Optional.of(ApiIamRole.READER));
    List<ApiWorkspaceDescription> matchedWorkspace =
        listedWorkspaces.stream().filter(l -> l.getId().equals(workspace.getId())).toList();
    assertTrue(matchedWorkspace.isEmpty());
  }

  @Test
  public void grantRole_logsAnActivity() throws Exception {
    mockWorkspaceV1Api.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    // TODO(PF-2314): Change to call API. We don't expose this in API yet, so read from db
    mockMvcUtils.assertLatestActivityLogChangeDetails(
        workspace.getId(),
        userAccessUtils.getDefaultUserEmail(),
        samService.getUserStatusInfo(userAccessUtils.defaultUserAuthRequest()).getUserSubjectId(),
        OperationType.GRANT_WORKSPACE_ROLE,
        userAccessUtils.getSecondUserEmail(),
        ActivityLogChangedTarget.USER);
  }

  @Test
  public void removeRole_logsAnActivity() throws Exception {
    mockWorkspaceV1Api.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    mockWorkspaceV1Api.removeRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    // TODO(PF-2314): Change to call API. We don't expose this in API yet, so read from db.
    mockMvcUtils.assertLatestActivityLogChangeDetails(
        workspace.getId(),
        userAccessUtils.getDefaultUserEmail(),
        samService.getUserStatusInfo(userAccessUtils.defaultUserAuthRequest()).getUserSubjectId(),
        OperationType.REMOVE_WORKSPACE_ROLE,
        userAccessUtils.getSecondUserEmail(),
        ActivityLogChangedTarget.USER);
  }

  @Test
  public void
      listWorkspaces_requesterIsDiscoverer_requestMinHighestRoleSetToDiscoverer_returnsStrippedWorkspace()
          throws Exception {
    mockWorkspaceV1Api.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    List<ApiWorkspaceDescription> listedWorkspaces =
        listWorkspaces(userAccessUtils.secondUserAuthRequest(), Optional.of(ApiIamRole.DISCOVERER));

    assertThat(listedWorkspaces, hasSize(1));
    assertStrippedWorkspace(listedWorkspaces.get(0));
  }

  @Test
  @EnabledIf(expression = "${feature.tps-enabled}", loadContext = true)
  public void explainPolicies() throws Exception {
    ApiWsmPolicyExplainResult result =
        explainPolicies(userAccessUtils.defaultUserAuthRequest(), workspace.getId(), 0);

    assertEquals(0, result.getDepth());
    assertEquals(workspace.getId(), result.getObjectId());
    assertEquals(1, result.getExplainObjects().size());
    ApiWsmPolicyObject source = result.getExplainObjects().get(0);
    assertEquals(workspace.getId(), source.getObjectId());
    assertFalse(source.isDeleted());
    assertEquals(0, result.getExplanation().size());
    assertNotNull(source.getCreatedDate());
    assertNotNull(source.getLastUpdatedDate());
  }

  @Test
  @EnabledIf(expression = "${feature.tps-enabled}", loadContext = true)
  public void mergeCheck_sameWorkspace() throws Exception {
    ApiWsmPolicyMergeCheckResult result =
        mergeCheck(userAccessUtils.defaultUserAuthRequest(), workspace.getId(), workspace.getId());
    assertEquals(0, result.getConflicts().size());
    assertEquals(0, result.getResourcesWithConflict().size());
  }

  @Test
  @EnabledIf(expression = "${feature.tps-enabled}", loadContext = true)
  public void mergeCheck_workspaceWithDifferentRegion() throws Exception {
    // Create workspace with US region constraint.
    UUID targetWorkspaceId =
        mockWorkspaceV1Api.createWorkspaceWithRegionConstraint(
            userAccessUtils.defaultUserAuthRequest(), PolicyFixtures.US_REGION);

    // Create workspace with Europe region constraint.
    UUID sourceWorkspaceId =
        mockWorkspaceV1Api.createWorkspaceWithRegionConstraint(
            userAccessUtils.defaultUserAuthRequest(), PolicyFixtures.EUROPE_REGION);

    // Both workspaces have conflicting policy.
    ApiWsmPolicyMergeCheckResult result =
        mergeCheck(userAccessUtils.defaultUserAuthRequest(), targetWorkspaceId, sourceWorkspaceId);

    assertTrue(result.getConflicts().size() > 0);
    assertEquals(0, result.getResourcesWithConflict().size());
  }

  @Test
  @EnabledIf(expression = "${feature.tps-enabled}", loadContext = true)
  public void mergeCheck_resourceWithDifferentRegion() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    UUID targetWorkspaceId = null;
    UUID sourceWorkspaceId = null;
    try {
      //  Create target workspace with US region constraint.
      targetWorkspaceId =
          mockWorkspaceV1Api.createWorkspaceWithRegionConstraintAndCloudContext(
              userRequest, apiCloudPlatform, PolicyFixtures.US_REGION);

      // Then add a resource with US east region to the target.
      mockGcpApi.createControlledGcsBucket(
          userRequest,
          targetWorkspaceId,
          "resource-name",
          String.valueOf(UUID.randomUUID()),
          "US-EAST1",
          null,
          null);

      // Create source workspace with US central region constraint.
      sourceWorkspaceId =
          mockWorkspaceV1Api.createWorkspaceWithRegionConstraint(userRequest, "gcp.us-central1");

      // Target workspace has compatible policy (usa) with source.
      // However, target has resource (us-east1) that will conflict with the policy (us-central1) in
      // source workspace.
      ApiWsmPolicyMergeCheckResult result =
          mergeCheck(userRequest, targetWorkspaceId, sourceWorkspaceId);

      assertEquals(0, result.getConflicts().size());
      assertEquals(1, result.getResourcesWithConflict().size());
    } finally {
      mockWorkspaceV1Api.deleteWorkspace(userRequest, targetWorkspaceId);
      mockWorkspaceV1Api.deleteWorkspace(userRequest, sourceWorkspaceId);
    }
  }

  @Test
  @EnabledIf(expression = "${feature.tps-enabled}", loadContext = true)
  public void mergeCheck_existingResourceNoPolicyOnSource() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    UUID targetWorkspaceId = null;
    try {
      //  Create target workspace with US region constraint.
      targetWorkspaceId =
          mockWorkspaceV1Api.createWorkspaceWithRegionConstraintAndCloudContext(
              userRequest, apiCloudPlatform, PolicyFixtures.US_REGION);

      // Then add a resource with US east region to the target.
      mockGcpApi.createControlledGcsBucket(
          userRequest,
          targetWorkspaceId,
          "resource-name",
          String.valueOf(UUID.randomUUID()),
          "US-CENTRAL1",
          null,
          null);

      // Source has no region policy. Should be compatible with existing resource.
      ApiWsmPolicyMergeCheckResult result =
          mergeCheck(userRequest, targetWorkspaceId, workspace.getId());

      assertEquals(0, result.getConflicts().size());
      assertEquals(0, result.getResourcesWithConflict().size());
    } finally {
      mockWorkspaceV1Api.deleteWorkspace(userRequest, targetWorkspaceId);
    }
  }

  @Test
  @EnabledIf(expression = "${feature.tps-enabled}", loadContext = true)
  public void mergeCheck_caseInsensitiveRegion() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    UUID targetWorkspaceId = null;
    UUID sourceWorkspaceId = null;
    try {
      //  Create target workspace with US region constraint.
      targetWorkspaceId =
          mockWorkspaceV1Api.createWorkspaceWithRegionConstraintAndCloudContext(
              userRequest, apiCloudPlatform, PolicyFixtures.US_REGION);

      // Then add a resource with US east region to the target.
      mockGcpApi.createControlledGcsBucket(
          userRequest,
          targetWorkspaceId,
          "resource-name",
          String.valueOf(UUID.randomUUID()),
          "US-CENTRAL1",
          null,
          null);

      // Create source workspace with US central region constraint.
      sourceWorkspaceId =
          mockWorkspaceV1Api.createWorkspaceWithRegionConstraint(userRequest, "gcp.us-central1");

      // Target workspace has a compatible policy with the source.
      // But target has a resource in US-CENTRAL1, which doesn't match the casing of the policy.
      // This should be fine.
      ApiWsmPolicyMergeCheckResult result =
          mergeCheck(userRequest, targetWorkspaceId, sourceWorkspaceId);

      assertEquals(0, result.getConflicts().size());
      assertEquals(0, result.getResourcesWithConflict().size());
    } finally {
      if (targetWorkspaceId != null) {
        mockWorkspaceV1Api.deleteWorkspace(userRequest, targetWorkspaceId);
      }
      if (sourceWorkspaceId != null) {
        mockWorkspaceV1Api.deleteWorkspace(userRequest, sourceWorkspaceId);
      }
    }
  }

  @Test
  @EnabledIf(expression = "${feature.tps-enabled}", loadContext = true)
  public void mergeCheck_addGroup() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    UUID targetWorkspaceId = null;
    UUID sourceWorkspaceId = null;
    try {
      targetWorkspaceId =
          mockWorkspaceV1Api.createWorkspaceWithGroupConstraint(
              userRequest, PolicyFixtures.DEFAULT_GROUP);
      sourceWorkspaceId =
          mockWorkspaceV1Api.createWorkspaceWithGroupConstraint(
              userRequest, PolicyFixtures.ALT_GROUP);

      ApiWsmPolicyMergeCheckResult result =
          mergeCheck(userRequest, targetWorkspaceId, sourceWorkspaceId);

      // Adding an additional group should not conflict
      assertEquals(0, result.getConflicts().size());
    } finally {
      mockWorkspaceV1Api.deleteWorkspace(userRequest, targetWorkspaceId);
      mockWorkspaceV1Api.deleteWorkspace(userRequest, sourceWorkspaceId);
    }
  }

  @Test
  @EnabledIf(expression = "${feature.tps-enabled}", loadContext = true)
  public void mergeCheck_combineGroupAndRegion() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    UUID targetWorkspaceId = null;
    UUID sourceWorkspaceId = null;
    try {
      targetWorkspaceId =
          mockWorkspaceV1Api.createWorkspaceWithGroupConstraint(
              userRequest, PolicyFixtures.DEFAULT_GROUP);
      sourceWorkspaceId =
          mockWorkspaceV1Api.createWorkspaceWithRegionConstraintAndCloudContext(
              userRequest, apiCloudPlatform, PolicyFixtures.US_REGION);

      ApiWsmPolicyMergeCheckResult result =
          mergeCheck(userRequest, targetWorkspaceId, sourceWorkspaceId);

      assertEquals(0, result.getConflicts().size());
    } finally {
      mockWorkspaceV1Api.deleteWorkspace(userRequest, targetWorkspaceId);
      mockWorkspaceV1Api.deleteWorkspace(userRequest, sourceWorkspaceId);
    }
  }

  @Test
  @EnabledIf(expression = "${feature.tps-enabled}", loadContext = true)
  public void mergeCheck_noGroupChange() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    UUID targetWorkspaceId = null;
    UUID sourceWorkspaceId = null;
    try {
      targetWorkspaceId =
          mockWorkspaceV1Api.createWorkspaceWithGroupConstraint(
              userRequest, PolicyFixtures.DEFAULT_GROUP);
      sourceWorkspaceId =
          mockWorkspaceV1Api.createWorkspaceWithGroupConstraint(
              userRequest, PolicyFixtures.DEFAULT_GROUP);

      ApiWsmPolicyMergeCheckResult result =
          mergeCheck(userRequest, targetWorkspaceId, sourceWorkspaceId);

      // No change to groups, should merge just fine.
      assertEquals(0, result.getConflicts().size());
    } finally {
      mockWorkspaceV1Api.deleteWorkspace(userRequest, targetWorkspaceId);
      mockWorkspaceV1Api.deleteWorkspace(userRequest, sourceWorkspaceId);
    }
  }

  @Test
  @EnabledIf(expression = "${feature.tps-enabled}", loadContext = true)
  public void updatePolicies_tpsEnabledAndPolicyUpdated() throws Exception {
    ApiWorkspaceDescription workspaceWithoutPolicy =
        mockWorkspaceV1Api.getWorkspace(
            userAccessUtils.defaultUserAuthRequest(), workspace.getId());
    assertTrue(workspaceWithoutPolicy.getPolicies().isEmpty());

    // add attributes
    String usRegion = PolicyFixtures.US_REGION;
    ApiWsmPolicyUpdateResult result =
        mockWorkspaceV1Api.updateRegionPolicy(
            userAccessUtils.defaultUserAuthRequest(), workspace.getId(), usRegion);

    assertTrue(result.isUpdateApplied());
    ApiWorkspaceDescription updatedWorkspace =
        mockWorkspaceV1Api.getWorkspace(
            userAccessUtils.defaultUserAuthRequest(), workspace.getId());
    assertEquals(1, updatedWorkspace.getPolicies().size());
    assertEquals(
        usRegion, updatedWorkspace.getPolicies().get(0).getAdditionalData().get(0).getValue());

    // remove attributes
    ApiWsmPolicyUpdateResult removeResult =
        mockWorkspaceV1Api.removeRegionPolicy(
            userAccessUtils.defaultUserAuthRequest(), workspace.getId(), usRegion);

    assertTrue(removeResult.isUpdateApplied());
    workspaceWithoutPolicy =
        mockWorkspaceV1Api.getWorkspace(
            userAccessUtils.defaultUserAuthRequest(), workspace.getId());
    assertEquals(0, workspaceWithoutPolicy.getPolicies().size());
  }

  @Test
  @EnabledIf(expression = "${feature.tps-enabled}", loadContext = true)
  public void updatePolicies_tpsEnabledAndPolicyConflict() throws Exception {
    ApiWorkspaceDescription workspaceWithoutPolicy =
        mockWorkspaceV1Api.getWorkspace(
            userAccessUtils.defaultUserAuthRequest(), workspace.getId());
    assertTrue(workspaceWithoutPolicy.getPolicies().isEmpty());

    String usRegion = PolicyFixtures.US_REGION;
    ApiWsmPolicyUpdateResult result =
        mockWorkspaceV1Api.updateRegionPolicy(
            userAccessUtils.defaultUserAuthRequest(), workspace.getId(), usRegion);

    assertTrue(result.isUpdateApplied());
    ApiWorkspaceDescription updatedWorkspace =
        mockWorkspaceV1Api.getWorkspace(
            userAccessUtils.defaultUserAuthRequest(), workspace.getId());
    assertEquals(1, updatedWorkspace.getPolicies().size());

    mockWorkspaceV1Api.updatePoliciesAndExpect(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        buildWsmRegionPolicyInput(PolicyFixtures.EUROPE_REGION),
        ApiWsmPolicyUpdateMode.FAIL_ON_CONFLICT,
        HttpStatus.SC_CONFLICT);
    mockWorkspaceV1Api.updatePoliciesAndExpect(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        buildWsmRegionPolicyInput("asiapacific"),
        ApiWsmPolicyUpdateMode.ENFORCE_CONFLICT,
        HttpStatus.SC_CONFLICT);
    updatedWorkspace =
        mockWorkspaceV1Api.getWorkspace(
            userAccessUtils.defaultUserAuthRequest(), workspace.getId());
    assertEquals(1, updatedWorkspace.getPolicies().size());
    assertEquals(
        usRegion, updatedWorkspace.getPolicies().get(0).getAdditionalData().get(0).getValue());

    // clean up
    mockWorkspaceV1Api.removeRegionPolicy(
        userAccessUtils.defaultUserAuthRequest(), workspace.getId(), usRegion);
  }

  @Test
  public void updatePolicies_requesterIsWriter_throws() throws Exception {
    mockWorkspaceV1Api.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.WRITER,
        userAccessUtils.noBillingUser().getEmail());

    mockWorkspaceV1Api.updatePoliciesAndExpect(
        userAccessUtils.noBillingAccessUserAuthRequest(),
        workspace.getId(),
        buildWsmRegionPolicyInput("asiapacific"),
        ApiWsmPolicyUpdateMode.ENFORCE_CONFLICT,
        HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void workspaceOwnerCannotAbandonWorkspace() throws Exception {
    // Default user should be the only workspace owner, and so should not be able to remove
    // themselves.
    mockWorkspaceV1Api.removeRoleExpectBadRequest(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.OWNER,
        userAccessUtils.getDefaultUserEmail());
    // After adding a second user, they should be able to remove themselves.
    mockWorkspaceV1Api.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.OWNER,
        userAccessUtils.getSecondUserEmail());
    mockWorkspaceV1Api.removeRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.OWNER,
        userAccessUtils.getDefaultUserEmail());
    // Reset workspace to starting setup, where default user is an owner and secondary user has no
    // role.
    mockWorkspaceV1Api.grantRole(
        userAccessUtils.secondUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.OWNER,
        userAccessUtils.getDefaultUserEmail());
    mockWorkspaceV1Api.removeRole(
        userAccessUtils.secondUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.OWNER,
        userAccessUtils.getSecondUserEmail());
  }

  private ApiWorkspaceDescription getWorkspace(AuthenticatedUserRequest request, UUID id)
      throws Exception {
    return getWorkspace(request, id, /* minimumHighestRole= */ Optional.empty());
  }

  private ApiWorkspaceDescription getWorkspace(
      AuthenticatedUserRequest request, UUID id, Optional<ApiIamRole> minimumHighestRole)
      throws Exception {
    MockHttpServletRequestBuilder requestBuilder = get(String.format(WORKSPACES_V1, id));
    minimumHighestRole.ifPresent(
        apiIamRole -> requestBuilder.param("minimumHighestRole", apiIamRole.name()));
    String serializedResponse =
        mockMvc
            .perform(addAuth(requestBuilder, request))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiWorkspaceDescription.class);
  }

  private void getWorkspaceExpectingError(
      AuthenticatedUserRequest userRequest,
      UUID id,
      Optional<ApiIamRole> minimumHighestRole,
      int statusCode)
      throws Exception {
    MockHttpServletRequestBuilder requestBuilder = get(String.format(WORKSPACES_V1, id));
    minimumHighestRole.ifPresent(
        apiIamRole -> requestBuilder.param("minimumHighestRole", apiIamRole.name()));
    mockMvc.perform(addAuth(requestBuilder, userRequest)).andExpect(status().is(statusCode));
  }

  private ApiWorkspaceDescription getWorkspaceByUserFacingId(
      AuthenticatedUserRequest request, String userFacingId) throws Exception {
    return getWorkspaceByUserFacingId(
        request, userFacingId, /* minimumHighestRole= */ Optional.empty());
  }

  private ApiWorkspaceDescription getWorkspaceByUserFacingId(
      AuthenticatedUserRequest request,
      String userFacingId,
      Optional<ApiIamRole> minimumHighestRole)
      throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        get(String.format(WORKSPACES_V1_BY_UFID, userFacingId));
    minimumHighestRole.ifPresent(
        apiIamRole -> requestBuilder.param("minimumHighestRole", apiIamRole.name()));
    String serializedResponse =
        mockMvc
            .perform(addAuth(requestBuilder, request))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiWorkspaceDescription.class);
  }

  private void getWorkspaceByUserFacingIdExpectingError(
      AuthenticatedUserRequest userRequest,
      String userFacingId,
      Optional<ApiIamRole> minimumHighestRole,
      int statusCode)
      throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        get(String.format(WORKSPACES_V1_BY_UFID, userFacingId));
    minimumHighestRole.ifPresent(
        apiIamRole -> requestBuilder.param("minimumHighestRole", apiIamRole.name()));
    mockMvc.perform(addAuth(requestBuilder, userRequest)).andExpect(status().is(statusCode));
  }

  private List<ApiWorkspaceDescription> listWorkspaces(AuthenticatedUserRequest request)
      throws Exception {
    return listWorkspaces(request, /* minimumHighestRole= */ Optional.empty());
  }

  private List<ApiWorkspaceDescription> listWorkspaces(
      AuthenticatedUserRequest request, Optional<ApiIamRole> minimumHighestRole) throws Exception {
    MockHttpServletRequestBuilder requestBuilder = get(WORKSPACES_V1_CREATE);
    minimumHighestRole.ifPresent(
        apiIamRole -> requestBuilder.param("minimumHighestRole", apiIamRole.name()));
    String serializedResponse =
        mockMvc
            .perform(addAuth(requestBuilder, request))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper
        .readValue(serializedResponse, ApiWorkspaceDescriptionList.class)
        .getWorkspaces();
  }

  private ApiWsmPolicyExplainResult explainPolicies(
      AuthenticatedUserRequest userRequest, UUID workspaceId, int depth) throws Exception {
    String serializedResponse =
        mockMvc
            .perform(
                addAuth(
                    get(String.format(WORKSPACES_V1_POLICIES_EXPLAIN, workspaceId))
                        .queryParam("depth", String.valueOf(depth)),
                    userRequest))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiWsmPolicyExplainResult.class);
  }

  private ApiWsmPolicyMergeCheckResult mergeCheck(
      AuthenticatedUserRequest userRequest, UUID targetWorkspaceId, UUID sourceWorkspaceId)
      throws Exception {
    ApiMergeCheckRequest request = new ApiMergeCheckRequest().workspaceId(sourceWorkspaceId);
    String serializedResponse =
        mockMvc
            .perform(
                addAuth(
                    addJsonContentType(
                        post(String.format(WORKSPACES_V1_POLICIES_MERGE_CHECK, targetWorkspaceId))
                            .content(objectMapper.writeValueAsString(request))),
                    userRequest))
            .andExpect(status().is(HttpStatus.SC_ACCEPTED))
            .andReturn()
            .getResponse()
            .getContentAsString();

    return objectMapper.readValue(serializedResponse, ApiWsmPolicyMergeCheckResult.class);
  }

  /** Assert all workspace fields are set, when requester has at least READER role. */
  private void assertFullWorkspace(ApiWorkspaceDescription workspace) {
    assertNotNull(workspace.getId());
    assertThat(workspace.getUserFacingId(), not(emptyString()));
    assertThat(workspace.getDisplayName(), not(emptyString()));
    assertThat(workspace.getDescription(), not(emptyString()));
    assertNotNull(workspace.getHighestRole());
    assertNotNull(workspace.getStage());
    assertThat(
        workspace.getProperties(),
        containsInAnyOrder(
            TYPE_PROPERTY, SHORT_DESCRIPTION_PROPERTY, VERSION_PROPERTY, USER_SET_PROPERTY));
    assertNotNull(workspace.getCreatedDate());
    assertThat(workspace.getCreatedBy(), not(emptyString()));
    assertNotNull(workspace.getLastUpdatedDate());
    assertThat(workspace.getLastUpdatedBy(), not(emptyString()));
  }

  /** Assert subset of workspace fields are set, when requester only has DISCOVERER role. */
  private void assertStrippedWorkspace(ApiWorkspaceDescription workspace) {
    assertNotNull(workspace.getId());
    assertThat(workspace.getUserFacingId(), not(emptyString()));
    assertThat(workspace.getDisplayName(), not(emptyString()));
    // Description not returned
    assertNull(workspace.getDescription());
    assertNotNull(workspace.getHighestRole());
    assertNotNull(workspace.getStage());
    // Only type, short description and version properties are returned, not properties set by user
    assertThat(
        workspace.getProperties(),
        containsInAnyOrder(TYPE_PROPERTY, SHORT_DESCRIPTION_PROPERTY, VERSION_PROPERTY));
    assertNotNull(workspace.getCreatedDate());
    assertThat(workspace.getCreatedBy(), not(emptyString()));
    assertNotNull(workspace.getLastUpdatedDate());
    assertThat(workspace.getLastUpdatedBy(), not(emptyString()));
  }
}
