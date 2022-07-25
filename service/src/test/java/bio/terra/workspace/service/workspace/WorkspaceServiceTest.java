package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.utils.MockMvcUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.exception.ErrorReportException;
import bio.terra.common.exception.ForbiddenException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.common.sam.exception.SamExceptionFactory;
import bio.terra.common.sam.exception.SamInternalServerErrorException;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceActivityLogDao;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.generated.model.ApiCloneResourceResult;
import bio.terra.workspace.generated.model.ApiClonedWorkspace;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreateCloudContextRequest;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycle;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRule;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleAction;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleActionType;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleCondition;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiResourceCloneDetails;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.SamConstants.SamResource;
import bio.terra.workspace.service.iam.model.SamConstants.SamSpendProfileAction;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.JobResultOrException;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.DuplicateUserFacingIdException;
import bio.terra.workspace.service.workspace.exceptions.DuplicateWorkspaceException;
import bio.terra.workspace.service.workspace.flight.CheckSamWorkspaceAuthzStep;
import bio.terra.workspace.service.workspace.flight.CreateWorkspaceAuthzStep;
import bio.terra.workspace.service.workspace.flight.CreateWorkspaceStep;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceAndHighestRole;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class WorkspaceServiceTest extends BaseConnectedTest {

  /** A fake authenticated user request. */
  private static final AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest()
          .token(Optional.of("fake-token"))
          .email("fake@email.com")
          .subjectId("fakeID123");

  public static final String SPEND_PROFILE_ID = "wm-default-spend-profile";

  @MockBean private DataRepoService mockDataRepoService;
  /** Mock SamService does nothing for all calls that would throw if unauthorized. */
  @MockBean private SamService mockSamService;

  // Populated because this test is annotated with @AutoConfigureMockMvc
  @Autowired private MockMvc mockMvc;

  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private CrlService crl;
  @Autowired private GcpCloudContextService gcpCloudContextService;
  @Autowired private JobService jobService;
  @Autowired private ReferencedResourceService referenceResourceService;
  @Autowired private ResourceDao resourceDao;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private WorkspaceActivityLogDao workspaceActivityLogDao;

  @BeforeEach
  void setup() throws Exception {
    doReturn(true).when(mockDataRepoService).snapshotReadable(any(), any(), any());
    // By default, allow all spend link calls as authorized. (All other isAuthorized calls return
    // false by Mockito default).
    when(mockSamService.isAuthorized(
            any(), eq(SamResource.SPEND_PROFILE), any(), eq(SamSpendProfileAction.LINK)))
        .thenReturn(true);
    final String policyGroup = "terra-workspace-manager-test-group@googlegroups.com";
    // Return a valid google group for cloud sync, as Google validates groups added to GCP projects.
    when(mockSamService.syncWorkspacePolicy(any(), any(), any())).thenReturn(policyGroup);

    doReturn(policyGroup)
        .when(mockSamService)
        .syncResourcePolicy(
            any(ControlledResource.class),
            any(ControlledResourceIamRole.class),
            any(AuthenticatedUserRequest.class));
    when(mockSamService.getUserStatusInfo(any()))
        .thenReturn(
            new UserStatusInfo()
                .userEmail(USER_REQUEST.getEmail())
                .userSubjectId(USER_REQUEST.getSubjectId()));
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    jobService.setFlightDebugInfoForTest(null);
  }

  @Test
  void listWorkspacesAndHighestRoles_existing() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    Workspace request = defaultRequestBuilder(workspaceId).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    when(mockSamService.listWorkspaceIdsAndHighestRoles(any()))
        .thenReturn(Map.of(workspaceId, WsmIamRole.OWNER));

    List<WorkspaceAndHighestRole> actual =
        workspaceService.listWorkspacesAndHighestRoles(USER_REQUEST, /*offset=*/ 0, /*limit=*/ 10);
    assertEquals(1, actual.size());
    assertEquals(request.getWorkspaceId(), actual.get(0).workspace().getWorkspaceId());
    assertEquals(WsmIamRole.OWNER, actual.get(0).highestRole());
  }

  @Test
  void getWorkspace_existing() {
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    assertEquals(
        request.getWorkspaceId(),
        workspaceService.getWorkspace(request.getWorkspaceId()).getWorkspaceId());
  }

  @Test
  void getWorkspace_missing() {
    assertThrows(
        WorkspaceNotFoundException.class, () -> workspaceService.getWorkspace(UUID.randomUUID()));
  }

  @Test
  void getWorkspace_forbiddenMissing() throws Exception {
    doThrow(new ForbiddenException("forbid!"))
        .when(mockSamService)
        .checkAuthz(any(), any(), any(), any());
    mockMvc
        .perform(
            addAuth(
                get(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, UUID.randomUUID())),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_NOT_FOUND));
  }

  @Test
  void getWorkspace_forbiddenExisting() throws Exception {
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    doThrow(new ForbiddenException("forbid!"))
        .when(mockSamService)
        .checkAuthz(any(), any(), any(), any());
    mockMvc
        .perform(
            addAuth(
                get(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, request.getWorkspaceId())),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_FORBIDDEN));
  }

  @Test
  void getWorkspaceByUserFacingId_existing() {
    String userFacingId = "user-facing-id-getworkspacebyuserfacingid_existing";
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).userFacingId(userFacingId).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    assertEquals(
        request.getWorkspaceId(),
        workspaceService.getWorkspaceByUserFacingId(userFacingId, USER_REQUEST).getWorkspaceId());
  }

  @Test
  void getWorkspaceByUserFacingId_missing() {
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspaceByUserFacingId("missing-workspace", USER_REQUEST));
  }

  @Test
  void getWorkspaceByUserFacingId_forbiddenMissing() throws Exception {
    doThrow(new ForbiddenException("forbid!"))
        .when(mockSamService)
        .checkAuthz(any(), any(), any(), any());
    mockMvc
        .perform(
            addAuth(
                get(String.format(WORKSPACES_V1_BY_UFID_PATH_FORMAT, "missing-workspace")),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_NOT_FOUND));
  }

  @Test
  void getWorkspaceByUserFacingId_forbiddenExisting() throws Exception {
    String userFacingId = "user-facing-id-getworkspacebyuserfacingid_forbiddenexisting";
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).userFacingId(userFacingId).build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    Workspace createdWorkspace = workspaceService.getWorkspace(request.getWorkspaceId());

    doThrow(new ForbiddenException("forbid!"))
        .when(mockSamService)
        .checkAuthz(any(), any(), any(), any());
    mockMvc
        .perform(
            addAuth(
                get(String.format(WORKSPACES_V1_BY_UFID_PATH_FORMAT, userFacingId)), USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_FORBIDDEN));
    assertThrows(
        ForbiddenException.class,
        () -> workspaceService.getWorkspaceByUserFacingId(userFacingId, USER_REQUEST));
  }

  @Test
  void getHighestRole_existing() {
    when(mockSamService.listRequesterRoles(any(), any(), any()))
        .thenReturn(ImmutableList.of(WsmIamRole.OWNER, WsmIamRole.WRITER));

    Workspace request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    assertEquals(
        WsmIamRole.OWNER, workspaceService.getHighestRole(request.getWorkspaceId(), USER_REQUEST));
  }

  @Test
  void testWorkspaceStagePersists() {
    Workspace mcWorkspaceRequest =
        defaultRequestBuilder(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceService.createWorkspace(mcWorkspaceRequest, USER_REQUEST);
    Workspace createdWorkspace = workspaceService.getWorkspace(mcWorkspaceRequest.getWorkspaceId());
    assertEquals(mcWorkspaceRequest.getWorkspaceId(), createdWorkspace.getWorkspaceId());
    assertEquals(WorkspaceStage.MC_WORKSPACE, createdWorkspace.getWorkspaceStage());
  }

  @Test
  void duplicateWorkspaceIdRequestsRejected() {
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    Workspace duplicateWorkspace =
        defaultRequestBuilder(request.getWorkspaceId())
            .description("slightly different workspace")
            .build();
    assertThrows(
        DuplicateWorkspaceException.class,
        () -> workspaceService.createWorkspace(duplicateWorkspace, USER_REQUEST));
  }

  @Test
  void duplicateWorkspaceUserFacingIdRequestsRejected() {
    String userFacingId = "create-workspace-user-facing-id";
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).userFacingId(userFacingId).build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    Workspace duplicateUserFacingId =
        defaultRequestBuilder(UUID.randomUUID()).userFacingId(userFacingId).build();

    DuplicateUserFacingIdException ex =
        assertThrows(
            DuplicateUserFacingIdException.class,
            () -> workspaceService.createWorkspace(duplicateUserFacingId, USER_REQUEST));
    assertEquals(
        String.format("Workspace with ID %s already exists", userFacingId), ex.getMessage());
  }

  @Test
  void duplicateOperationSharesFailureResponse() throws Exception {
    String errorMsg = "fake SAM error message";
    doThrow(SamExceptionFactory.create(errorMsg, new ApiException(("test"))))
        .when(mockSamService)
        .createWorkspaceWithDefaults(any(), any());

    assertThrows(
        ErrorReportException.class,
        () ->
            workspaceService.createWorkspace(
                defaultRequestBuilder(UUID.randomUUID()).build(), USER_REQUEST));
    // This second call shares the above operation ID, and so should return the same exception
    // instead of a more generic internal Stairway exception.
    assertThrows(
        ErrorReportException.class,
        () ->
            workspaceService.createWorkspace(
                defaultRequestBuilder(UUID.randomUUID()).build(), USER_REQUEST));
  }

  @Test
  void testWithSpendProfile() {
    SpendProfileId spendProfileId = new SpendProfileId("foo");
    Workspace request =
        defaultRequestBuilder(UUID.randomUUID()).spendProfileId(spendProfileId).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    Workspace createdWorkspace = workspaceService.getWorkspace(request.getWorkspaceId());
    assertEquals(request.getWorkspaceId(), createdWorkspace.getWorkspaceId());
    assertEquals(spendProfileId, createdWorkspace.getSpendProfileId().orElse(null));
  }

  @Test
  void testWithDisplayNameAndDescription() {
    String name = "My workspace";
    String description = "The greatest workspace";
    Workspace request =
        defaultRequestBuilder(UUID.randomUUID()).displayName(name).description(description).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    Workspace createdWorkspace = workspaceService.getWorkspace(request.getWorkspaceId());
    assertEquals(
        request.getDescription().orElse(null), createdWorkspace.getDescription().orElse(null));
    assertEquals(name, createdWorkspace.getDisplayName().orElse(null));
    assertEquals(description, createdWorkspace.getDescription().orElse(null));
  }

  @Test
  void testUpdateWorkspace() {
    Map<String, String> propertyMap = new HashMap<>();
    propertyMap.put("foo", "bar");
    propertyMap.put("xyzzy", "plohg");
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).properties(propertyMap).build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    UUID workspaceUuid = request.getWorkspaceId();
    var lastUpdateDetails = workspaceActivityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(lastUpdateDetails.isPresent());
    Workspace createdWorkspace = workspaceService.getWorkspace(request.getWorkspaceId());
    assertEquals(request.getWorkspaceId(), createdWorkspace.getWorkspaceId());
    assertEquals("", createdWorkspace.getDisplayName().orElse(null));
    assertEquals("", createdWorkspace.getDescription().orElse(null));

    String userFacingId = "my-user-facing-id";
    String name = "My workspace";
    String description = "The greatest workspace";
    Map<String, String> propertyMap2 = new HashMap<>();
    propertyMap.put("ted", "lasso");
    propertyMap.put("keeley", "jones");

    Workspace updatedWorkspace =
        workspaceService.updateWorkspace(
            workspaceUuid,
            userFacingId,
            name,
            description,
            propertyMap2,
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId());

    var workspaceUpdateChangeDetails = workspaceActivityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(
        lastUpdateDetails
            .get()
            .getChangedDate()
            .isBefore(workspaceUpdateChangeDetails.get().getChangedDate()));
    assertEquals(USER_REQUEST.getEmail(), workspaceUpdateChangeDetails.get().getUserEmail());
    assertEquals(
        USER_REQUEST.getSubjectId(), workspaceUpdateChangeDetails.get().getUserSubjectId());

    assertEquals(userFacingId, updatedWorkspace.getUserFacingId());
    assertTrue(updatedWorkspace.getDisplayName().isPresent());
    assertEquals(name, updatedWorkspace.getDisplayName().get());
    assertTrue(updatedWorkspace.getDescription().isPresent());
    assertEquals(description, updatedWorkspace.getDescription().get());
    assertEquals(propertyMap2, updatedWorkspace.getProperties());

    String otherDescription = "The deprecated workspace";

    Workspace secondUpdatedWorkspace =
        workspaceService.updateWorkspace(
            workspaceUuid,
            null,
            null,
            otherDescription,
            null,
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId());

    var secondUpdateChangeDetails = workspaceActivityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(
        workspaceUpdateChangeDetails
            .get()
            .getChangedDate()
            .isBefore(secondUpdateChangeDetails.get().getChangedDate()));
    assertEquals(USER_REQUEST.getEmail(), secondUpdateChangeDetails.get().getUserEmail());
    assertEquals(USER_REQUEST.getSubjectId(), secondUpdateChangeDetails.get().getUserSubjectId());
    // Since name is null, leave it alone. Description should be updated.
    assertTrue(secondUpdatedWorkspace.getDisplayName().isPresent());
    assertEquals(name, secondUpdatedWorkspace.getDisplayName().get());
    assertTrue(secondUpdatedWorkspace.getDescription().isPresent());
    assertEquals(otherDescription, secondUpdatedWorkspace.getDescription().get());
    assertEquals(propertyMap2, updatedWorkspace.getProperties());

    // Sending through empty strings and an empty map clears the values.
    Map<String, String> propertyMap3 = new HashMap<>();
    Workspace thirdUpdatedWorkspace =
        workspaceService.updateWorkspace(
            workspaceUuid,
            userFacingId,
            "",
            "",
            propertyMap3,
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId());
    var thirdUpdatedDateAfterWorkspaceUpdate =
        workspaceActivityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(
        secondUpdateChangeDetails
            .get()
            .getChangedDate()
            .isBefore(thirdUpdatedDateAfterWorkspaceUpdate.get().getChangedDate()));
    assertTrue(thirdUpdatedWorkspace.getDisplayName().isPresent());
    assertEquals("", thirdUpdatedWorkspace.getDisplayName().get());
    assertTrue(thirdUpdatedWorkspace.getDescription().isPresent());
    assertEquals("", thirdUpdatedWorkspace.getDescription().get());

    // Fail if request doesn't contain any updated fields.
    assertThrows(
        MissingRequiredFieldException.class,
        () ->
            workspaceService.updateWorkspace(
                workspaceUuid,
                null,
                null,
                null,
                null,
                USER_REQUEST.getEmail(),
                USER_REQUEST.getSubjectId()));
    var failedUpdateDate = workspaceActivityLogDao.getLastUpdateDetails(workspaceUuid);
    assertEquals(
        thirdUpdatedDateAfterWorkspaceUpdate.get().getChangedDate(),
        failedUpdateDate.get().getChangedDate());
  }

  @Test
  void testUpdateWorkspaceUserFacingIdAlreadyExistsRejected() {
    // Create one workspace with userFacingId, one without.
    String userFacingId = "update-workspace-user-facing-id";
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).userFacingId(userFacingId).build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    UUID secondWorkspaceUuid = UUID.randomUUID();
    request = defaultRequestBuilder(secondWorkspaceUuid).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    // Try to set second workspace's userFacing to first.
    DuplicateUserFacingIdException ex =
        assertThrows(
            DuplicateUserFacingIdException.class,
            () ->
                workspaceService.updateWorkspace(
                    secondWorkspaceUuid,
                    userFacingId,
                    null,
                    null,
                    null,
                    USER_REQUEST.getEmail(),
                    USER_REQUEST.getSubjectId()));
    assertEquals(
        ex.getMessage(), String.format("Workspace with ID %s already exists", userFacingId));
  }

  @Test
  void testHandlesSamError() throws Exception {
    String apiErrorMsg = "test";
    ErrorReportException testex = new SamInternalServerErrorException(apiErrorMsg);
    doThrow(testex).when(mockSamService).createWorkspaceWithDefaults(any(), any());
    ErrorReportException exception =
        assertThrows(
            SamInternalServerErrorException.class,
            () ->
                workspaceService.createWorkspace(
                    defaultRequestBuilder(UUID.randomUUID()).build(), USER_REQUEST));
    assertEquals(apiErrorMsg, exception.getMessage());
  }

  @Test
  void createAndDeleteWorkspace() {
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    workspaceService.deleteWorkspace(request, USER_REQUEST);
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(request.getWorkspaceId()));
  }

  @Test
  void createMcWorkspaceDoSteps() {
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).build();
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(CreateWorkspaceAuthzStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(CreateWorkspaceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build();
    jobService.setFlightDebugInfoForTest(debugInfo);

    UUID createdId = workspaceService.createWorkspace(request, USER_REQUEST);
    assertEquals(createdId, request.getWorkspaceId());
  }

  @Test
  void createRawlsWorkspaceDoSteps() throws InterruptedException {
    Workspace request =
        defaultRequestBuilder(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .build();
    // Ensure the auth check in CheckSamWorkspaceAuthzStep always succeeds.
    doReturn(true).when(mockSamService).isAuthorized(any(), any(), any(), any());
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        CheckSamWorkspaceAuthzStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(CreateWorkspaceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build();
    jobService.setFlightDebugInfoForTest(debugInfo);

    UUID createdId = workspaceService.createWorkspace(request, USER_REQUEST);
    assertEquals(createdId, request.getWorkspaceId());
  }

  @Test
  void createMcWorkspaceUndoSteps() {
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).build();
    // Retry undo steps once and fail at the end of the flight.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(CreateWorkspaceAuthzStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(CreateWorkspaceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo =
        FlightDebugInfo.newBuilder().undoStepFailures(retrySteps).lastStepFailure(true).build();
    jobService.setFlightDebugInfoForTest(debugInfo);

    // Service methods which wait for a flight to complete will throw an
    // InvalidResultStateException when that flight fails without a cause, which occurs when a
    // flight fails via debugInfo.
    assertThrows(
        InvalidResultStateException.class,
        () -> workspaceService.createWorkspace(request, USER_REQUEST));
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(request.getWorkspaceId()));
  }

  @Test
  void testDeleteWorkspaceProperties() {
    // Create one workspace with properties
    Map<String, String> propertyMap = new HashMap<>();
    propertyMap.put("foo", "bar");
    propertyMap.put("xyzzy", "plohg");

    Workspace request = defaultRequestBuilder(UUID.randomUUID()).properties(propertyMap).build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    UUID workspaceUuid = request.getWorkspaceId();
    var lastUpdatedDate = workspaceActivityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(lastUpdatedDate.isPresent());

    List<String> propertyKeys = new ArrayList<>(Arrays.asList("foo", "foo1"));

    workspaceService.deleteWorkspaceProperties(workspaceUuid, propertyKeys);
    Workspace deletedWorkspace = workspaceService.getWorkspace(workspaceUuid);

    var updatedDateAfterWorkspaceUpdate = workspaceActivityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(lastUpdatedDate.get().isBefore(updatedDateAfterWorkspaceUpdate.get()));
    Map<String, String> expectedPropertyMap = Map.of("xyzzy", "plohg");
    assertEquals(
        expectedPropertyMap,
        deletedWorkspace.getProperties(),
        "Workspace properties update successfully");
  }

  @Test
  void deleteForbiddenMissingWorkspace() throws Exception {
    doThrow(new ForbiddenException("forbid!"))
        .when(mockSamService)
        .checkAuthz(any(), any(), any(), any());
    mockMvc
        .perform(
            addAuth(
                delete(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, UUID.randomUUID())),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_NOT_FOUND));
  }

  @Test
  void deleteForbiddenExistingWorkspace() throws Exception {
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    doThrow(new ForbiddenException("forbid!"))
        .when(mockSamService)
        .checkAuthz(any(), any(), any(), any());

    mockMvc
        .perform(
            addAuth(
                delete(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, request.getWorkspaceId())),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_FORBIDDEN));
  }

  @Test
  void deleteWorkspaceWithDataReference() {
    // First, create a workspace.
    UUID workspaceUuid = UUID.randomUUID();
    Workspace request = defaultRequestBuilder(workspaceUuid).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    // Next, add a data reference to that workspace.
    UUID resourceId = UUID.randomUUID();
    ReferencedDataRepoSnapshotResource snapshot =
        new ReferencedDataRepoSnapshotResource(
            workspaceUuid,
            resourceId,
            "fake_data_reference",
            null,
            CloningInstructions.COPY_NOTHING,
            "fakeinstance",
            "fakesnapshot");
    referenceResourceService.createReferenceResource(snapshot, USER_REQUEST);

    // Validate that the reference exists.
    referenceResourceService.getReferenceResource(workspaceUuid, resourceId);

    // Delete the workspace.
    workspaceService.deleteWorkspace(request, USER_REQUEST);

    // Verify that the workspace was successfully deleted, even though it contained references
    assertThrows(
        WorkspaceNotFoundException.class, () -> workspaceService.getWorkspace(workspaceUuid));

    // Verify that the resource is also deleted
    assertThrows(
        ResourceNotFoundException.class, () -> resourceDao.getResource(workspaceUuid, resourceId));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void deleteWorkspaceWithGoogleContext() throws Exception {
    Workspace request =
        defaultRequestBuilder(UUID.randomUUID())
            .spendProfileId(spendUtils.defaultSpendId())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    String jobId = UUID.randomUUID().toString();
    workspaceService.createGcpCloudContext(request, jobId, USER_REQUEST, "/fake/value");
    jobService.waitForJob(jobId);
    assertNull(jobService.retrieveJobResult(jobId, Object.class).getException());
    Workspace workspace = workspaceService.getWorkspace(request.getWorkspaceId());
    String projectId = gcpCloudContextService.getRequiredGcpProject(workspace.getWorkspaceId());

    // Verify project exists by retrieving it.
    crl.getCloudResourceManagerCow().projects().get(projectId).execute();

    workspaceService.deleteWorkspace(request, USER_REQUEST);

    // Check that project is now being deleted.
    Project project = crl.getCloudResourceManagerCow().projects().get(projectId).execute();
    assertEquals("DELETE_REQUESTED", project.getState());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createGetDeleteGoogleContext() {
    Workspace request =
        defaultRequestBuilder(UUID.randomUUID())
            .spendProfileId(spendUtils.defaultSpendId())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    String jobId = UUID.randomUUID().toString();
    workspaceService.createGcpCloudContext(request, jobId, USER_REQUEST, "/fake/value");
    jobService.waitForJob(jobId);
    assertNull(jobService.retrieveJobResult(jobId, Object.class).getException());
    assertTrue(gcpCloudContextService.getGcpCloudContext(request.getWorkspaceId()).isPresent());
    workspaceService.deleteGcpCloudContext(request, USER_REQUEST);
    assertTrue(gcpCloudContextService.getGcpCloudContext(request.getWorkspaceId()).isEmpty());
  }

  @Test
  void createGoogleContextRawlsStageThrows() throws Exception {
    // RAWLS_WORKSPACE stage workspaces use existing Sam resources instead of owning them, so the
    // mock pretends our user has access to any workspace we ask about.
    when(mockSamService.isAuthorized(
            any(), eq(SamResource.WORKSPACE), any(), eq(SamWorkspaceAction.READ)))
        .thenReturn(true);
    UUID workspaceId = UUID.randomUUID();
    Workspace request =
        defaultRequestBuilder(workspaceId).workspaceStage(WorkspaceStage.RAWLS_WORKSPACE).build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    String jobId = UUID.randomUUID().toString();
    ApiCreateCloudContextRequest contextRequest =
        new ApiCreateCloudContextRequest()
            .cloudPlatform(ApiCloudPlatform.GCP)
            .jobControl(new ApiJobControl().id(jobId));
    // Validation happens in the controller, so make this request through the mock MVC object rather
    // than calling the service directly.
    mockMvc
        .perform(
            addJsonContentType(
                    addAuth(
                        post(String.format(CREATE_CLOUD_CONTEXT_PATH_FORMAT, workspaceId)),
                        USER_REQUEST))
                .content(objectMapper.writeValueAsString(contextRequest)))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void cloneGcpWorkspace() {
    // Create a workspace
    final Workspace sourceWorkspace =
        defaultRequestBuilder(UUID.randomUUID())
            .userFacingId("source-user-facing-id")
            .displayName("Source Workspace")
            .description("The original workspace.")
            .spendProfileId(new SpendProfileId(SPEND_PROFILE_ID))
            .build();
    final UUID sourceWorkspaceId = workspaceService.createWorkspace(sourceWorkspace, USER_REQUEST);

    // create a cloud context
    final String createCloudContextJobId = UUID.randomUUID().toString();
    workspaceService.createGcpCloudContext(sourceWorkspace, createCloudContextJobId, USER_REQUEST);
    jobService.waitForJob(createCloudContextJobId);
    assertNull(jobService.retrieveJobResult(createCloudContextJobId, Object.class).getException());

    // add a bucket resource
    final ControlledGcsBucketResource bucketResource =
        ControlledGcsBucketResource.builder()
            .bucketName("terra-test-" + UUID.randomUUID().toString().toLowerCase())
            .common(
                ControlledResourceFields.builder()
                    .name("bucket_1")
                    .description("Just a plain bucket.")
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .resourceId(UUID.randomUUID())
                    .workspaceUuid(sourceWorkspaceId)
                    .managedBy(ManagedByType.MANAGED_BY_USER)
                    .privateResourceState(PrivateResourceState.INITIALIZING)
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                    .applicationId(null)
                    .iamRole(ControlledResourceIamRole.OWNER)
                    .assignedUser(USER_REQUEST.getEmail())
                    .build())
            .build();
    final ApiGcpGcsBucketCreationParameters creationParameters =
        new ApiGcpGcsBucketCreationParameters()
            .name("foo")
            .defaultStorageClass(ApiGcpGcsBucketDefaultStorageClass.NEARLINE)
            .lifecycle(
                new ApiGcpGcsBucketLifecycle()
                    .addRulesItem(
                        new ApiGcpGcsBucketLifecycleRule()
                            .condition(new ApiGcpGcsBucketLifecycleRuleCondition().age(90))
                            .action(
                                new ApiGcpGcsBucketLifecycleRuleAction()
                                    .type(ApiGcpGcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS)
                                    .storageClass(ApiGcpGcsBucketDefaultStorageClass.STANDARD))));

    final ControlledResource createdResource =
        controlledResourceService.createControlledResourceSync(
            bucketResource, ControlledResourceIamRole.OWNER, USER_REQUEST, creationParameters);

    final ControlledGcsBucketResource createdBucketResource =
        createdResource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    final Workspace destinationWorkspace =
        defaultRequestBuilder(UUID.randomUUID())
            .userFacingId("dest-user-facing-id")
            .displayName("Destination Workspace")
            .description("Copied from source")
            .spendProfileId(new SpendProfileId(SPEND_PROFILE_ID))
            .build();
    final String destinationLocation = "us-east1";
    final String cloneJobId =
        workspaceService.cloneWorkspace(
            sourceWorkspace, USER_REQUEST, destinationLocation, destinationWorkspace);
    jobService.waitForJob(cloneJobId);
    final JobResultOrException<ApiClonedWorkspace> cloneResultOrException =
        jobService.retrieveJobResult(cloneJobId, ApiClonedWorkspace.class);
    assertNull(cloneResultOrException.getException());
    final ApiClonedWorkspace cloneResult = cloneResultOrException.getResult();
    assertEquals(destinationWorkspace.getWorkspaceId(), cloneResult.getDestinationWorkspaceId());
    assertThat(cloneResult.getResources(), hasSize(1));

    final ApiResourceCloneDetails bucketCloneDetails = cloneResult.getResources().get(0);
    assertEquals(ApiCloneResourceResult.SUCCEEDED, bucketCloneDetails.getResult());
    assertNull(bucketCloneDetails.getErrorMessage());
    assertEquals(ApiResourceType.GCS_BUCKET, bucketCloneDetails.getResourceType());
    assertEquals(createdBucketResource.getResourceId(), bucketCloneDetails.getSourceResourceId());

    // destination WS should exist
    final Workspace retrievedDestinationWorkspace =
        workspaceService.getWorkspace(destinationWorkspace.getWorkspaceId());
    assertEquals(
        "Destination Workspace", retrievedDestinationWorkspace.getDisplayName().orElseThrow());
    assertEquals(
        "Copied from source", retrievedDestinationWorkspace.getDescription().orElseThrow());
    assertEquals(WorkspaceStage.MC_WORKSPACE, retrievedDestinationWorkspace.getWorkspaceStage());

    // Destination Workspace should have a GCP context
    assertNotNull(
        gcpCloudContextService
            .getGcpCloudContext(destinationWorkspace.getWorkspaceId())
            .orElseThrow());

    // clean up
    workspaceService.deleteWorkspace(sourceWorkspace, USER_REQUEST);
    workspaceService.deleteWorkspace(destinationWorkspace, USER_REQUEST);
  }

  /**
   * Convenience method for getting a WorkspaceRequest builder with some pre-filled default values.
   *
   * <p>This provides default values for jobId (random UUID), spend profile (Optional.empty()), and
   * workspace stage (MC_WORKSPACE).
   *
   * <p>Because the tests in this class mock Sam, we do not need to explicitly clean up workspaces
   * created here.
   */
  private Workspace.Builder defaultRequestBuilder(UUID workspaceUuid) {
    return Workspace.builder()
        .workspaceId(workspaceUuid)
        .userFacingId("a" + workspaceUuid.toString())
        .spendProfileId(null)
        .workspaceStage(WorkspaceStage.MC_WORKSPACE);
  }
}
