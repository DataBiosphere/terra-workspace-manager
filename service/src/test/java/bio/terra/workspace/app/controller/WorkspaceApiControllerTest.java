package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_SPEND_PROFILE_NAME;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.SHORT_DESCRIPTION_PROPERTY;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.TYPE_PROPERTY;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.VERSION_PROPERTY;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.WORKSPACE_NAME;
import static bio.terra.workspace.common.mocks.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.mocks.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.mocks.MockMvcUtils.addJsonContentType;
import static bio.terra.workspace.common.mocks.MockWorkspaceV1Api.WORKSPACES_V1_CREATE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.policy.model.TpsComponent;
import bio.terra.policy.model.TpsObjectType;
import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsPolicyInput;
import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.policy.model.TpsPolicyPair;
import bio.terra.workspace.common.BaseUnitTestMockDataRepoService;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.common.mocks.MockDataRepoApi;
import bio.terra.workspace.common.mocks.MockMvcUtils;
import bio.terra.workspace.common.mocks.MockWorkspaceV1Api;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.db.WorkspaceDao;
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
import bio.terra.workspace.generated.model.ApiWsmPolicyUpdateResult;
import bio.terra.workspace.service.iam.model.AccessibleWorkspace;
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
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

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

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired MockWorkspaceV1Api mockWorkspaceV1Api;
  @Autowired MockDataRepoApi mockDataRepoApi;
  @Autowired ObjectMapper objectMapper;
  @Autowired WorkspaceActivityLogService workspaceActivityLogService;
  @Autowired JobService jobService;
  @Autowired WorkspaceDao workspaceDao;

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
  }

  @Test
  public void createWorkspace_duplicateUuid_throws400() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    mockWorkspaceV1Api.createWorkspaceWithoutCloudContextExpectError(
        USER_REQUEST, workspaceId, /* stageModel= */ null, /* policies= */ null, HttpStatus.SC_OK);
    mockWorkspaceV1Api.createWorkspaceWithoutCloudContextExpectError(
        USER_REQUEST,
        workspaceId,
        /* stageModel= */ null,
        /* policies= */ null,
        HttpStatus.SC_BAD_REQUEST);
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
    ApiCreatedWorkspace workspace =
        mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST);

    ApiWorkspaceDescription getWorkspace =
        mockWorkspaceV1Api.getWorkspace(USER_REQUEST, workspace.getId());
    assertEquals(WORKSPACE_NAME, getWorkspace.getDisplayName());
    assertEquals(
        WorkspaceFixtures.getUserFacingId(workspace.getId()), getWorkspace.getUserFacingId());
    assertEquals(ApiWorkspaceStageModel.MC_WORKSPACE, getWorkspace.getStage());
    assertEquals(USER_REQUEST.getEmail(), getWorkspace.getCreatedBy());
    assertNotNull(getWorkspace.getCreatedDate());
    assertEquals(USER_REQUEST.getEmail(), getWorkspace.getLastUpdatedBy());
    assertNotNull(getWorkspace.getLastUpdatedDate());
  }

  @Test
  public void updateWorkspace() throws Exception {
    ApiCreatedWorkspace workspace =
        mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST);

    // Update workspace
    String newUserFacingId = TestUtils.appendRandomNumber("new-ufid");
    String newDisplayName = "new workspace display name";
    String newDescription = "new description for the workspace";
    ApiWorkspaceDescription updatedWorkspace =
        mockWorkspaceV1Api.updateWorkspace(
            USER_REQUEST, workspace.getId(), newUserFacingId, newDisplayName, newDescription);

    // Assert updated workspace
    assertWorkspace(
        updatedWorkspace,
        newUserFacingId,
        newDisplayName,
        newDescription,
        /* expectedCreatedByEmail= */ USER_REQUEST.getEmail(),
        /* expectedLastUpdatedByEmail= */ USER_REQUEST.getEmail());

    // As second user, update only description
    String secondUserEmail = "foo@gmail.com";
    UserStatusInfo newUser = new UserStatusInfo().userEmail(secondUserEmail).userSubjectId("foo");
    when(mockSamService().getUserStatusInfo(any())).thenReturn(newUser);
    String secondNewDescription = "This is yet another description";
    ApiWorkspaceDescription secondUpdatedWorkspace =
        mockWorkspaceV1Api.updateWorkspace(
            USER_REQUEST,
            workspace.getId(),
            /* newUserFacingId= */ null,
            /* newDisplayName= */ null,
            secondNewDescription);

    // Assert description is updated, while ufId and displayName are the same
    assertWorkspace(
        secondUpdatedWorkspace,
        newUserFacingId,
        newDisplayName,
        secondNewDescription,
        /* expectedCreatedByEmail= */ USER_REQUEST.getEmail(),
        /* expectedLastUpdatedByEmail= */ secondUserEmail);

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
    UUID workspaceId = mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    // Delete terra-type, userkey properties
    mockWorkspaceV1Api.deleteWorkspaceProperties(
        USER_REQUEST, workspaceId, List.of(Properties.TYPE, "userkey"));

    // Assert remaining 2 properties
    ApiWorkspaceDescription gotWorkspace =
        mockWorkspaceV1Api.getWorkspace(USER_REQUEST, workspaceId);
    assertProperties(
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
    UUID workspaceId = mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    // Change userkey value to uservalue2. Add new property foo=bar.
    ApiProperty newUserProperty = new ApiProperty().key("userkey").value("uservalue2");
    ApiProperty fooProperty = new ApiProperty().key("foo").value("bar");
    mockWorkspaceV1Api.updateWorkspaceProperties(
        USER_REQUEST, workspaceId, List.of(newUserProperty, fooProperty));

    // Assert 5 properties.
    ApiWorkspaceDescription gotWorkspace =
        mockWorkspaceV1Api.getWorkspace(USER_REQUEST, workspaceId);
    assertProperties(
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

    UUID workspaceId = mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiWorkspaceDescription sourceWorkspace =
        mockWorkspaceV1Api.getWorkspace(USER_REQUEST, workspaceId);

    ApiCloneWorkspaceResult cloneWorkspace =
        mockWorkspaceV1Api.cloneWorkspace(
            USER_REQUEST, workspaceId, DEFAULT_SPEND_PROFILE_NAME, null, null);
    jobService.waitForJob(cloneWorkspace.getJobReport().getId());

    UUID destinationWorkspaceId = cloneWorkspace.getWorkspace().getDestinationWorkspaceId();
    ApiWorkspaceDescription destinationWorkspace =
        mockWorkspaceV1Api.getWorkspace(USER_REQUEST, destinationWorkspaceId);

    assertEquals(sourceWorkspace.getProperties(), destinationWorkspace.getProperties());
    assertEquals(
        sourceWorkspace.getDisplayName() + " (Copy)", destinationWorkspace.getDisplayName());

    // TODO(PF-2314): Change to call API. We don't expose this in API yet, so read from db
    mockMvcUtils.assertLatestActivityLogChangeDetails(
        destinationWorkspaceId,
        USER_REQUEST.getEmail(),
        USER_REQUEST.getSubjectId(),
        OperationType.CLONE,
        sourceWorkspace.getId().toString(),
        ActivityLogChangedTarget.WORKSPACE);
  }

  @Test
  public void cloneWorkspace_rawls() throws Exception {
    UUID sourceWorkspaceId =
        mockWorkspaceV1Api
            .createWorkspaceWithoutCloudContext(
                USER_REQUEST, ApiWorkspaceStageModel.RAWLS_WORKSPACE)
            .getId();

    when(mockTpsApiDispatch().linkPao(any(), any(), any()))
        .thenReturn(new TpsPaoUpdateResult().resultingPao(emptyWorkspacePao()).updateApplied(true));

    // Create some data repo references
    ApiDataRepoSnapshotResource snap1 =
        mockDataRepoApi.createReferencedDataRepoSnapshot(
            USER_REQUEST,
            sourceWorkspaceId,
            ApiCloningInstructionsEnum.REFERENCE,
            "snap1-resource-name",
            "snap1-instance-name",
            UUID.randomUUID().toString());
    ApiDataRepoSnapshotResource snap2 =
        mockDataRepoApi.createReferencedDataRepoSnapshot(
            USER_REQUEST,
            sourceWorkspaceId,
            ApiCloningInstructionsEnum.REFERENCE,
            "snap2-resource-name",
            "snap2-instance-name",
            UUID.randomUUID().toString());
    ApiDataRepoSnapshotResource snap3 =
        mockDataRepoApi.createReferencedDataRepoSnapshot(
            USER_REQUEST,
            sourceWorkspaceId,
            ApiCloningInstructionsEnum.NOTHING,
            "snap3-resource-name",
            "snap3-instance-name",
            UUID.randomUUID().toString());

    // Clone the rawls workspace into a destination rawls workspace
    // This relies on the mocked Sam check
    UUID destinationWorkspaceId = UUID.randomUUID();

    ApiCloneWorkspaceResult cloneWorkspace =
        mockWorkspaceV1Api.cloneWorkspace(
            USER_REQUEST,
            sourceWorkspaceId,
            DEFAULT_SPEND_PROFILE_NAME,
            null,
            destinationWorkspaceId);

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
    for (ApiResourceCloneDetails cloneDetail : cloneDetails) {
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
    ApiCreatedWorkspace workspace =
        mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST);

    TpsPaoGetResult getPolicyResult =
        emptyWorkspacePao()
            .objectId(workspace.getId())
            .attributes(new TpsPolicyInputs().addInputsItem(TPS_GROUP_POLICY))
            .effectiveAttributes(new TpsPolicyInputs().addInputsItem(TPS_GROUP_POLICY));
    when(mockTpsApiDispatch().getPao(eq(workspace.getId()))).thenReturn(getPolicyResult);
    when(mockTpsApiDispatch().getOrCreatePao(eq(workspace.getId()), any(), any()))
        .thenReturn(getPolicyResult);

    ApiWorkspaceDescription gotWorkspace =
        mockWorkspaceV1Api.getWorkspace(USER_REQUEST, workspace.getId());
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
    ApiCreatedWorkspace workspace =
        mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST);

    ApiWorkspaceDescription gotWorkspace =
        mockWorkspaceV1Api.getWorkspace(USER_REQUEST, workspace.getId());
    assertEquals(0, gotWorkspace.getPolicies().size());
  }

  @Test
  public void listWorkspace_includesPolicy() throws Exception {
    // No need to actually pass policy inputs because TPS is mocked.
    ApiCreatedWorkspace workspace =
        mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST);
    ApiCreatedWorkspace noPolicyWorkspace =
        mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST);
    List<String> missingAuthDomains =
        List.of(TPS_GROUP_POLICY.getAdditionalData().get(0).getValue());
    when(mockSamService().listWorkspaceIdsAndHighestRoles(any(), any()))
        .thenReturn(
            ImmutableMap.of(
                workspace.getId(),
                new AccessibleWorkspace(workspace.getId(), WsmIamRole.OWNER, missingAuthDomains),
                noPolicyWorkspace.getId(),
                new AccessibleWorkspace(
                    noPolicyWorkspace.getId(), WsmIamRole.OWNER, Collections.emptyList())));
    TpsPaoGetResult getPolicyResult =
        new TpsPaoGetResult()
            .attributes(new TpsPolicyInputs().addInputsItem(TPS_GROUP_POLICY))
            .effectiveAttributes(new TpsPolicyInputs().addInputsItem(TPS_GROUP_POLICY))
            .component(TpsComponent.WSM)
            .objectType(TpsObjectType.WORKSPACE)
            .objectId(workspace.getId())
            .sourcesObjectIds(Collections.emptyList());
    // Return a policy object for the first workspace
    when(mockTpsApiDispatch().listPaos(argThat(l -> l.contains(workspace.getId()))))
        .thenReturn(List.of(getPolicyResult));

    List<ApiWorkspaceDescription> workspaces = listWorkspaces();

    List<ApiWorkspaceDescription> workspaceDescriptions =
        workspaces.stream()
            .filter(
                w ->
                    w.getId().equals(workspace.getId())
                        || w.getId().equals(noPolicyWorkspace.getId()))
            .toList();
    assertEquals(2, workspaceDescriptions.size());
    ApiWorkspaceDescription gotWorkspace =
        workspaceDescriptions.stream()
            .filter(w -> w.getId().equals(workspace.getId()))
            .findAny()
            .get();
    assertEquals(missingAuthDomains, gotWorkspace.getMissingAuthDomains());
    assertEquals(1, gotWorkspace.getPolicies().size());
    // The workspace polices from the REST API are in "ApiTps*" form.
    // So we have to convert from TPS form to API form to compare.
    assertEquals(
        TpsApiConversionUtils.apiFromTpsPolicyInput(TPS_GROUP_POLICY),
        gotWorkspace.getPolicies().get(0));
    ApiWorkspaceDescription gotNoPolicyWorkspace =
        workspaceDescriptions.stream()
            .filter(w -> w.getId().equals(noPolicyWorkspace.getId()))
            .findAny()
            .get();
    assertEquals(0, gotNoPolicyWorkspace.getPolicies().size());
    assertTrue(gotNoPolicyWorkspace.getMissingAuthDomains().isEmpty());
  }

  @Test
  public void listWorkspace_tpsDisabled_excludesPolicy() throws Exception {
    when(mockFeatureConfiguration().isTpsEnabled()).thenReturn(false);
    ApiCreatedWorkspace workspace =
        mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST);
    when(mockSamService().listWorkspaceIdsAndHighestRoles(any(), any()))
        .thenReturn(
            ImmutableMap.of(
                workspace.getId(),
                new AccessibleWorkspace(
                    workspace.getId(), WsmIamRole.OWNER, Collections.emptyList())));

    ApiWorkspaceDescription gotWorkspace =
        mockWorkspaceV1Api.getWorkspace(USER_REQUEST, workspace.getId());
    assertEquals(0, gotWorkspace.getPolicies().size());
  }

  @Test
  public void updatePolicies_tpsEnabledAndPolicyUpdated_log() throws Exception {
    ApiCreatedWorkspace workspace =
        mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST);
    when(mockTpsApiDispatch().updatePao(eq(workspace.getId()), any(), any(), any()))
        .thenReturn(new TpsPaoUpdateResult().updateApplied(true));
    ActivityLogChangeDetails lastChangeDetails =
        workspaceActivityLogService.getLastUpdatedDetails(workspace.getId()).get();
    OffsetDateTime lastChangedDate = lastChangeDetails.changeDate();
    assertEquals(OperationType.CREATE, lastChangeDetails.operationType());

    ApiWsmPolicyUpdateResult result =
        mockWorkspaceV1Api.updatePolicies(USER_REQUEST, workspace.getId());
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
    ApiCreatedWorkspace workspace =
        mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST);
    when(mockTpsApiDispatch().updatePao(eq(workspace.getId()), any(), any(), any()))
        .thenReturn(new TpsPaoUpdateResult().updateApplied(false));
    ActivityLogChangeDetails lastChangeDetails =
        workspaceActivityLogService.getLastUpdatedDetails(workspace.getId()).get();
    assertEquals(OperationType.CREATE, lastChangeDetails.operationType());

    ApiWsmPolicyUpdateResult result =
        mockWorkspaceV1Api.updatePolicies(USER_REQUEST, workspace.getId());
    assertFalse(result.isUpdateApplied());
    assertEquals(
        lastChangeDetails,
        workspaceActivityLogService.getLastUpdatedDetails(workspace.getId()).get());
  }

  @Test
  void listValidRegions() throws Exception {
    List<String> expectedRegions = List.of("region1", "region2");
    ApiCreatedWorkspace workspace =
        mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST);
    when(mockTpsApiDispatch().listValidRegions(eq(workspace.getId()), any()))
        .thenReturn(expectedRegions);

    ApiRegions result = mockWorkspaceV1Api.listValidRegions(USER_REQUEST, workspace.getId(), "GCP");
    assertEquals(expectedRegions, result);
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

    return mockWorkspaceV1Api.createWorkspaceWithoutCloudContextExpectError(
        USER_REQUEST,
        /* workspaceId= */ UUID.randomUUID(),
        ApiWorkspaceStageModel.RAWLS_WORKSPACE,
        policyInputs,
        expectedCode);
  }

  /**
   * Similar to getWorkspaceDescription, but call the ListWorkspaces endpoint instead of the
   * GetWorkspace endpoint.
   */
  private List<ApiWorkspaceDescription> listWorkspaces() throws Exception {
    String serializedResponse =
        mockMvc
            .perform(addJsonContentType(addAuth(get(WORKSPACES_V1_CREATE), USER_REQUEST)))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    ApiWorkspaceDescriptionList workspaceDescriptionList =
        objectMapper.readValue(serializedResponse, ApiWorkspaceDescriptionList.class);
    return workspaceDescriptionList.getWorkspaces();
  }

  public static void assertWorkspace(
      ApiWorkspaceDescription actualWorkspace,
      String expectedUserFacingId,
      String expectedDisplayName,
      String expectedDescription,
      String expectedCreatedByEmail,
      String expectedLastUpdatedByEmail) {
    assertEquals(expectedUserFacingId, actualWorkspace.getUserFacingId());
    assertEquals(expectedDisplayName, actualWorkspace.getDisplayName());
    assertEquals(expectedDescription, actualWorkspace.getDescription());
    OffsetDateTime lastUpdatedDate = actualWorkspace.getLastUpdatedDate();
    assertNotNull(lastUpdatedDate);
    OffsetDateTime createdDate = actualWorkspace.getCreatedDate();
    assertNotNull(createdDate);
    assertTrue(lastUpdatedDate.isAfter(createdDate));
    assertEquals(expectedCreatedByEmail, actualWorkspace.getCreatedBy());
    assertEquals(expectedLastUpdatedByEmail, actualWorkspace.getLastUpdatedBy());
  }

  private static void assertProperties(List<ApiProperty> expected, List<ApiProperty> actual) {
    assertThat(expected, containsInAnyOrder(actual.toArray()));
  }
}
