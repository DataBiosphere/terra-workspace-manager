package bio.terra.workspace.app.controller;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertMapToApiProperties;
import static bio.terra.workspace.common.utils.MockMvcUtils.CLONE_WORKSPACE_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.UPDATE_WORKSPACES_V1_PROPERTIES_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.WORKSPACES_V1_BY_UUID_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.WORKSPACES_V1_PATH;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static bio.terra.workspace.common.utils.MockMvcUtils.createWorkspace;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.amalgam.tps.TpsApiDispatch;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.generated.model.ApiCloneWorkspaceRequest;
import bio.terra.workspace.generated.model.ApiCloneWorkspaceResult;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiTpsComponent;
import bio.terra.workspace.generated.model.ApiTpsObjectType;
import bio.terra.workspace.generated.model.ApiTpsPaoGetResult;
import bio.terra.workspace.generated.model.ApiTpsPolicyInput;
import bio.terra.workspace.generated.model.ApiTpsPolicyInputs;
import bio.terra.workspace.generated.model.ApiTpsPolicyPair;
import bio.terra.workspace.generated.model.ApiUpdateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.generated.model.ApiWorkspaceDescriptionList;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants.SamResource;
import bio.terra.workspace.service.iam.model.SamConstants.SamSpendProfileAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * An example of a mockMvc-based unit test for a controller.
 *
 * <p>In general, we would like to move towards testing new endpoints via controller instead of
 * calling services directly like we have in the past. Although this test duplicates coverage
 * currently in WorkspaceServiceTest, it's intended as a proof-of-concept for future mockMvc-based
 * tests.
 */
public class WorkspaceApiControllerTest extends BaseUnitTest {

  AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest(
          "fake@email.com", "subjectId123456", Optional.of("ThisIsNotARealBearerToken"));
  /** A fake group-constraint policy for a workspace. */
  private static final ApiTpsPolicyInput GROUP_POLICY =
      new ApiTpsPolicyInput()
          .namespace("terra")
          .name("group-constraint")
          .addAdditionalDataItem(new ApiTpsPolicyPair().key("group").value("my_fake_group"));

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean FeatureConfiguration mockFeatureConfiguration;
  @MockBean TpsApiDispatch mockTpsApiDispatch;
  @MockBean SamService mockSamService;

  @BeforeEach
  public void setup() throws InterruptedException {
    when(mockSamService.isAuthorized(
            any(), eq(SamResource.SPEND_PROFILE), any(), eq(SamSpendProfileAction.LINK)))
        .thenReturn(true);
    when(mockSamService.listRequesterRoles(any(), any(), any()))
        .thenReturn(List.of(WsmIamRole.OWNER));
    when(mockSamService.getUserStatusInfo(any()))
        .thenReturn(
            new UserStatusInfo()
                .userEmail(USER_REQUEST.getEmail())
                .userSubjectId(USER_REQUEST.getSubjectId()));

    when(mockFeatureConfiguration.isTpsEnabled()).thenReturn(true);
    // We don't need to mock tpsCheck() because Mockito will already do nothing by default.

    // Pretend every workspace has an empty policy. The ID on the PAO will not match the workspace
    // ID, but that doesn't matter for tests which don't care about policy.
    when(mockTpsApiDispatch.getPaoIfExists(any(), any()))
        .thenReturn(Optional.of(emptyWorkspacePao()));
  }

