package bio.terra.workspace.common.logging;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCES_TO_DELETE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.stairway.Direction;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.ProgressMeter;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.UnknownFlightClassNameException;
import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceActivityLogDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.flight.DeleteGcpContextFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceCreateFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceDeleteFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class WorkspaceActivityLogHookTest extends BaseUnitTest {

  /** A fake authenticated user request. */
  private static final AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest()
          .token(Optional.of("fake-token"))
          .email("fake@email.com")
          .subjectId("fakeID123");

  private static final UserStatusInfo USER_STATUS_INFO =
      new UserStatusInfo()
          .userEmail(USER_REQUEST.getEmail())
          .userSubjectId(USER_REQUEST.getSubjectId());
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private WorkspaceActivityLogDao activityLogDao;
  @Autowired private ResourceDao resourceDao;
  @Autowired private WorkspaceActivityLogHook hook;
  @MockBean private SamService mockSamService;

  @BeforeEach
  void setUpOnce() throws InterruptedException {
    when(mockSamService.getUserStatusInfo(any())).thenReturn(USER_STATUS_INFO);
  }

  @Test
  void createFlightSucceeds_activityLogUpdated() throws InterruptedException {
    var workspaceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    FlightMap inputParams = buildInputParams(workspaceUuid, OperationType.CREATE);
    hook.endFlight(
        new FakeFlightContext(
            WorkspaceCreateFlight.class.getName(), inputParams, FlightStatus.SUCCESS));
    var changeDetails = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertChangeDetails(changeDetails);
  }

  @Test
  void deleteFlightSucceeds_activityLogUpdated() throws InterruptedException {
    var workspaceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    FlightMap inputParams = buildInputParams(workspaceUuid, OperationType.DELETE);
    hook.endFlight(
        new FakeFlightContext(
            WorkspaceDeleteFlight.class.getName(), inputParams, FlightStatus.SUCCESS));

    var changeDetails = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertChangeDetails(changeDetails);
  }

  @Test
  void unknownFlightSucceeds_activityLogNotUpdated() {
    var workspaceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    FlightMap inputParams = buildInputParams(workspaceUuid, OperationType.DELETE);

    assertThrows(
        UnknownFlightClassNameException.class,
        () ->
            hook.endFlight(new FakeFlightContext("TestFlight", inputParams, FlightStatus.SUCCESS)));

    var changeDetails = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(changeDetails.isEmpty());
  }

  @Test
  void createFlightFails_activityLogNotUpdated() throws InterruptedException {
    var workspaceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    FlightMap inputParams = buildInputParams(workspaceUuid, OperationType.CREATE);

    hook.endFlight(
        new FakeFlightContext(
            WorkspaceCreateFlight.class.getName(), inputParams, FlightStatus.ERROR));

    var changeDetailsAfterFailedFlight = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(changeDetailsAfterFailedFlight.isEmpty());
  }

  @Test
  void deleteWorkspaceFlightFails_workspaceNotExist_logChangeDetails() throws InterruptedException {
    var workspaceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    FlightMap inputParams = buildInputParams(workspaceUuid, OperationType.DELETE);
    hook.endFlight(
        new FakeFlightContext(
            WorkspaceDeleteFlight.class.getName(), inputParams, FlightStatus.ERROR));

    assertTrue(workspaceDao.getWorkspaceIfExists(workspaceUuid).isEmpty());
    var changeDetailsAfterFailedFlight = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertChangeDetails(changeDetailsAfterFailedFlight);
  }

  @Test
  void deleteWorkspaceFlightFails_workspaceStillExist_NotLogChangeDetails()
      throws InterruptedException {
    var workspaceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    workspaceDao.createWorkspace(
        Workspace.builder()
            .workspaceId(workspaceUuid)
            .userFacingId(workspaceUuid.toString())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build());
    FlightMap inputParams = buildInputParams(workspaceUuid, OperationType.DELETE);
    hook.endFlight(
        new FakeFlightContext(
            WorkspaceDeleteFlight.class.getName(), inputParams, FlightStatus.ERROR));

    assertTrue(workspaceDao.getWorkspaceIfExists(workspaceUuid).isPresent());
    var changeDetailsAfterFailedFlight = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(changeDetailsAfterFailedFlight.isEmpty());
  }

  @Test
  void deleteCloudContextFlightFails_cloudContextNotExist_logChangeDetails()
      throws InterruptedException {
    var workspaceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    FlightMap inputParams = buildInputParams(workspaceUuid, OperationType.DELETE);
    hook.endFlight(
        new FakeFlightContext(
            DeleteGcpContextFlight.class.getName(), inputParams, FlightStatus.ERROR));

    assertTrue(workspaceDao.getCloudContext(workspaceUuid, CloudPlatform.GCP).isEmpty());
    var changeDetailsAfterFailedFlight = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertChangeDetails(changeDetailsAfterFailedFlight);
  }

  @Test
  void deleteGcpCloudContextFlightFails_cloudContextStillExist_notLogChangeDetails()
      throws InterruptedException {
    var workspaceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    workspaceDao.createWorkspace(
        Workspace.builder()
            .workspaceId(workspaceUuid)
            .userFacingId(workspaceUuid.toString())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build());
    var flightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(workspaceUuid, CloudPlatform.GCP, flightId);
    workspaceDao.createCloudContextFinish(
        workspaceUuid,
        CloudPlatform.GCP,
        "{\"version\": 1, \"gcpProjectId\": \"my-gcp-project-name-123\"}",
        flightId);

    FlightMap inputParams = buildInputParams(workspaceUuid, OperationType.DELETE);
    hook.endFlight(
        new FakeFlightContext(
            DeleteGcpContextFlight.class.getName(), inputParams, FlightStatus.ERROR));

    assertTrue(workspaceDao.getCloudContext(workspaceUuid, CloudPlatform.GCP).isPresent());
    var changeDetailsAfterFailedFlight = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(changeDetailsAfterFailedFlight.isEmpty());
  }

  @Test
  void deleteResourceFlightFails_resourceNotExist_logChangeDetails() throws InterruptedException {
    var workspaceUuid = UUID.randomUUID();
    var resourceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    FlightMap inputParams = buildInputParams(workspaceUuid, OperationType.DELETE);
    List<WsmResource> resourceToDelete = new ArrayList<>();
    resourceToDelete.add(
        ControlledAiNotebookInstanceResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceUuid)
                    .name("my-notebook")
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                    .assignedUser("yuhuyoyo@google.com")
                    .cloningInstructions(CloningInstructions.COPY_NOTHING)
                    .managedBy(ManagedByType.MANAGED_BY_USER)
                    .build())
            .instanceId("my-notebook-instance")
            .projectId("my-project")
            .location("us-central1-a")
            .build());
    inputParams.put(CONTROLLED_RESOURCES_TO_DELETE, resourceToDelete);
    hook.endFlight(
        new FakeFlightContext(
            DeleteControlledResourcesFlight.class.getName(), inputParams, FlightStatus.ERROR));

    var changeDetailsAfterFailedFlight = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertChangeDetails(changeDetailsAfterFailedFlight);
  }

  @Test
  void deleteResourceFlightFails_resourceStillExist_notLogChangeDetails()
      throws InterruptedException {
    var workspaceUuid = UUID.randomUUID();
    var resourceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    workspaceDao.createWorkspace(
        Workspace.builder()
            .workspaceId(workspaceUuid)
            .userFacingId(workspaceUuid.toString())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build());
    var flightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(workspaceUuid, CloudPlatform.GCP, flightId);
    workspaceDao.createCloudContextFinish(
        workspaceUuid,
        CloudPlatform.GCP,
        "{\"version\": 1, \"gcpProjectId\": \"my-gcp-project-name-123\"}",
        flightId);
    var resource =
        ControlledAiNotebookInstanceResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceUuid)
                    .name("my-notebook")
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                    .assignedUser("yuhuyoyo@google.com")
                    .cloningInstructions(CloningInstructions.COPY_NOTHING)
                    .managedBy(ManagedByType.MANAGED_BY_USER)
                    .build())
            .instanceId("my-notebook-instance-123")
            .projectId("my-gcp-project")
            .location("us-central-1a")
            .build();
    resourceDao.createControlledResource(resource);

    FlightMap inputParams = buildInputParams(workspaceUuid, OperationType.DELETE);
    inputParams.put(ResourceKeys.RESOURCE, resource);
    hook.endFlight(
        new FakeFlightContext(
            DeleteGcpContextFlight.class.getName(), inputParams, FlightStatus.ERROR));

    assertNotNull(resourceDao.getResource(workspaceUuid, resourceUuid));
    var changeDetailsAfterFailedFlight = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(changeDetailsAfterFailedFlight.isEmpty());
  }

  @Test
  void unknownFlightFails_activityLogNotUpdated() {
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    FlightMap inputParams = buildInputParams(workspaceUuid, OperationType.DELETE);

    assertThrows(
        UnknownFlightClassNameException.class,
        () ->
            hook.endFlight(
                new FakeFlightContext("TestDeleteFlight", inputParams, FlightStatus.ERROR)));

    var changeDetailsAfterFailedFlight = activityLogDao.getLastUpdateDetails(workspaceUuid);
    assertTrue(changeDetailsAfterFailedFlight.isEmpty());
  }

  private FlightMap buildInputParams(UUID workspaceUuid, OperationType operationType) {
    FlightMap inputParams = new FlightMap();
    inputParams.put(WorkspaceFlightMapKeys.OPERATION_TYPE, operationType);
    inputParams.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    inputParams.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), USER_REQUEST);
    return inputParams;
  }

  private void assertChangeDetails(Optional<ActivityLogChangeDetails> changeDetails) {
    assertTrue(changeDetails.isPresent());
    assertEquals(USER_REQUEST.getEmail(), changeDetails.get().getActorEmail());
    assertEquals(USER_REQUEST.getSubjectId(), changeDetails.get().getActorSubjectId());
    assertNotNull(changeDetails.get().getChangeDate());
  }

  /**
   * The hook assert the flight class name so we can't use a fake test flight to test the hook
   * logic. Mocking the static method {@code ActivityFlight#fromFlightClassName} does not work
   * either because JobService launches the flight in a separate thread, escaping the mock. Thus we
   * create this fake flight context class to specifically test the endFlight in {@link
   * WorkspaceActivityLogHook}
   */
  private class FakeFlightContext implements FlightContext {

    private final String flightClassName;
    private final FlightMap inputParams;
    private final FlightStatus status;

    FakeFlightContext(String flightClassName, FlightMap inputMap, FlightStatus flightStatus) {
      this.flightClassName = flightClassName;
      inputParams = inputMap;
      status = flightStatus;
    }

    @Override
    public Object getApplicationContext() {
      return null;
    }

    @Override
    public String getFlightId() {
      return "flight-id";
    }

    @Override
    public String getFlightClassName() {
      return flightClassName;
    }

    @Override
    public FlightMap getInputParameters() {
      return inputParams;
    }

    @Override
    public FlightMap getWorkingMap() {
      return new FlightMap();
    }

    @Override
    public int getStepIndex() {
      return 0;
    }

    @Override
    public FlightStatus getFlightStatus() {
      return status;
    }

    @Override
    public boolean isRerun() {
      return false;
    }

    @Override
    public Direction getDirection() {
      return Direction.DO;
    }

    @Override
    public StepResult getResult() {
      return StepResult.getStepResultSuccess();
    }

    @Override
    public Stairway getStairway() {
      return null;
    }

    @Override
    public List<String> getStepClassNames() {
      return null;
    }

    @Override
    public String getStepClassName() {
      return null;
    }

    @Override
    public String prettyStepState() {
      return null;
    }

    @Override
    public String flightDesc() {
      return null;
    }

    @Override
    public ProgressMeter getProgressMeter(String name) {
      return null;
    }

    @Override
    public void setProgressMeter(String name, long v1, long v2) throws InterruptedException {}
  }
}
