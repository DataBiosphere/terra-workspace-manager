package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.SHORT_DESCRIPTION_PROPERTY;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.TYPE_PROPERTY;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.VERSION_PROPERTY;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.WORKSPACE_NAME;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.getUserFacingId;
import static bio.terra.workspace.common.utils.MockMvcUtils.UPDATE_WORKSPACES_V1_POLICIES_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.WORKSPACES_V1_LIST_VALID_REGIONS_PATH_FORMAT;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.policy.model.TpsComponent;
import bio.terra.policy.model.TpsObjectType;
import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsPolicyInput;
import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.policy.model.TpsPolicyPair;
import bio.terra.workspace.common.BaseUnitTestMockDataRepoService;
import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.generated.model.ApiCloneResourceResult;
import bio.terra.workspace.generated.model.ApiCloneWorkspaceResult;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotResource;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiRegions;
import bio.terra.workspace.generated.model.ApiResourceCloneDetails;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.generated.model.ApiWorkspaceDescriptionList;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.generated.model.ApiWsmPolicyInput;
import bio.terra.workspace.generated.model.ApiWsmPolicyInputs;
import bio.terra.workspace.generated.model.ApiWsmPolicyPair;
import bio.terra.workspace.generated.model.ApiWsmPolicyUpdateMode;
import bio.terra.workspace.generated.model.ApiWsmPolicyUpdateRequest;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamResource;
import bio.terra.workspace.service.iam.model.SamConstants.SamSpendProfileAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.policy.TpsApiConversionUtils;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants.Properties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
 *
 * <p>It can be confusing looking at the Tps types. The "Tps*" types are returned from the Terra
 * Policy Service through the TPS REST API and are mocked in this test. The "ApiTps*" types are part
 * of the WSM REST API and are created and passed as input via MockMvc to the WSM controller. We use
 * that naming on statics to make it clear which is which. This naming is an unfortunate side-effect
 * of supporting the TPS REST API from within the WSM REST API and allowing the TPS objects to be
 * used in the WSM API.
 */
public class WorkspaceApiControllerTest extends BaseUnitTestMockDataRepoService {
  /** A fake group-constraint policy for a workspace. */
  private static final TpsPolicyInput TPS_GROUP_POLICY =
      new TpsPolicyInput()
          .namespace("terra")
          .name("group-constraint")
          .addAdditionalDataItem(new TpsPolicyPair().key("group").value("my_fake_group"));

  private static final String FAKE_SPEND_PROFILE = "fake-spend-profile";

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;
  @Autowired WorkspaceActivityLogService workspaceActivityLogService;
  @Autowired JobService jobService;

  private static TpsPaoGetResult emptyWorkspacePao() {
    return new TpsPaoGetResult()
        .component(TpsComponent.WSM)
        .objectType(TpsObjectType.WORKSPACE)
        .objectId(UUID.randomUUID())
        .sourcesObjectIds(Collections.emptyList())
        .attributes(new TpsPolicyInputs())
        .effectiveAttributes(new TpsPolicyInputs());
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
    when(mockTpsApiDispatch().getPaoIfExists(any())).thenReturn(Optional.of(emptyWorkspacePao()));
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
    String newUserFacingId = TestUtils.appendRandomNumber("new-ufid");
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
    mockMvcUtils.deleteWorkspaceProperties(
        USER_REQUEST, workspaceId, List.of(Properties.TYPE, "userkey"));

    // Assert remaining 2 properties
    ApiWorkspaceDescription gotWorkspace = mockMvcUtils.getWorkspace(USER_REQUEST, workspaceId);
    mockMvcUtils.assertProperties(
        List.of(SHORT_DESCRIPTION_PROPERTY, VERSION_PROPERTY), gotWorkspace.getProperties());

    // TODO(PF-2314): Change to call API. We don't expose this in API yet, so read from db.
    mockMvcUtils.assertLatestActivityLogChangeDetails(
        workspaceId,
        USER_REQUEST.getEmail(),
        USER_REQUEST.getSubjectId(),
        OperationType.DELETE_PROPERTIES,
        workspaceId.toString(),
        ActivityLogChangedTarget.WORKSPACE);
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
    mockMvcUtils.updateWorkspaceProperties(
        USER_REQUEST, workspaceId, List.of(newUserProperty, fooProperty));

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

    // TODO(PF-2314): Change to call API. We don't expose this in API yet, so read from db.
    mockMvcUtils.assertLatestActivityLogChangeDetails(
        workspaceId,
        USER_REQUEST.getEmail(),
        USER_REQUEST.getSubjectId(),
        OperationType.UPDATE_PROPERTIES,
        workspaceId.toString(),
        ActivityLogChangedTarget.WORKSPACE);
  }

