package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.defaultWorkspaceBuilder;
import static bio.terra.workspace.common.utils.MockMvcUtils.CREATE_CLOUD_CONTEXT_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.WORKSPACES_V1_BY_UFID_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.WORKSPACES_V1_BY_UUID_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceActivityLogDao;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreateCloudContextRequest;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamResource;
import bio.terra.workspace.service.iam.model.SamConstants.SamSpendProfileAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.DuplicateUserFacingIdException;
import bio.terra.workspace.service.workspace.exceptions.DuplicateWorkspaceException;
import bio.terra.workspace.service.workspace.flight.CheckSamWorkspaceAuthzStep;
import bio.terra.workspace.service.workspace.flight.CreateWorkspaceAuthzStep;
import bio.terra.workspace.service.workspace.flight.CreateWorkspacePoliciesStep;
import bio.terra.workspace.service.workspace.flight.CreateWorkspaceStep;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// Use application configuration profile in addition to the standard connected test profile
// inherited from the base class.
@Tag("connected")
@ActiveProfiles({"app-test"})
class WorkspaceServiceTest extends BaseConnectedTest {
  @MockBean private DataRepoService mockDataRepoService;
  /** Mock SamService does nothing for all calls that would throw if unauthorized. */
  @MockBean private SamService mockSamService;

