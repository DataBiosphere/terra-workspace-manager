package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.SHORT_DESCRIPTION_PROPERTY;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.TYPE_PROPERTY;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.VERSION_PROPERTY;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.WORKSPACE_NAME;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.getUserFacingId;
import static bio.terra.workspace.common.utils.MockMvcUtils.UPDATE_WORKSPACES_V1_POLICIES_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.UPDATE_WORKSPACES_V1_PROPERTIES_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.WORKSPACES_V1_PATH;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import bio.terra.workspace.common.BaseUnitTestMockDataRepoService;
import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.generated.model.ApiCloneResourceResult;
import bio.terra.workspace.generated.model.ApiCloneWorkspaceResult;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotAttributes;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotResource;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiProperties;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiPropertyKeys;
import bio.terra.workspace.generated.model.ApiReferenceResourceCommonFields;
import bio.terra.workspace.generated.model.ApiResourceCloneDetails;
import bio.terra.workspace.generated.model.ApiTpsComponent;
import bio.terra.workspace.generated.model.ApiTpsObjectType;
import bio.terra.workspace.generated.model.ApiTpsPaoGetResult;
import bio.terra.workspace.generated.model.ApiTpsPaoUpdateRequest;
import bio.terra.workspace.generated.model.ApiTpsPaoUpdateResult;
import bio.terra.workspace.generated.model.ApiTpsPolicyInput;
import bio.terra.workspace.generated.model.ApiTpsPolicyInputs;
import bio.terra.workspace.generated.model.ApiTpsPolicyPair;
import bio.terra.workspace.generated.model.ApiTpsUpdateMode;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.generated.model.ApiWorkspaceDescriptionList;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamResource;
import bio.terra.workspace.service.iam.model.SamConstants.SamSpendProfileAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants.Properties;
import bio.terra.workspace.service.workspace.model.WsmObjectType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * An example of a mockMvc-based unit test for a controller.
 *
 * <p>In general, we would like to move towards testing new endpoints via controller instead of
 * calling services directly like we have in the past. Although this test duplicates coverage
 * currently in WorkspaceServiceTest, it's intended as a proof-of-concept for future mockMvc-based
 * tests.
 */
public class WorkspaceApiControllerTest extends BaseUnitTestMockDataRepoService {
  /** A fake group-constraint policy for a workspace. */
  private static final ApiTpsPolicyInput GROUP_POLICY =
      new ApiTpsPolicyInput()
          .namespace("terra")
          .name("group-constraint")
          .addAdditionalDataItem(new ApiTpsPolicyPair().key("group").value("my_fake_group"));

  private static final String FAKE_SPEND_PROFILE = "fake-spend-profile";

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;
  @Autowired WorkspaceActivityLogService workspaceActivityLogService;

  private static ApiTpsPaoGetResult emptyWorkspacePao() {
    return new ApiTpsPaoGetResult()
        .component(ApiTpsComponent.WSM)
        .objectType(ApiTpsObjectType.WORKSPACE)
        .objectId(UUID.randomUUID())
        .sourcesObjectIds(Collections.emptyList())
        .attributes(new ApiTpsPolicyInputs())
        .effectiveAttributes(new ApiTpsPolicyInputs());
  }

  @BeforeEach
  public void setup() throws InterruptedException {
    when(mockSamService()
            .isAuthorized(
                any(), eq(SamResource.SPEND_PROFILE), any(), eq(SamSpendProfileAction.LINK)))
        .thenReturn(true);
    when(mockSamService().listRequesterRoles(any(), any(), any()))
        .thenReturn(List.of(WsmIamRole.OWNER));
    when(mockSamService().getUserStatusInfo(any()))
        .thenReturn(
            new UserStatusInfo()
                .userEmail(USER_REQUEST.getEmail())
                .userSubjectId(USER_REQUEST.getSubjectId()));
    when(mockSamService().getUserEmailFromSamAndRethrowOnInterrupt(any()))
        .thenReturn(USER_REQUEST.getEmail());
    when(mockSamService().isAuthorized(any(), eq(SamConstants.SamResource.WORKSPACE), any(), any()))
        .thenReturn(true);

    when(mockFeatureConfiguration().isTpsEnabled()).thenReturn(true);
    // We don't need to mock tpsCheck() because Mockito will already do nothing by default.

    // Pretend every workspace has an empty policy. The ID on the PAO will not match the workspace
    // ID, but that doesn't matter for tests which don't care about policy.
    when(mockTpsApiDispatch().getPaoIfExists(any(), any()))
        .thenReturn(Optional.of(emptyWorkspacePao()));
  }

