package bio.terra.workspace.app.controller;

import static bio.terra.policy.model.TpsUpdateMode.DRY_RUN;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.SHORT_DESCRIPTION_PROPERTY;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.TYPE_PROPERTY;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.USER_SET_PROPERTY;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.VERSION_PROPERTY;
import static bio.terra.workspace.common.utils.MockMvcUtils.WORKSPACES_V1_BY_UFID_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.WORKSPACES_V1_BY_UUID_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.WORKSPACES_V1_EXPLAIN_POLICIES_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.WORKSPACES_V1_LIST_VALID_REGIONS_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.WORKSPACES_V1_MERGE_POLICIES_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.WORKSPACES_V1_PATH;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.policy.model.TpsUpdateMode;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.generated.model.ApiIamRole;
import bio.terra.workspace.generated.model.ApiRegions;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.generated.model.ApiWorkspaceDescriptionList;
import bio.terra.workspace.generated.model.ApiWsmPolicyExplainResult;
import bio.terra.workspace.generated.model.ApiWsmPolicyObject;
import bio.terra.workspace.generated.model.ApiWsmPolicySourceRequestBody;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
@Tag("connected")
@TestInstance(Lifecycle.PER_CLASS)
public class WorkspaceApiControllerConnectedTest extends BaseConnectedTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MockMvcUtils mockMvcUtils;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
  @Autowired private SamService samService;

  private ApiCreatedWorkspace workspace;

  // See here for how to skip workspace creation for local runs:
  // https://github.com/DataBiosphere/terra-workspace-manager#for-local-runs-skip-workspacecontext-creation
  @BeforeAll
  public void setup() throws Exception {
    workspace =
        mockMvcUtils.createWorkspaceWithoutCloudContext(userAccessUtils.defaultUserAuthRequest());
  }

  /** Clean up workspaces from Broad dev SAM. */
  @AfterAll
  public void cleanup() throws Exception {
    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspace.getId());
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
    mockMvcUtils.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    getWorkspaceExpectingError(
        userAccessUtils.secondUserAuthRequest(),
        workspace.getId(),
        /*minimumHighestRole=*/ Optional.empty(),
        HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void getWorkspace_requesterIsDiscoverer_requestMinHighestRoleSetToReader_throws()
      throws Exception {
    mockMvcUtils.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    getWorkspaceExpectingError(
        userAccessUtils.secondUserAuthRequest(),
        workspace.getId(),
        /*minimumHighestRole=*/ Optional.of(ApiIamRole.READER),
        HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void
      getWorkspace_requesterIsDiscoverer_requestMinHighestRoleSetToDiscoverer_returnsStrippedWorkspace()
          throws Exception {
    mockMvcUtils.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    ApiWorkspaceDescription gotWorkspace =
        getWorkspace(
            userAccessUtils.secondUserAuthRequest(),
            workspace.getId(),
            /*minimumHighestRole=*/ Optional.of(ApiIamRole.DISCOVERER));

    assertStrippedWorkspace(gotWorkspace);
  }

  @Test
  public void getWorkspaceByUserFacingId_requesterIsOwner_returnsFullWorkspace() throws Exception {
    ApiWorkspaceDescription workspaceDescription =
        mockMvcUtils.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspace.getId());

    ApiWorkspaceDescription gotWorkspace =
        getWorkspaceByUserFacingId(
            userAccessUtils.defaultUserAuthRequest(), workspaceDescription.getUserFacingId());

    assertFullWorkspace(gotWorkspace);
  }

  @Test
  public void getWorkspaceByUserFacingId_requesterIsDiscoverer_requestMinHighestRoleNotSet_throws()
      throws Exception {
    ApiWorkspaceDescription workspaceDescription =
        mockMvcUtils.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspace.getId());

    mockMvcUtils.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    getWorkspaceByUserFacingIdExpectingError(
        userAccessUtils.secondUserAuthRequest(),
        workspaceDescription.getUserFacingId(),
        /*minimumHighestRole=*/ Optional.empty(),
        HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void
      getWorkspaceByUserFacingId_requesterIsDiscoverer_requestMinHighestRoleSetToReader_throws()
          throws Exception {
    ApiWorkspaceDescription workspaceDescription =
        mockMvcUtils.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspace.getId());

    mockMvcUtils.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    getWorkspaceByUserFacingIdExpectingError(
        userAccessUtils.secondUserAuthRequest(),
        workspaceDescription.getUserFacingId(),
        /*minimumHighestRole=*/ Optional.of(ApiIamRole.READER),
        HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void
      getWorkspaceByUserFacingId_requesterIsDiscoverer_requestMinHighestRoleSetToDiscoverer_returnsStrippedWorkspace()
          throws Exception {
    ApiWorkspaceDescription workspaceDescription =
        mockMvcUtils.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspace.getId());

    mockMvcUtils.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    ApiWorkspaceDescription gotWorkspace =
        getWorkspaceByUserFacingId(
            userAccessUtils.secondUserAuthRequest(),
            workspaceDescription.getUserFacingId(),
            /*minimumHighestRole=*/ Optional.of(ApiIamRole.DISCOVERER));

    assertStrippedWorkspace(gotWorkspace);
  }

  @Disabled("Assert by list size is not reliable if other tests have failed")
  // TODO: PF-2413 Assert by list size is not reliable if other tests have failed
  @Test
  public void listWorkspaces_requesterIsOwner_returnsFullWorkspace() throws Exception {
    List<ApiWorkspaceDescription> listedWorkspaces =
        listWorkspaces(userAccessUtils.defaultUserAuthRequest());

    assertThat(
        String.format(
            "Expected 1 workspace. Got %s: %s", listedWorkspaces.size(), listedWorkspaces),
        listedWorkspaces.stream().map(ApiWorkspaceDescription::getId).toList(),
        hasSize(1));
    assertFullWorkspace(listedWorkspaces.get(0));
  }

  @Disabled("Assert by list size is not reliable if other tests have failed")
  // TODO: PF-2413 Assert by list size is not reliable if other tests have failed
  @Test
  public void listWorkspaces_requesterIsDiscoverer_requestMinHighestRoleNotSet_returnsNoWorkspaces()
      throws Exception {
    mockMvcUtils.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    List<ApiWorkspaceDescription> listedWorkspaces =
        listWorkspaces(userAccessUtils.secondUserAuthRequest());
    assertTrue(listedWorkspaces.isEmpty());
  }

  @Disabled("Assert by list size is not reliable if other tests have failed")
  // TODO: PF-2413 Assert by list size is not reliable if other tests have failed
  @Test
  public void
      listWorkspaces_requesterIsDiscoverer_requestMinHighestRoleSetToReader_returnsNoWorkspaces()
          throws Exception {
    mockMvcUtils.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    List<ApiWorkspaceDescription> listedWorkspaces =
        listWorkspaces(userAccessUtils.secondUserAuthRequest(), Optional.of(ApiIamRole.READER));

    assertTrue(listedWorkspaces.isEmpty());
  }

  @Test
  public void grantRole_logsAnActivity() throws Exception {
    mockMvcUtils.grantRole(
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
    mockMvcUtils.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspace.getId(),
        WsmIamRole.DISCOVERER,
        userAccessUtils.getSecondUserEmail());

    mockMvcUtils.removeRole(
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
    mockMvcUtils.grantRole(
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
  }

  @Test
  @EnabledIf(expression = "${feature.tps-enabled}", loadContext = true)
  public void mergePao() throws Exception {
    ApiWsmPolicyUpdateResult result =
        mergePao(
            userAccessUtils.defaultUserAuthRequest(),
            workspace.getId(),
            workspace.getId(),
            DRY_RUN);

    assertEquals(0,result.getConflicts().size());
//    assertEquals(result.getResultingPolicy(), workspace.);
  }

  @Test
  @EnabledIf(expression = "${feature.tps-enabled}", loadContext = true)
  public void listValidRegions() throws Exception {
    ApiRegions gcps =
        listValid(
            workspace.getId(),
            ApiCloudPlatform.GCP.name(),
            userAccessUtils.defaultUserAuthRequest());
    ApiRegions azures =
        listValid(
            workspace.getId(),
            ApiCloudPlatform.AZURE.name(),
            userAccessUtils.defaultUserAuthRequest());

    assertFalse(gcps.isEmpty());
    // Not an azure workspace so not valid azure regions.
    assertTrue(azures.isEmpty());
  }

  private ApiRegions listValid(
      UUID workspaceId, String platform, AuthenticatedUserRequest userRequest) throws Exception {
    var serializedResponse =
        mockMvc
            .perform(
                addAuth(
                    get(String.format(WORKSPACES_V1_LIST_VALID_REGIONS_PATH_FORMAT, workspaceId))
                        .queryParam("platform", platform),
                    userRequest))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiRegions.class);
  }

  private ApiWorkspaceDescription getWorkspace(AuthenticatedUserRequest request, UUID id)
      throws Exception {
    return getWorkspace(request, id, /*minimumHighestRole=*/ Optional.empty());
  }

  private ApiWorkspaceDescription getWorkspace(
      AuthenticatedUserRequest request, UUID id, Optional<ApiIamRole> minimumHighestRole)
      throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        get(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, id));
    if (minimumHighestRole.isPresent()) {
      requestBuilder.param("minimumHighestRole", minimumHighestRole.get().name());
    }
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
    MockHttpServletRequestBuilder requestBuilder =
        get(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, id));
    if (minimumHighestRole.isPresent()) {
      requestBuilder.param("minimumHighestRole", minimumHighestRole.get().name());
    }
    mockMvc.perform(addAuth(requestBuilder, userRequest)).andExpect(status().is(statusCode));
  }

  private ApiWorkspaceDescription getWorkspaceByUserFacingId(
      AuthenticatedUserRequest request, String userFacingId) throws Exception {
    return getWorkspaceByUserFacingId(
        request, userFacingId, /*minimumHighestRole=*/ Optional.empty());
  }

  private ApiWorkspaceDescription getWorkspaceByUserFacingId(
      AuthenticatedUserRequest request,
      String userFacingId,
      Optional<ApiIamRole> minimumHighestRole)
      throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        get(String.format(WORKSPACES_V1_BY_UFID_PATH_FORMAT, userFacingId));
    if (minimumHighestRole.isPresent()) {
      requestBuilder.param("minimumHighestRole", minimumHighestRole.get().name());
    }
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
        get(String.format(WORKSPACES_V1_BY_UFID_PATH_FORMAT, userFacingId));
    if (minimumHighestRole.isPresent()) {
      requestBuilder.param("minimumHighestRole", minimumHighestRole.get().name());
    }
    mockMvc.perform(addAuth(requestBuilder, userRequest)).andExpect(status().is(statusCode));
  }

  private List<ApiWorkspaceDescription> listWorkspaces(AuthenticatedUserRequest request)
      throws Exception {
    return listWorkspaces(request, /*minimumHighestRole=*/ Optional.empty());
  }

  private List<ApiWorkspaceDescription> listWorkspaces(
      AuthenticatedUserRequest request, Optional<ApiIamRole> minimumHighestRole) throws Exception {
    MockHttpServletRequestBuilder requestBuilder = get(WORKSPACES_V1_PATH);
    if (minimumHighestRole.isPresent()) {
      requestBuilder.param("minimumHighestRole", minimumHighestRole.get().name());
    }
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
    var serializedResponse =
        mockMvc
            .perform(
                addAuth(
                    get(String.format(WORKSPACES_V1_EXPLAIN_POLICIES_PATH_FORMAT, workspaceId))
                        .queryParam("depth", String.valueOf(depth)),
                    userRequest))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiWsmPolicyExplainResult.class);
  }

  private ApiWsmPolicyUpdateResult mergePao(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      UUID sourceObjectId,
      TpsUpdateMode updateMode)
      throws Exception {
    var serializedResponse =
        mockMvc
            .perform(
                addAuth(
                    get(String.format(WORKSPACES_V1_MERGE_POLICIES_PATH_FORMAT, workspaceId))
                        .queryParam("sourceObjectId", String.valueOf(sourceObjectId))
                        .queryParam("updateMode", String.valueOf(updateMode)),
                    userRequest))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiWsmPolicyUpdateResult.class);
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