  @Test
  public void cloneWorkspace() throws Exception {
    // Disable TPS feature flag for this test
    when(mockFeatureConfiguration().isTpsEnabled()).thenReturn(false);

    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiWorkspaceDescription sourceWorkspace = mockMvcUtils.getWorkspace(USER_REQUEST, workspaceId);

    ApiCloneWorkspaceResult cloneWorkspace =
        mockMvcUtils.cloneWorkspace(USER_REQUEST, workspaceId, FAKE_SPEND_PROFILE, null);
    jobService.waitForJob(cloneWorkspace.getJobReport().getId());

    UUID destinationWorkspaceId = cloneWorkspace.getWorkspace().getDestinationWorkspaceId();
    ApiWorkspaceDescription destinationWorkspace =
        mockMvcUtils.getWorkspace(USER_REQUEST, destinationWorkspaceId);

    assertEquals(sourceWorkspace.getProperties(), destinationWorkspace.getProperties());
    assertEquals(
        sourceWorkspace.getDisplayName() + " (Copy)", destinationWorkspace.getDisplayName());

    // TODO(PF-2314): Change to call API. We don't expose this in API yet, so read from db
    mockMvcUtils.assertLatestActivityLogChangeDetails(
        destinationWorkspaceId,
        USER_REQUEST.getEmail(),
        USER_REQUEST.getSubjectId(),
        OperationType.CLONE,
        destinationWorkspaceId.toString(),
        ActivityLogChangedTarget.WORKSPACE);
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
        mockMvcUtils.createReferencedDataRepoSnapshot(
            USER_REQUEST,
            sourceWorkspaceId,
            ApiCloningInstructionsEnum.REFERENCE,
            "snap1-resource-name",
            "snap1-instance-name",
            "snap1-snapshot");
    ApiDataRepoSnapshotResource snap2 =
        mockMvcUtils.createReferencedDataRepoSnapshot(
            USER_REQUEST,
            sourceWorkspaceId,
            ApiCloningInstructionsEnum.REFERENCE,
            "snap2-resource-name",
            "snap2-instance-name",
            "snap2-snapshot");
    ApiDataRepoSnapshotResource snap3 =
        mockMvcUtils.createReferencedDataRepoSnapshot(
            USER_REQUEST,
            sourceWorkspaceId,
            ApiCloningInstructionsEnum.NOTHING,
            "snap3-resource-name",
            "snap3-instance-name",
            "snap3-snapshot");

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

    TpsPaoGetResult getPolicyResult =
        emptyWorkspacePao()
            .objectId(workspace.getId())
            .attributes(new TpsPolicyInputs().addInputsItem(TPS_GROUP_POLICY))
            .effectiveAttributes(new TpsPolicyInputs().addInputsItem(TPS_GROUP_POLICY));
    when(mockTpsApiDispatch().getPaoIfExists(eq(workspace.getId())))
        .thenReturn(Optional.of(getPolicyResult));

    ApiWorkspaceDescription gotWorkspace =
        mockMvcUtils.getWorkspace(USER_REQUEST, workspace.getId());
    assertEquals(1, gotWorkspace.getPolicies().size());
    // The workspace polices from the REST API are in "ApiTps*" form.
    // So we have to convert from TPS form to API form to compare.
    assertEquals(
        TpsApiConversionUtils.apiFromTpsPolicyInput(TPS_GROUP_POLICY),
        gotWorkspace.getPolicies().get(0));
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

    TpsPaoGetResult getPolicyResult =
        new TpsPaoGetResult()
            .attributes(new TpsPolicyInputs().addInputsItem(TPS_GROUP_POLICY))
            .effectiveAttributes(new TpsPolicyInputs().addInputsItem(TPS_GROUP_POLICY))
            .component(TpsComponent.WSM)
            .objectType(TpsObjectType.WORKSPACE)
            .objectId(workspace.getId())
            .sourcesObjectIds(Collections.emptyList());

    // Return a policy object for the first workspace
    when(mockTpsApiDispatch().getPaoIfExists(eq(workspace.getId())))
        .thenReturn(Optional.of(getPolicyResult));
    // Treat the second workspace like it was created before policy existed and doesn't have a PAO
    when(mockTpsApiDispatch().getPaoIfExists(eq(noPolicyWorkspace.getId())))
        .thenReturn(Optional.empty());

    ApiWorkspaceDescription gotWorkspace =
        mockMvcUtils.getWorkspace(USER_REQUEST, workspace.getId());
    assertEquals(1, gotWorkspace.getPolicies().size());
    // The workspace polices from the REST API are in "ApiTps*" form.
    // So we have to convert from TPS form to API form to compare.
    assertEquals(
        TpsApiConversionUtils.apiFromTpsPolicyInput(TPS_GROUP_POLICY),
        gotWorkspace.getPolicies().get(0));
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
  public void updatePolicies_tpsEnabledAndPolicyUpdated_log() throws Exception {
    ApiCreatedWorkspace workspace = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST);
    when(mockTpsApiDispatch().updatePao(eq(workspace.getId()), any(), any(), any()))
        .thenReturn(new TpsPaoUpdateResult().updateApplied(true));
    ActivityLogChangeDetails lastChangeDetails =
        workspaceActivityLogService.getLastUpdatedDetails(workspace.getId()).get();
    OffsetDateTime lastChangedDate = lastChangeDetails.changeDate();
    assertEquals(OperationType.CREATE, lastChangeDetails.operationType());

    TpsPaoUpdateResult result = updatePolicies(workspace.getId());
    assertTrue(result.isUpdateApplied());
    ActivityLogChangeDetails secondChangeDetails =
        workspaceActivityLogService.getLastUpdatedDetails(workspace.getId()).get();
    assertTrue(lastChangedDate.isBefore(secondChangeDetails.changeDate()));

    // TODO(PF-2314): Change to call API. We don't expose this in API yet, so read from db
    mockMvcUtils.assertLatestActivityLogChangeDetails(
        workspace.getId(),
        USER_REQUEST.getEmail(),
        USER_REQUEST.getSubjectId(),
        OperationType.UPDATE,
        workspace.getId().toString(),
        ActivityLogChangedTarget.POLICIES);
  }