  @Test
  public void createWorkspace_duplicateUuid_throws409() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    mockMvcUtils.createWorkspaceWithoutCloudContextExpectError(
        USER_REQUEST, workspaceId, /*stageModel=*/ null, /*policies=*/ null, HttpStatus.SC_OK);
    mockMvcUtils.createWorkspaceWithoutCloudContextExpectError(
        USER_REQUEST,
        workspaceId,
        /*stageModel=*/ null,
        /*policies=*/ null,
        HttpStatus.SC_CONFLICT);
  }

  @Test
  public void createWorkspace_policyRejectedForRawlsWorkspace_throws400() throws Exception {
    ApiErrorReport errorReport =
        createRawlsWorkspaceWithPolicyExpectError(HttpStatus.SC_BAD_REQUEST);
    assertTrue(
        errorReport.getMessage().contains(ApiWorkspaceStageModel.RAWLS_WORKSPACE.toString()));
  }

  @Test
  public void createWorkspace_policyRejectedIfTpsDisabled_throws501() throws Exception {
    // Disable TPS feature flag for this test only
    when(mockFeatureConfiguration().isTpsEnabled()).thenReturn(false);

    ApiErrorReport errorReport =
        createRawlsWorkspaceWithPolicyExpectError(HttpStatus.SC_NOT_IMPLEMENTED);
    assertTrue(errorReport.getMessage().contains("enabled"));
  }

  @Test
  public void createWorkspace() throws Exception {
    ApiCreatedWorkspace workspace = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST);

    ApiWorkspaceDescription getWorkspace =
        mockMvcUtils.getWorkspace(USER_REQUEST, workspace.getId());
    assertEquals(WORKSPACE_NAME, getWorkspace.getDisplayName());
    assertEquals(getUserFacingId(workspace.getId()), getWorkspace.getUserFacingId());
    assertEquals(ApiWorkspaceStageModel.MC_WORKSPACE, getWorkspace.getStage());
    assertEquals(USER_REQUEST.getEmail(), getWorkspace.getCreatedBy());
    assertNotNull(getWorkspace.getCreatedDate());
    assertEquals(USER_REQUEST.getEmail(), getWorkspace.getLastUpdatedBy());
    assertNotNull(getWorkspace.getLastUpdatedDate());
  }

  @Test
  public void updateWorkspace() throws Exception {
    ApiCreatedWorkspace workspace = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST);

    // Update workspace
    String newUserFacingId = "new-ufid";
    String newDisplayName = "new workspace display name";
    String newDescription = "new description for the workspace";
    ApiWorkspaceDescription updatedWorkspace =
        mockMvcUtils.updateWorkspace(
            USER_REQUEST, workspace.getId(), newUserFacingId, newDisplayName, newDescription);

    // Assert updated workspace
    mockMvcUtils.assertWorkspace(
        updatedWorkspace,
        newUserFacingId,
        newDisplayName,
        newDescription,
        /*expectedCreatedByEmail=*/ USER_REQUEST.getEmail(),
        /*expectedLastUpdatedByEmail=*/ USER_REQUEST.getEmail());

    // As second user, update only description
    String secondUserEmail = "foo@gmail.com";
    var newUser = new UserStatusInfo().userEmail(secondUserEmail).userSubjectId("foo");
    when(mockSamService().getUserStatusInfo(any())).thenReturn(newUser);
    var secondNewDescription = "This is yet another description";
    ApiWorkspaceDescription secondUpdatedWorkspace =
        mockMvcUtils.updateWorkspace(
            USER_REQUEST,
            workspace.getId(),
            /*newUserFacingId=*/ null,
            /*newDisplayName=*/ null,
            secondNewDescription);

    // Assert description is updated, while ufId and displayName are the same
    mockMvcUtils.assertWorkspace(
        secondUpdatedWorkspace,
        newUserFacingId,
        newDisplayName,
        secondNewDescription,
        /*expectedCreatedByEmail=*/ USER_REQUEST.getEmail(),
        /*expectedLastUpdatedByEmail=*/ secondUserEmail);

    // Assert second updated workspace's dates, in relation to first updated workspace
    assertEquals(secondUpdatedWorkspace.getCreatedDate(), updatedWorkspace.getCreatedDate());
    assertTrue(
        secondUpdatedWorkspace.getLastUpdatedDate().isAfter(updatedWorkspace.getLastUpdatedDate()));
  }

  @Test
  public void deleteWorkspaceProperties() throws Exception {
    // Create workspace with 4 properties: terra-type=type,
    // terra-workspace-short-description=short description, terra-workspace-version=version 3
    // userkey=uservalue
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    // Delete terra-type, userkey properties
    deleteWorkspaceProperties(workspaceId, List.of(Properties.TYPE, "userkey"));

    // Assert remaining 2 properties
    ApiWorkspaceDescription gotWorkspace = mockMvcUtils.getWorkspace(USER_REQUEST, workspaceId);
    mockMvcUtils.assertProperties(
        List.of(SHORT_DESCRIPTION_PROPERTY, VERSION_PROPERTY), gotWorkspace.getProperties());
    assertActivityLogChangeDetails(
        workspaceId,
        new ActivityLogChangeDetails(
            null,
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId(),
            OperationType.DELETE_PROPERTIES,
            workspaceId.toString(),
            WsmObjectType.WORKSPACE));
  }

  @Test
  public void updateWorkspaceProperties() throws Exception {
    // Create workspace with 4 properties: terra-type=type,
    // terra-workspace-short-description=short description, terra-workspace-version=version 3
    // userkey=uservalue
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    // Change userkey value to uservalue2. Add new property foo=bar.
    ApiProperty newUserProperty = new ApiProperty().key("userkey").value("uservalue2");
    ApiProperty fooProperty = new ApiProperty().key("foo").value("bar");
    updateWorkspaceProperties(workspaceId, List.of(newUserProperty, fooProperty));

    // Assert 5 properties.
    ApiWorkspaceDescription gotWorkspace = mockMvcUtils.getWorkspace(USER_REQUEST, workspaceId);
    mockMvcUtils.assertProperties(
        List.of(
            SHORT_DESCRIPTION_PROPERTY,
            VERSION_PROPERTY,
            TYPE_PROPERTY,
            newUserProperty,
            fooProperty),
        gotWorkspace.getProperties());
    assertActivityLogChangeDetails(
        workspaceId,
        new ActivityLogChangeDetails(
            null,
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId(),
            OperationType.UPDATE_PROPERTIES,
            workspaceId.toString(),
            WsmObjectType.WORKSPACE));
  }

  @Test
  public void cloneWorkspace() throws Exception {
    // Disable TPS feature flag for this test
    when(mockFeatureConfiguration().isTpsEnabled()).thenReturn(false);

    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiWorkspaceDescription sourceWorkspace = mockMvcUtils.getWorkspace(USER_REQUEST, workspaceId);

    ApiCloneWorkspaceResult cloneWorkspace =
        mockMvcUtils.cloneWorkspace(USER_REQUEST, workspaceId, FAKE_SPEND_PROFILE, null);

    UUID destinationWorkspaceId = cloneWorkspace.getWorkspace().getDestinationWorkspaceId();
    ApiWorkspaceDescription destinationWorkspace =
        mockMvcUtils.getWorkspace(USER_REQUEST, destinationWorkspaceId);

    assertEquals(sourceWorkspace.getProperties(), destinationWorkspace.getProperties());
    assertEquals(
        sourceWorkspace.getDisplayName() + " (Copy)", destinationWorkspace.getDisplayName());
    assertActivityLogChangeDetails(
        destinationWorkspaceId,
        new ActivityLogChangeDetails(
            null,
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId(),
            OperationType.CLONE,
            destinationWorkspaceId.toString(),
            WsmObjectType.WORKSPACE));
  }

  private void assertActivityLogChangeDetails(
      UUID destinationWorkspaceId, ActivityLogChangeDetails expectedDetails) {
    var changeDetails =
        workspaceActivityLogService.getLastUpdatedDetails(destinationWorkspaceId).get();
    assertEquals(expectedDetails.changeSubjectId(), changeDetails.changeSubjectId());
    assertEquals(expectedDetails.operationType(), changeDetails.operationType());
    assertEquals(expectedDetails.actorEmail(), changeDetails.actorEmail());
    assertEquals(expectedDetails.actorSubjectId(), changeDetails.actorSubjectId());
    assertNotNull(changeDetails.changeDate());
  }

  @Test
  public void cloneWorkspace_rawls() throws Exception {
    UUID sourceWorkspaceId =
        mockMvcUtils
            .createWorkspaceWithoutCloudContext(
                USER_REQUEST, ApiWorkspaceStageModel.RAWLS_WORKSPACE)
            .getId();

    // Create some data repo references
    ApiDataRepoSnapshotResource snap1 =
        mockMvcUtils.createDataRepoSnapshotReference(USER_REQUEST, sourceWorkspaceId);
    ApiDataRepoSnapshotResource snap2 =
        mockMvcUtils.createDataRepoSnapshotReference(USER_REQUEST, sourceWorkspaceId);
    ApiDataRepoSnapshotResource snap3 =
        mockMvcUtils.createDataRepoSnapshotReference(
            USER_REQUEST,
            sourceWorkspaceId,
            new ApiCreateDataRepoSnapshotReferenceRequestBody()
                .metadata(
                    new ApiReferenceResourceCommonFields()
                        .cloningInstructions(ApiCloningInstructionsEnum.NOTHING)
                        .description("description")
                        .name(RandomStringUtils.randomAlphabetic(10)))
                .snapshot(
                    new ApiDataRepoSnapshotAttributes()
                        .instanceName("terra")
                        .snapshot("polaroid")));

    // Clone the rawls workspace into a destination rawls workspace
    // This relies on the mocked Sam check
    UUID destinationWorkspaceId = UUID.randomUUID();

    ApiCloneWorkspaceResult cloneWorkspace =
        mockMvcUtils.cloneWorkspace(
            USER_REQUEST, sourceWorkspaceId, FAKE_SPEND_PROFILE, destinationWorkspaceId);

    List<ApiResourceCloneDetails> cloneDetails = cloneWorkspace.getWorkspace().getResources();
    assertEquals(3, cloneDetails.size());
    List<UUID> cloneIdList =
        cloneDetails.stream().map(ApiResourceCloneDetails::getSourceResourceId).toList();
    assertThat(
        cloneIdList,
        containsInAnyOrder(
            snap1.getMetadata().getResourceId(),
            snap2.getMetadata().getResourceId(),
            snap3.getMetadata().getResourceId()));
    for (var cloneDetail : cloneDetails) {
      if (cloneDetail.getSourceResourceId().equals(snap3.getMetadata().getResourceId())) {
        assertEquals(ApiCloneResourceResult.SKIPPED, cloneDetail.getResult());
        assertNull(cloneDetail.getDestinationResourceId());
      } else {
        assertEquals(ApiCloneResourceResult.SUCCEEDED, cloneDetail.getResult());
        assertNotNull(cloneDetail.getDestinationResourceId());
      }
    }
  }

  @Test
  public void getWorkspace_includesPolicy() throws Exception {
    // No need to actually pass policy inputs because TPS is mocked.
    ApiCreatedWorkspace workspace = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST);

    ApiTpsPaoGetResult getPolicyResult =
        emptyWorkspacePao()
            .objectId(workspace.getId())
            .attributes(new ApiTpsPolicyInputs().addInputsItem(GROUP_POLICY))
            .effectiveAttributes(new ApiTpsPolicyInputs().addInputsItem(GROUP_POLICY));
    when(mockTpsApiDispatch().getPaoIfExists(any(), eq(workspace.getId())))
        .thenReturn(Optional.of(getPolicyResult));

    ApiWorkspaceDescription gotWorkspace =
        mockMvcUtils.getWorkspace(USER_REQUEST, workspace.getId());
    assertEquals(1, gotWorkspace.getPolicies().size());
    assertEquals(GROUP_POLICY, gotWorkspace.getPolicies().get(0));
  }

  @Test
  public void getWorkspace_tpsDisabled_excludesPolicy() throws Exception {
    when(mockFeatureConfiguration().isTpsEnabled()).thenReturn(false);
    ApiCreatedWorkspace workspace = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST);

    ApiWorkspaceDescription gotWorkspace =
        mockMvcUtils.getWorkspace(USER_REQUEST, workspace.getId());
    assertNull(gotWorkspace.getPolicies());
  }

  @Test
  public void listWorkspace_includesPolicy() throws Exception {
    // No need to actually pass policy inputs because TPS is mocked.
    ApiCreatedWorkspace workspace = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST);
    ApiCreatedWorkspace noPolicyWorkspace =
        mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST);
    when(mockSamService().listWorkspaceIdsAndHighestRoles(any(), any()))
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
    when(mockTpsApiDispatch().getPaoIfExists(any(), eq(workspace.getId())))
        .thenReturn(Optional.of(getPolicyResult));
    // Treat the second workspace like it was created before policy existed and doesn't have a PAO
    when(mockTpsApiDispatch().getPaoIfExists(any(), eq(noPolicyWorkspace.getId())))
        .thenReturn(Optional.empty());

    ApiWorkspaceDescription gotWorkspace =
        mockMvcUtils.getWorkspace(USER_REQUEST, workspace.getId());
    assertEquals(1, gotWorkspace.getPolicies().size());
    assertEquals(GROUP_POLICY, gotWorkspace.getPolicies().get(0));
    ApiWorkspaceDescription gotNoPolicyWorkspace =
        getWorkspaceDescriptionFromList(noPolicyWorkspace.getId());
    assertTrue(gotNoPolicyWorkspace.getPolicies().isEmpty());
  }

  @Test
  public void listWorkspace_tpsDisabled_excludesPolicy() throws Exception {
    when(mockFeatureConfiguration().isTpsEnabled()).thenReturn(false);
    ApiCreatedWorkspace workspace = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST);
    when(mockSamService().listWorkspaceIdsAndHighestRoles(any(), any()))
        .thenReturn(ImmutableMap.of(workspace.getId(), WsmIamRole.OWNER));

    ApiWorkspaceDescription gotWorkspace =
        mockMvcUtils.getWorkspace(USER_REQUEST, workspace.getId());
    assertNull(gotWorkspace.getPolicies());
  }

  @Test
  public void updatePolicies_tpsDisabled_throws501() throws Exception {
    when(mockFeatureConfiguration().isTpsEnabled()).thenReturn(false);
    ApiCreatedWorkspace workspace = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST);

    updatePoliciesExpect(workspace.getId(), HttpStatus.SC_NOT_IMPLEMENTED);
  }

  @Test
  public void updatePolicies_tpsEnabledAndPolicyUpdated_log() throws Exception {
    ApiCreatedWorkspace workspace = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST);
    when(mockTpsApiDispatch().updatePao(any(), eq(workspace.getId()), any()))
        .thenReturn(new ApiTpsPaoUpdateResult().updateApplied(true));
    ActivityLogChangeDetails lastChangeDetails =
        workspaceActivityLogService.getLastUpdatedDetails(workspace.getId()).get();
    OffsetDateTime lastChangedDate = lastChangeDetails.changeDate();
    assertEquals(OperationType.CREATE, lastChangeDetails.operationType());

    ApiTpsPaoUpdateResult result = updatePolicies(workspace.getId());
    assertTrue(result.isUpdateApplied());
    ActivityLogChangeDetails secondChangeDetails =
        workspaceActivityLogService.getLastUpdatedDetails(workspace.getId()).get();
    assertTrue(lastChangedDate.isBefore(secondChangeDetails.changeDate()));
    assertActivityLogChangeDetails(
        workspace.getId(),
        new ActivityLogChangeDetails(
            /*changeDate=*/ null,
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId(),
            OperationType.UPDATE,
            workspace.getId().toString(),
            WsmObjectType.WORKSPACE));
  }

  @Test
  public void updatePolicies_tpsEnabledAndPolicyNotUpdated_notLog() throws Exception {
    ApiCreatedWorkspace workspace = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST);
    when(mockTpsApiDispatch().updatePao(any(), eq(workspace.getId()), any()))
        .thenReturn(new ApiTpsPaoUpdateResult().updateApplied(false));
    ActivityLogChangeDetails lastChangeDetails =
        workspaceActivityLogService.getLastUpdatedDetails(workspace.getId()).get();
    assertEquals(OperationType.CREATE, lastChangeDetails.operationType());

    ApiTpsPaoUpdateResult result = updatePolicies(workspace.getId());
    assertFalse(result.isUpdateApplied());
    assertEquals(
        lastChangeDetails,
        workspaceActivityLogService.getLastUpdatedDetails(workspace.getId()).get());
  }

  private ApiErrorReport createRawlsWorkspaceWithPolicyExpectError(int expectedCode)
      throws Exception {
    ApiTpsPolicyInputs policyInputs =
        new ApiTpsPolicyInputs()
            .addInputsItem(new ApiTpsPolicyInput().namespace("terra").name("group-constraint"));
    return mockMvcUtils.createWorkspaceWithoutCloudContextExpectError(
        USER_REQUEST,
        /*workspaceId=*/ UUID.randomUUID(),
        ApiWorkspaceStageModel.RAWLS_WORKSPACE,
        policyInputs,
        expectedCode);
  }

  private void updateWorkspaceProperties(UUID workspaceId, List<ApiProperty> properties)
      throws Exception {
    ApiProperties apiProperties = new ApiProperties();
    apiProperties.addAll(properties);
    mockMvc
        .perform(
            addAuth(
                post(String.format(UPDATE_WORKSPACES_V1_PROPERTIES_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(objectMapper.writeValueAsString(apiProperties)),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_NO_CONTENT));
  }

  private void deleteWorkspaceProperties(UUID workspaceId, List<String> propertyKeys)
      throws Exception {
    ApiPropertyKeys apiPropertyKeys = new ApiPropertyKeys();
    apiPropertyKeys.addAll(propertyKeys);
    mockMvc
        .perform(
            addAuth(
                patch(String.format(UPDATE_WORKSPACES_V1_PROPERTIES_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(objectMapper.writeValueAsString(apiPropertyKeys)),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_NO_CONTENT));
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

  private ApiTpsPaoUpdateResult updatePolicies(UUID workspaceId) throws Exception {
    var serializedResponse =
        updatePoliciesExpect(workspaceId, HttpStatus.SC_OK)
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiTpsPaoUpdateResult.class);
  }

  private ResultActions updatePoliciesExpect(UUID workspaceId, int code) throws Exception {
    ApiTpsPaoUpdateRequest updateRequest =
        new ApiTpsPaoUpdateRequest()
            .updateMode(ApiTpsUpdateMode.ENFORCE_CONFLICT)
            .addAttributes(
                new ApiTpsPolicyInputs()
                    .addInputsItem(
                        new ApiTpsPolicyInput()
                            .namespace("terra")
                            .name("region-constraint")
                            .addAdditionalDataItem(
                                new ApiTpsPolicyPair().key("foo").value("bar"))));
    return mockMvc
        .perform(
            addAuth(
                patch(String.format(UPDATE_WORKSPACES_V1_POLICIES_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(objectMapper.writeValueAsString(updateRequest)),
                USER_REQUEST))
        .andExpect(status().is(code));
  }
}