  @Autowired private MockMvc mockMvc;
  @Autowired private JobService jobService;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ReferencedResourceService referenceResourceService;
  @Autowired private ResourceDao resourceDao;
  @Autowired private WorkspaceService workspaceService;
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
    // Return a valid Google group for cloud sync, as Google validates groups added to GCP projects.
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
    when(mockSamService.getUserEmailFromSamAndRethrowOnInterrupt(
            any(AuthenticatedUserRequest.class)))
        .thenReturn(USER_REQUEST.getEmail());
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    jobService.setFlightDebugInfoForTest(null);
  }

  @Test
  void getWorkspace_existing() {
    Workspace request = WorkspaceFixtures.buildMcWorkspace();
    workspaceService.createWorkspace(request, null, null, USER_REQUEST);

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
    Workspace request = WorkspaceFixtures.buildMcWorkspace();
    workspaceService.createWorkspace(request, null, null, USER_REQUEST);

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
    Workspace request = defaultWorkspaceBuilder(null).userFacingId(userFacingId).build();
    workspaceService.createWorkspace(request, null, null, USER_REQUEST);

    assertEquals(
        request.getWorkspaceId(),
        workspaceService
            .getWorkspaceByUserFacingId(
                userFacingId, USER_REQUEST, /*minimumHighestRoleFromRequest=*/ WsmIamRole.READER)
            .getWorkspaceId());
  }

  @Test
  void getWorkspaceByUserFacingId_missing() {
    assertThrows(
        WorkspaceNotFoundException.class,
        () ->
            workspaceService.getWorkspaceByUserFacingId(
                "missing-workspace",
                USER_REQUEST,
                /*minimumHighestRoleFromRequest=*/ WsmIamRole.READER));
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
    Workspace request = defaultWorkspaceBuilder(null).userFacingId(userFacingId).build();
    workspaceService.createWorkspace(request, null, null, USER_REQUEST);

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
        () ->
            workspaceService.getWorkspaceByUserFacingId(
                userFacingId, USER_REQUEST, /*minimumHighestRoleFromRequest=*/ WsmIamRole.READER));
  }

  @Test
  void getHighestRole_existing() {
    when(mockSamService.listRequesterRoles(any(), any(), any()))
        .thenReturn(ImmutableList.of(WsmIamRole.OWNER, WsmIamRole.WRITER));

    Workspace request = WorkspaceFixtures.buildMcWorkspace();
    workspaceService.createWorkspace(request, null, null, USER_REQUEST);

    assertEquals(
        WsmIamRole.OWNER, workspaceService.getHighestRole(request.getWorkspaceId(), USER_REQUEST));
  }

  @Test
  void testWorkspaceStagePersists() {
    Workspace mcWorkspaceRequest = WorkspaceFixtures.buildMcWorkspace();
    workspaceService.createWorkspace(mcWorkspaceRequest, null, null, USER_REQUEST);
    Workspace createdWorkspace = workspaceService.getWorkspace(mcWorkspaceRequest.getWorkspaceId());
    assertEquals(mcWorkspaceRequest.getWorkspaceId(), createdWorkspace.getWorkspaceId());
    assertEquals(WorkspaceStage.MC_WORKSPACE, createdWorkspace.getWorkspaceStage());
  }

  @Test
  void duplicateWorkspaceIdRequestsRejected() {
    Workspace request = WorkspaceFixtures.buildMcWorkspace();
    workspaceService.createWorkspace(request, null, null, USER_REQUEST);
    Workspace duplicateWorkspace =
        defaultWorkspaceBuilder(request.getWorkspaceId())
            .description("slightly different workspace")
            .build();
    assertThrows(
        DuplicateWorkspaceException.class,
        () -> workspaceService.createWorkspace(duplicateWorkspace, null, null, USER_REQUEST));
  }

  @Test
  void duplicateWorkspaceUserFacingIdRequestsRejected() {
    String userFacingId = "create-workspace-user-facing-id";
    Workspace request = defaultWorkspaceBuilder(null).userFacingId(userFacingId).build();
    workspaceService.createWorkspace(request, null, null, USER_REQUEST);
    Workspace duplicateUserFacingId =
        defaultWorkspaceBuilder(null).userFacingId(userFacingId).build();

    DuplicateUserFacingIdException ex =
        assertThrows(
            DuplicateUserFacingIdException.class,
            () ->
                workspaceService.createWorkspace(duplicateUserFacingId, null, null, USER_REQUEST));
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
                WorkspaceFixtures.buildMcWorkspace(), null, null, USER_REQUEST));
    // This second call shares the above operation ID, and so should return the same exception
    // instead of a more generic internal Stairway exception.
    assertThrows(
        ErrorReportException.class,
        () ->
            workspaceService.createWorkspace(
                WorkspaceFixtures.buildMcWorkspace(), null, null, USER_REQUEST));
  }

  @Test
  void testWithSpendProfile() {
    SpendProfileId spendProfileId = new SpendProfileId("foo");
    Workspace request = defaultWorkspaceBuilder(null).spendProfileId(spendProfileId).build();
    workspaceService.createWorkspace(request, null, null, USER_REQUEST);

    Workspace createdWorkspace = workspaceService.getWorkspace(request.getWorkspaceId());
    assertEquals(request.getWorkspaceId(), createdWorkspace.getWorkspaceId());
    assertEquals(spendProfileId, createdWorkspace.getSpendProfileId().orElse(null));
  }

  @Test
  void testWithDisplayNameAndDescription() {
    String name = "My workspace";
    String description = "The greatest workspace";
    Workspace request =
        defaultWorkspaceBuilder(null).displayName(name).description(description).build();
    workspaceService.createWorkspace(request, null, null, USER_REQUEST);

    Workspace createdWorkspace = workspaceService.getWorkspace(request.getWorkspaceId());
    assertEquals(
        request.getDescription().orElse(null), createdWorkspace.getDescription().orElse(null));
    assertEquals(name, createdWorkspace.getDisplayName().orElse(null));
    assertEquals(description, createdWorkspace.getDescription().orElse(null));
  }

  @Test
  void testUpdateWorkspace() {
    Workspace request = WorkspaceFixtures.buildMcWorkspace();
    workspaceService.createWorkspace(request, null, null, USER_REQUEST);
    UUID workspaceUuid = request.getWorkspaceId();
    var lastUpdateDetails = workspaceActivityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(lastUpdateDetails.isPresent());
    Workspace createdWorkspace = workspaceService.getWorkspace(request.getWorkspaceId());
    assertEquals(request.getWorkspaceId(), createdWorkspace.getWorkspaceId());
    assertTrue(createdWorkspace.getDisplayName().isEmpty());
    assertTrue(createdWorkspace.getDescription().isEmpty());

    String userFacingId = "my-user-facing-id";
    String name = "My workspace";
    String description = "The greatest workspace";

    Workspace updatedWorkspace =
        workspaceService.updateWorkspace(
            workspaceUuid, userFacingId, name, description, USER_REQUEST);

    var workspaceUpdateChangeDetails = workspaceActivityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(
        lastUpdateDetails
            .get()
            .changeDate()
            .isBefore(workspaceUpdateChangeDetails.get().changeDate()));
    assertEquals(USER_REQUEST.getEmail(), workspaceUpdateChangeDetails.get().actorEmail());
    assertEquals(USER_REQUEST.getSubjectId(), workspaceUpdateChangeDetails.get().actorSubjectId());

    assertEquals(userFacingId, updatedWorkspace.getUserFacingId());
    assertTrue(updatedWorkspace.getDisplayName().isPresent());
    assertEquals(name, updatedWorkspace.getDisplayName().get());
    assertTrue(updatedWorkspace.getDescription().isPresent());
    assertEquals(description, updatedWorkspace.getDescription().get());

    String otherDescription = "The deprecated workspace";

    Workspace secondUpdatedWorkspace =
        workspaceService.updateWorkspace(
            workspaceUuid, /*userFacingId=*/ null, /*name=*/ null, otherDescription, USER_REQUEST);

    var secondUpdateChangeDetails = workspaceActivityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(
        workspaceUpdateChangeDetails
            .get()
            .changeDate()
            .isBefore(secondUpdateChangeDetails.get().changeDate()));
    assertEquals(USER_REQUEST.getEmail(), secondUpdateChangeDetails.get().actorEmail());
    assertEquals(USER_REQUEST.getSubjectId(), secondUpdateChangeDetails.get().actorSubjectId());
    // Since name is null, leave it alone. Description should be updated.
    assertTrue(secondUpdatedWorkspace.getDisplayName().isPresent());
    assertEquals(name, secondUpdatedWorkspace.getDisplayName().get());
    assertTrue(secondUpdatedWorkspace.getDescription().isPresent());
    assertEquals(otherDescription, secondUpdatedWorkspace.getDescription().get());

    // Sending through empty strings and an empty map clears the values.
    Workspace thirdUpdatedWorkspace =
        workspaceService.updateWorkspace(workspaceUuid, userFacingId, "", "", USER_REQUEST);

    var thirdUpdatedDateAfterWorkspaceUpdate =
        workspaceActivityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(
        secondUpdateChangeDetails
            .get()
            .changeDate()
            .isBefore(thirdUpdatedDateAfterWorkspaceUpdate.get().changeDate()));
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
                /*userFacingId=*/ null,
                /*name=*/ null,
                /*description=*/ null,
                USER_REQUEST));
    var failedUpdateDate = workspaceActivityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertEquals(
        thirdUpdatedDateAfterWorkspaceUpdate.get().changeDate(),
        failedUpdateDate.get().changeDate());
  }

  @Test
  void testUpdateWorkspaceUserFacingIdAlreadyExistsRejected() {
    // Create one workspace with userFacingId, one without.
    String userFacingId = "update-workspace-user-facing-id";
    Workspace request = defaultWorkspaceBuilder(null).userFacingId(userFacingId).build();
    workspaceService.createWorkspace(request, null, null, USER_REQUEST);
    UUID secondWorkspaceUuid = UUID.randomUUID();
    request = defaultWorkspaceBuilder(secondWorkspaceUuid).build();
    workspaceService.createWorkspace(request, null, null, USER_REQUEST);

    // Try to set second workspace's userFacing to first.
    DuplicateUserFacingIdException ex =
        assertThrows(
            DuplicateUserFacingIdException.class,
            () ->
                workspaceService.updateWorkspace(
                    secondWorkspaceUuid, userFacingId, null, null, USER_REQUEST));
    assertEquals(
        ex.getMessage(), String.format("Workspace with ID %s already exists", userFacingId));
  }

  @Test
  void testUpdateWorkspaceProperties() {
    // Create one workspace with properties
    Map<String, String> propertyMap =
        new HashMap<>() {
          {
            put("foo", "bar");
            put("xyzzy", "plohg");
          }
        };
    Workspace request = defaultWorkspaceBuilder(null).build();
    workspaceService.createWorkspace(request, null, null, USER_REQUEST);
    UUID workspaceUuid = request.getWorkspaceId();
    var lastUpdateDetails = workspaceActivityLogDao.getLastUpdatedDetails(workspaceUuid);
    OffsetDateTime lastUpdatedDate = lastUpdateDetails.get().changeDate();
    assertNotNull(lastUpdatedDate);

    // Workspace update new properties
    workspaceService.updateWorkspaceProperties(workspaceUuid, propertyMap, USER_REQUEST);
    Workspace updatedWorkspace = workspaceService.getWorkspace(workspaceUuid);

    var updateDetailsAfterWorkspaceUpdate =
        workspaceActivityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(lastUpdatedDate.isBefore(updateDetailsAfterWorkspaceUpdate.get().changeDate()));
    assertEquals(
        propertyMap, updatedWorkspace.getProperties(), "Workspace properties update successfully");
    assertEquals(USER_REQUEST.getEmail(), updateDetailsAfterWorkspaceUpdate.get().actorEmail());
    assertEquals(
        USER_REQUEST.getSubjectId(), updateDetailsAfterWorkspaceUpdate.get().actorSubjectId());
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
                    WorkspaceFixtures.buildMcWorkspace(), null, null, USER_REQUEST));
    assertEquals(apiErrorMsg, exception.getMessage());
  }

  @Test
  void createAndDeleteWorkspace() {
    Workspace request = WorkspaceFixtures.buildMcWorkspace();
    workspaceService.createWorkspace(request, null, null, USER_REQUEST);

    workspaceService.deleteWorkspace(request, USER_REQUEST);
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(request.getWorkspaceId()));
  }

  @Test
  void createMcWorkspaceDoSteps() {
    Workspace request = WorkspaceFixtures.buildMcWorkspace();
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(CreateWorkspaceAuthzStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        CreateWorkspacePoliciesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(CreateWorkspaceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build();
    jobService.setFlightDebugInfoForTest(debugInfo);

    UUID createdId = workspaceService.createWorkspace(request, null, null, USER_REQUEST);
    assertEquals(createdId, request.getWorkspaceId());
  }

  @Test
  void createRawlsWorkspaceDoSteps() throws InterruptedException {
    Workspace request =
        defaultWorkspaceBuilder(null).workspaceStage(WorkspaceStage.RAWLS_WORKSPACE).build();
    // Ensure the auth check in CheckSamWorkspaceAuthzStep always succeeds.
    doReturn(true).when(mockSamService).isAuthorized(any(), any(), any(), any());
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        CheckSamWorkspaceAuthzStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(CreateWorkspaceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build();
    jobService.setFlightDebugInfoForTest(debugInfo);

    UUID createdId = workspaceService.createWorkspace(request, null, null, USER_REQUEST);
    assertEquals(createdId, request.getWorkspaceId());
  }

  @Test
  void createMcWorkspaceUndoSteps() {
    Workspace request = WorkspaceFixtures.buildMcWorkspace();
    // Retry undo steps once and fail at the end of the flight.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(CreateWorkspaceAuthzStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        CreateWorkspacePoliciesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(CreateWorkspaceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo =
        FlightDebugInfo.newBuilder().undoStepFailures(retrySteps).lastStepFailure(true).build();
    jobService.setFlightDebugInfoForTest(debugInfo);

    // Service methods which wait for a flight to complete will throw an
    // InvalidResultStateException when that flight fails without a cause, which occurs when a
    // flight fails via debugInfo.
    assertThrows(
        InvalidResultStateException.class,
        () -> workspaceService.createWorkspace(request, null, null, USER_REQUEST));
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

    Workspace request = defaultWorkspaceBuilder(null).properties(propertyMap).build();
    workspaceService.createWorkspace(request, null, null, USER_REQUEST);
    UUID workspaceUuid = request.getWorkspaceId();
    var lastUpdateDetails = workspaceActivityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(lastUpdateDetails.isPresent());

    List<String> propertyKeys = new ArrayList<>(Arrays.asList("foo", "foo1"));

    workspaceService.deleteWorkspaceProperties(workspaceUuid, propertyKeys, USER_REQUEST);
    Workspace deletedWorkspace = workspaceService.getWorkspace(workspaceUuid);

    var updateDetailsAfterWorkspaceUpdate =
        workspaceActivityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(
        lastUpdateDetails
            .get()
            .changeDate()
            .isBefore(updateDetailsAfterWorkspaceUpdate.get().changeDate()));
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
    Workspace request = WorkspaceFixtures.buildMcWorkspace();
    workspaceService.createWorkspace(request, null, null, USER_REQUEST);

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
    Workspace request = defaultWorkspaceBuilder(workspaceUuid).build();
    workspaceService.createWorkspace(request, null, null, USER_REQUEST);

    // Next, add a data reference to that workspace.
    ReferencedDataRepoSnapshotResource snapshot =
        ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceUuid);
    UUID resourceId = snapshot.getResourceId();

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
  void createGoogleContextRawlsStageThrows() throws Exception {
    // RAWLS_WORKSPACE stage workspaces use existing Sam resources instead of owning them, so the
    // mock pretends our user has access to any workspace we ask about.
    when(mockSamService.isAuthorized(
            any(), eq(SamResource.WORKSPACE), any(), eq(SamConstants.SamWorkspaceAction.READ)))
        .thenReturn(true);
    UUID workspaceId = UUID.randomUUID();
    Workspace request =
        defaultWorkspaceBuilder(workspaceId).workspaceStage(WorkspaceStage.RAWLS_WORKSPACE).build();
    workspaceService.createWorkspace(request, null, null, USER_REQUEST);
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
}