  @Test
  public void updatePolicies_tpsEnabledAndPolicyNotUpdated_notLog() throws Exception {
    ApiCreatedWorkspace workspace = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST);
    when(mockTpsApiDispatch().updatePao(eq(workspace.getId()), any(), any(), any()))
        .thenReturn(new TpsPaoUpdateResult().updateApplied(false));
    ActivityLogChangeDetails lastChangeDetails =
        workspaceActivityLogService.getLastUpdatedDetails(workspace.getId()).get();
    assertEquals(OperationType.CREATE, lastChangeDetails.operationType());

    TpsPaoUpdateResult result = updatePolicies(workspace.getId());
    assertFalse(result.isUpdateApplied());
    assertEquals(
        lastChangeDetails,
        workspaceActivityLogService.getLastUpdatedDetails(workspace.getId()).get());
  }

  @Test
  public void listValidRegions_tpsEnabled() throws Exception {
    ApiCreatedWorkspace workspace = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST);
    List<String> availableRegions = List.of("US", "EU", "asia-northeast3");
    when(mockTpsApiDispatch().listValidRegions(eq(workspace.getId()), eq("gcp")))
        .thenReturn(availableRegions);
    when(mockTpsApiDispatch().listValidRegions(eq(workspace.getId()), eq("azure")))
        .thenReturn(Collections.emptyList());

    ApiRegions result = listValid(workspace.getId(), "gcp");
    ApiRegions empty = listValid(workspace.getId(), "azure");

    assertTrue(result.containsAll(availableRegions));
    assertTrue(empty.isEmpty());
  }

  private ApiErrorReport createRawlsWorkspaceWithPolicyExpectError(int expectedCode)
      throws Exception {
    // Note: this is the WSM REST API form of the policy inputs
    ApiWsmPolicyInputs policyInputs =
        new ApiWsmPolicyInputs()
            .addInputsItem(
                new ApiWsmPolicyInput()
                    .namespace("terra")
                    .name("group-constraint")
                    .addAdditionalDataItem(
                        new ApiWsmPolicyPair().key("group").value("my_fake_group")));

    return mockMvcUtils.createWorkspaceWithoutCloudContextExpectError(
        USER_REQUEST,
        /*workspaceId=*/ UUID.randomUUID(),
        ApiWorkspaceStageModel.RAWLS_WORKSPACE,
        policyInputs,
        expectedCode);
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

  private TpsPaoUpdateResult updatePolicies(UUID workspaceId) throws Exception {
    var serializedResponse =
        updatePoliciesExpect(workspaceId, HttpStatus.SC_OK)
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, TpsPaoUpdateResult.class);
  }

  private ResultActions updatePoliciesExpect(UUID workspaceId, int code) throws Exception {
    ApiWsmPolicyUpdateRequest updateRequest =
        new ApiWsmPolicyUpdateRequest()
            .updateMode(ApiWsmPolicyUpdateMode.ENFORCE_CONFLICT)
            .addAttributes(
                new ApiWsmPolicyInputs()
                    .addInputsItem(
                        new ApiWsmPolicyInput()
                            .namespace("terra")
                            .name("region-constraint")
                            .addAdditionalDataItem(
                                new ApiWsmPolicyPair().key("foo").value("bar"))));
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

  private ApiRegions listValid(UUID workspaceId, String platform) throws Exception {
    var serializedResponse =
        mockMvc
            .perform(
                addAuth(
                    get(String.format(WORKSPACES_V1_LIST_VALID_REGIONS_PATH_FORMAT, workspaceId))
                        .queryParam("platform", platform),
                    USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiRegions.class);
  }
}