  @Test
  public void createDuplicateWorkspace() throws Exception {
    var createRequest = WorkspaceFixtures.createWorkspaceRequestBody();
    MockHttpServletResponse createResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(WORKSPACES_V1_PATH)
                            .content(objectMapper.writeValueAsString(createRequest)),
                        USER_REQUEST)))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse();

    int duplicateCreateResponseStatus =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(WORKSPACES_V1_PATH)
                            .content(objectMapper.writeValueAsString(createRequest)),
                        USER_REQUEST)))
            .andExpect(status().is(HttpStatus.SC_CONFLICT))
            .andReturn()
            .getResponse()
            .getStatus();

    assertEquals(HttpStatus.SC_CONFLICT, duplicateCreateResponseStatus);
  }

  @Test
  public void updateWorkspace() throws Exception {
    ApiCreatedWorkspace workspace = createWorkspace(mockMvc, objectMapper);
    String newDisplayName = "new workspace display name";
    String newUserFacingId = "new-ufid";
    String newDescription = "new description for the workspace";

    String serializedUpdateResponse =
        mockMvc
            .perform(
                addAuth(
                    patch(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, workspace.getId()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(
                            getUpdateRequestInJson(
                                newDisplayName, newUserFacingId, newDescription)),
                    USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    ApiWorkspaceDescription updatedWorkspaceDescription =
        objectMapper.readValue(serializedUpdateResponse, ApiWorkspaceDescription.class);

    assertEquals(newDisplayName, updatedWorkspaceDescription.getDisplayName());
    assertEquals(newDescription, updatedWorkspaceDescription.getDescription());
    assertEquals(newUserFacingId, updatedWorkspaceDescription.getUserFacingId());
    OffsetDateTime firstLastUpdatedDate = updatedWorkspaceDescription.getLastUpdatedDate();
    assertNotNull(firstLastUpdatedDate);
    OffsetDateTime createdDate = updatedWorkspaceDescription.getCreatedDate();
    assertNotNull(createdDate);
    assertTrue(firstLastUpdatedDate.isAfter(createdDate));
    assertEquals(USER_REQUEST.getEmail(), updatedWorkspaceDescription.getCreatedBy());
    assertEquals(USER_REQUEST.getEmail(), updatedWorkspaceDescription.getLastUpdatedBy());

    var newUser = new UserStatusInfo().userEmail("foo@gmail.com").userSubjectId("foo");
    when(mockSamService.getUserStatusInfo(any())).thenReturn(newUser);
    var secondNewDescription = "This is yet another description";
    String serializedSecondUpdateResponse =
        mockMvc
            .perform(
                addAuth(
                    patch(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, workspace.getId()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(
                            getUpdateRequestInJson(
                                newDisplayName, newUserFacingId, secondNewDescription)),
                    USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    ApiWorkspaceDescription secondUpdatedWorkspaceDescription =
        objectMapper.readValue(serializedSecondUpdateResponse, ApiWorkspaceDescription.class);

    assertEquals(secondNewDescription, secondUpdatedWorkspaceDescription.getDescription());
    var secondLastUpdatedDate = secondUpdatedWorkspaceDescription.getLastUpdatedDate();
    assertTrue(firstLastUpdatedDate.isBefore(secondLastUpdatedDate));
    assertNotNull(secondUpdatedWorkspaceDescription.getCreatedDate());
    assertEquals(createdDate, secondUpdatedWorkspaceDescription.getCreatedDate());
    assertEquals(newUser.getUserEmail(), secondUpdatedWorkspaceDescription.getLastUpdatedBy());
  }

  @Test
  public void deleteWorkspaceProperties() throws Exception {
    UUID workspaceId = createWorkspace(mockMvc, objectMapper).getId();
    ApiWorkspaceDescription sourceWorkspace = getWorkspaceDescription(workspaceId);
    ArrayList propertyUpdate = new ArrayList<>(Arrays.asList("foo", "foo1"));

    mockMvc
        .perform(
            addAuth(
                patch(String.format(UPDATE_WORKSPACES_V1_PROPERTIES_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(objectMapper.writeValueAsString(propertyUpdate)),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_NO_CONTENT))
        .andReturn()
        .getResponse()
        .getContentAsString();

    assertEquals(
        sourceWorkspace.getProperties(), convertMapToApiProperties(Map.of("xyzzy", "plohg")));
  }

  @Test
  public void updateWorkspaceProperties() throws Exception {
    UUID workspaceId = createWorkspace(mockMvc, objectMapper).getId();
    ApiWorkspaceDescription sourceWorkspace = getWorkspaceDescription(workspaceId);
    Map<String, String> properties = Map.of("foo", "bar");

    mockMvc
        .perform(
            addAuth(
                post(String.format(UPDATE_WORKSPACES_V1_PROPERTIES_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(
                        objectMapper.writeValueAsString(convertMapToApiProperties(properties))),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_NO_CONTENT))
        .andReturn()
        .getResponse()
        .getContentAsString();

    assertEquals(sourceWorkspace.getProperties(), convertMapToApiProperties(properties));
  }

  @Test
  public void cloneWorkspace() throws Exception {
    UUID workspaceId = createWorkspace(mockMvc, objectMapper).getId();
    ApiWorkspaceDescription sourceWorkspace = getWorkspaceDescription(workspaceId);

    String serializedGetResponse =
        mockMvc
            .perform(
                addAuth(
                    post(String.format(CLONE_WORKSPACE_PATH_FORMAT, workspaceId))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(
                            objectMapper.writeValueAsString(
                                new ApiCloneWorkspaceRequest()
                                    .spendProfile(SamResource.SPEND_PROFILE))),
                    USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_ACCEPTED))
            .andReturn()
            .getResponse()
            .getContentAsString();
    ApiCloneWorkspaceResult cloneWorkspace =
        objectMapper.readValue(serializedGetResponse, ApiCloneWorkspaceResult.class);

    UUID destinationWorkspaceId = cloneWorkspace.getWorkspace().getDestinationWorkspaceId();
    ApiWorkspaceDescription destinationWorkspace = getWorkspaceDescription(destinationWorkspaceId);

    assertEquals(sourceWorkspace.getProperties(), destinationWorkspace.getProperties());
    assertEquals(
        sourceWorkspace.getDisplayName() + " (Copy)", destinationWorkspace.getDisplayName());
  }

  @Test
  public void policyRejectedForRawlsWorkspace() throws Exception {
    var createRequest = WorkspaceFixtures.createWorkspaceRequestBody();
    createRequest
        .stage(ApiWorkspaceStageModel.RAWLS_WORKSPACE)
        .policies(
            new ApiTpsPolicyInputs()
                .addInputsItem(
                    new ApiTpsPolicyInput().namespace("terra").name("group-constraint")));
    String serializedError =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(WORKSPACES_V1_PATH)
                            .content(objectMapper.writeValueAsString(createRequest)),
                        USER_REQUEST)))
            .andExpect(status().is(HttpStatus.SC_BAD_REQUEST))
            .andReturn()
            .getResponse()
            .getContentAsString();
    ApiErrorReport errorReport = objectMapper.readValue(serializedError, ApiErrorReport.class);
    assertEquals(HttpStatus.SC_BAD_REQUEST, errorReport.getStatusCode());
    assertTrue(
        errorReport.getMessage().contains(ApiWorkspaceStageModel.RAWLS_WORKSPACE.toString()));
  }

  @Test
  public void policyRejectedIfTpsDisabled() throws Exception {
    // Disable TPS feature flag for this test only
    when(mockFeatureConfiguration.isTpsEnabled()).thenReturn(false);

    var createRequest = WorkspaceFixtures.createWorkspaceRequestBody();
    createRequest.policies(
        new ApiTpsPolicyInputs()
            .addInputsItem(new ApiTpsPolicyInput().namespace("terra").name("group-constraint")));
    String serializedError =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(WORKSPACES_V1_PATH)
                            .content(objectMapper.writeValueAsString(createRequest)),
                        USER_REQUEST)))
            .andExpect(status().is(HttpStatus.SC_NOT_IMPLEMENTED))
            .andReturn()
            .getResponse()
            .getContentAsString();
    ApiErrorReport errorReport = objectMapper.readValue(serializedError, ApiErrorReport.class);
    assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, errorReport.getStatusCode());
    assertTrue(errorReport.getMessage().contains("enabled"));
  }

  @Test
  public void getWorkspaceIncludesPolicy() throws Exception {
    // No need to actually pass policy inputs because TPS is mocked.
    ApiCreatedWorkspace workspace = createWorkspace(mockMvc, objectMapper);

    ApiTpsPaoGetResult getPolicyResult =
        emptyWorkspacePao()
            .objectId(workspace.getId())
            .attributes(new ApiTpsPolicyInputs().addInputsItem(GROUP_POLICY))
            .effectiveAttributes(new ApiTpsPolicyInputs().addInputsItem(GROUP_POLICY));
    when(mockTpsApiDispatch.getPaoIfExists(any(), eq(workspace.getId())))
        .thenReturn(Optional.of(getPolicyResult));

    ApiWorkspaceDescription gotWorkspace = getWorkspaceDescription(workspace.getId());
    assertEquals(1, gotWorkspace.getPolicies().size());
    assertEquals(GROUP_POLICY, gotWorkspace.getPolicies().get(0));
  }

  @Test
  public void tpsDisabledGetWorkspaceExcludesPolicy() throws Exception {
    when(mockFeatureConfiguration.isTpsEnabled()).thenReturn(false);
    ApiCreatedWorkspace workspace = createWorkspace(mockMvc, objectMapper);

    ApiWorkspaceDescription gotWorkspace = getWorkspaceDescription(workspace.getId());
    assertNull(gotWorkspace.getPolicies());
  }

  @Test
  public void listWorkspaceIncludesPolicy() throws Exception {
    // No need to actually pass policy inputs because TPS is mocked.
    ApiCreatedWorkspace workspace = createWorkspace(mockMvc, objectMapper);
    ApiCreatedWorkspace noPolicyWorkspace = createWorkspace(mockMvc, objectMapper);
    when(mockSamService.listWorkspaceIdsAndHighestRoles(any(), any()))
        .thenReturn(
            ImmutableMap.of(
                workspace.getId(), WsmIamRole.OWNER, noPolicyWorkspace.getId(), WsmIamRole.OWNER));

    ApiTpsPaoGetResult getPolicyResult =
        new ApiTpsPaoGetResult()
            .attributes(new ApiTpsPolicyInputs().addInputsItem(GROUP_POLICY))
            .effectiveAttributes(new ApiTpsPolicyInputs().addInputsItem(GROUP_POLICY))
            .component(ApiTpsComponent.WSM)
            .objectType(ApiTpsObjectType.WORKSPACE)
            .objectId(workspace.getId())
            .sourcesObjectIds(Collections.emptyList());

    // Return a policy object for the first workspace
    when(mockTpsApiDispatch.getPaoIfExists(any(), eq(workspace.getId())))
        .thenReturn(Optional.of(getPolicyResult));
    // Treat the second workspace like it was created before policy existed and doesn't have a PAO
    when(mockTpsApiDispatch.getPaoIfExists(any(), eq(noPolicyWorkspace.getId())))
        .thenReturn(Optional.empty());

    ApiWorkspaceDescription gotWorkspace = getWorkspaceDescriptionFromList(workspace.getId());
    assertEquals(1, gotWorkspace.getPolicies().size());
    assertEquals(GROUP_POLICY, gotWorkspace.getPolicies().get(0));
    ApiWorkspaceDescription gotNoPolicyWorkspace =
        getWorkspaceDescriptionFromList(noPolicyWorkspace.getId());
    assertTrue(gotNoPolicyWorkspace.getPolicies().isEmpty());
  }

  @Test
  public void tpsDisabledListWorkspaceExcludesPolicy() throws Exception {
    when(mockFeatureConfiguration.isTpsEnabled()).thenReturn(false);
    ApiCreatedWorkspace workspace = createWorkspace(mockMvc, objectMapper);
    when(mockSamService.listWorkspaceIdsAndHighestRoles(any(), any()))
        .thenReturn(ImmutableMap.of(workspace.getId(), WsmIamRole.OWNER));

    ApiWorkspaceDescription gotWorkspace = getWorkspaceDescriptionFromList(workspace.getId());
    assertNull(gotWorkspace.getPolicies());
  }

  private String getUpdateRequestInJson(
      String newDisplayName, String newUserFacingId, String newDescription)
      throws JsonProcessingException {
    var requestBody =
        new ApiUpdateWorkspaceRequestBody()
            .description(newDescription)
            .displayName(newDisplayName)
            .userFacingId(newUserFacingId);
    return objectMapper.writeValueAsString(requestBody);
  }

  private ApiWorkspaceDescription getWorkspaceDescription(UUID id) throws Exception {
    String WorkspaceGetResponse =
        mockMvc
            .perform(
                addAuth(get(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, id)), USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    ApiWorkspaceDescription resultWorkspace =
        objectMapper.readValue(WorkspaceGetResponse, ApiWorkspaceDescription.class);

    return resultWorkspace;
  }

  /**
   * Similar to getWorkspaceDescription, but call the ListWorkspaces endpoint instead of the
   * GetWorkspace endpoint.
   */
  private ApiWorkspaceDescription getWorkspaceDescriptionFromList(UUID id) throws Exception {
    String serializedResponse =
        mockMvc
            .perform(addJsonContentType(addAuth(get(WORKSPACES_V1_PATH), USER_REQUEST)))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    ApiWorkspaceDescriptionList workspaceDescriptionList =
        objectMapper.readValue(serializedResponse, ApiWorkspaceDescriptionList.class);
    return workspaceDescriptionList.getWorkspaces().stream()
        .filter(w -> w.getId().equals(id))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("workspace " + id + "not found in list!"));
  }

  private static ApiTpsPaoGetResult emptyWorkspacePao() {
    return new ApiTpsPaoGetResult()
        .component(ApiTpsComponent.WSM)
        .objectType(ApiTpsObjectType.WORKSPACE)
        .objectId(UUID.randomUUID())
        .sourcesObjectIds(Collections.emptyList())
        .attributes(new ApiTpsPolicyInputs())
        .effectiveAttributes(new ApiTpsPolicyInputs());
  }
}
