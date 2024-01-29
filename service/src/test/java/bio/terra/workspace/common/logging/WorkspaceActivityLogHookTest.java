package bio.terra.workspace.common.logging;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.mocks.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCES_TO_DELETE;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.FOLDER_ID;
import static bio.terra.workspace.service.workspace.model.OperationType.DELETE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.common.utils.WorkspaceUnitTestUtils;
import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.db.RawDaoTestFixture;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceActivityLogDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.model.DbWorkspaceActivityLog;
import bio.terra.workspace.service.folder.flights.DeleteFolderFlight;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.CloneControlledGcsBucketResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.CloneWorkspaceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.flight.create.workspace.WorkspaceCreateFlight;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextFlight;
import bio.terra.workspace.service.workspace.flight.delete.workspace.WorkspaceDeleteFlight;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class WorkspaceActivityLogHookTest extends BaseUnitTest {
  private static final UserStatusInfo USER_STATUS_INFO =
      new UserStatusInfo()
          .userEmail(USER_REQUEST.getEmail())
          .userSubjectId(USER_REQUEST.getSubjectId());
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private WorkspaceActivityLogDao activityLogDao;
  @Autowired private ResourceDao resourceDao;
  @Autowired private WorkspaceActivityLogHook hook;
  @Autowired private FolderDao folderDao;
  @Autowired private RawDaoTestFixture rawDaoTestFixture;

  @BeforeEach
  void setUpOnce() throws InterruptedException {
    when(mockSamService().getUserStatusInfo(any())).thenReturn(USER_STATUS_INFO);
  }

  @Test
  void createFlightSucceeds_activityLogUpdated() throws InterruptedException {
    var workspaceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    FlightMap inputParams = buildInputParams(workspaceUuid, OperationType.CREATE);
    hook.endFlight(
        new FakeFlightContext(
            WorkspaceCreateFlight.class.getName(), inputParams, FlightStatus.SUCCESS));
    ActivityLogChangeDetails changeDetails =
        activityLogDao.getLastUpdatedDetails(workspaceUuid).get();
    assertEquals(
        changeDetails,
        new ActivityLogChangeDetails(
            workspaceUuid,
            changeDetails.changeDate(),
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId(),
            OperationType.CREATE,
            workspaceUuid.toString(),
            ActivityLogChangedTarget.WORKSPACE));
  }

  @Test
  void cloneControlledGcsBucketResourceFlightSucceeds_activityLogUpdated()
      throws InterruptedException {
    var workspaceUuid = UUID.randomUUID();
    var destinationWorkspaceId = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdatedDetails(destinationWorkspaceId);
    assertTrue(emptyChangeDetails.isEmpty());

    FlightMap inputParams = buildInputParams(workspaceUuid, OperationType.CLONE);
    inputParams.put(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, destinationWorkspaceId);
    ControlledGcsBucketResource gcsBucketToClone =
        ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    inputParams.put(ResourceKeys.RESOURCE, gcsBucketToClone);
    hook.endFlight(
        new FakeFlightContext(
            CloneControlledGcsBucketResourceFlight.class.getName(),
            inputParams,
            FlightStatus.SUCCESS));
    ActivityLogChangeDetails changeDetails =
        activityLogDao.getLastUpdatedDetails(destinationWorkspaceId).get();
    assertEquals(
        changeDetails,
        new ActivityLogChangeDetails(
            destinationWorkspaceId,
            changeDetails.changeDate(),
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId(),
            OperationType.CLONE,
            gcsBucketToClone.getResourceId().toString(),
            ActivityLogChangedTarget.CONTROLLED_GCP_GCS_BUCKET));
    assertTrue(activityLogDao.getLastUpdatedDetails(workspaceUuid).isEmpty());
  }

  @Test
  void cloneWorkspaceFlightSucceeds_activityLogUpdated() throws InterruptedException {
    var workspaceUuid = UUID.randomUUID();
    var destinationWorkspaceId = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdatedDetails(destinationWorkspaceId);
    assertTrue(emptyChangeDetails.isEmpty());
    FlightMap inputParams = buildInputParams(workspaceUuid, OperationType.CLONE);
    inputParams.put(WorkspaceFlightMapKeys.WORKSPACE_ID, destinationWorkspaceId.toString());
    inputParams.put(ControlledResourceKeys.SOURCE_WORKSPACE_ID, workspaceUuid);

    hook.endFlight(
        new FakeFlightContext(
            CloneWorkspaceFlight.class.getName(), inputParams, FlightStatus.SUCCESS));

    ActivityLogChangeDetails changeDetails =
        activityLogDao.getLastUpdatedDetails(destinationWorkspaceId).get();
    assertEquals(
        changeDetails,
        new ActivityLogChangeDetails(
            destinationWorkspaceId,
            changeDetails.changeDate(),
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId(),
            OperationType.CLONE,
            workspaceUuid.toString(),
            ActivityLogChangedTarget.WORKSPACE));
    assertTrue(activityLogDao.getLastUpdatedDetails(workspaceUuid).isEmpty());
  }

  @Test
  void deleteFlightSucceeds_activityLogUpdated() throws InterruptedException {
    var workspaceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    FlightMap inputParams = buildInputParams(workspaceUuid, DELETE);
    hook.endFlight(
        new FakeFlightContext(
            WorkspaceDeleteFlight.class.getName(), inputParams, FlightStatus.SUCCESS));

    ActivityLogChangeDetails changeDetails =
        activityLogDao.getLastUpdatedDetails(workspaceUuid).get();
    assertEquals(
        changeDetails,
        new ActivityLogChangeDetails(
            workspaceUuid,
            changeDetails.changeDate(),
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId(),
            DELETE,
            workspaceUuid.toString(),
            ActivityLogChangedTarget.WORKSPACE));
  }

  @Test
  void unknownFlightSucceeds_activityLogNotUpdated() {
    var workspaceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    FlightMap inputParams = buildInputParams(workspaceUuid, DELETE);

    assertThrows(
        UnknownFlightClassNameException.class,
        () ->
            hook.endFlight(new FakeFlightContext("TestFlight", inputParams, FlightStatus.SUCCESS)));

    var changeDetails = activityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(changeDetails.isEmpty());
  }

  @Test
  void createFlightFails_activityLogNotUpdated() throws InterruptedException {
    var workspaceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    FlightMap inputParams = buildInputParams(workspaceUuid, OperationType.CREATE);

    hook.endFlight(
        new FakeFlightContext(
            WorkspaceCreateFlight.class.getName(), inputParams, FlightStatus.ERROR));

    var changeDetailsAfterFailedFlight = activityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(changeDetailsAfterFailedFlight.isEmpty());
  }

  @Test
  void deleteWorkspaceFlightFails_workspaceNotExist_logChangeDetails() throws InterruptedException {
    var workspaceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    FlightMap inputParams = buildInputParams(workspaceUuid, DELETE);
    hook.endFlight(
        new FakeFlightContext(
            WorkspaceDeleteFlight.class.getName(), inputParams, FlightStatus.ERROR));

    assertTrue(workspaceDao.getWorkspaceIfExists(workspaceUuid).isEmpty());
    ActivityLogChangeDetails changeDetailsAfterFailedFlight =
        activityLogDao.getLastUpdatedDetails(workspaceUuid).get();
    assertEquals(
        changeDetailsAfterFailedFlight,
        new ActivityLogChangeDetails(
            workspaceUuid,
            changeDetailsAfterFailedFlight.changeDate(),
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId(),
            DELETE,
            workspaceUuid.toString(),
            ActivityLogChangedTarget.WORKSPACE));
  }

  @Test
  void deleteWorkspaceFlightFails_workspaceStillExist_NotLogChangeDetails()
      throws InterruptedException {
    var workspace = WorkspaceFixtures.createDefaultMcWorkspace();
    var workspaceUuid = workspace.getWorkspaceId();
    var emptyChangeDetails = activityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    WorkspaceFixtures.createWorkspaceInDb(workspace, workspaceDao);
    FlightMap inputParams = buildInputParams(workspaceUuid, DELETE);
    hook.endFlight(
        new FakeFlightContext(
            WorkspaceDeleteFlight.class.getName(), inputParams, FlightStatus.ERROR));

    assertTrue(workspaceDao.getWorkspaceIfExists(workspaceUuid).isPresent());
    var changeDetailsAfterFailedFlight = activityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(changeDetailsAfterFailedFlight.isEmpty());
  }

  @Test
  void deleteCloudContextFlightFails_cloudContextNotExist_logChangeDetails()
      throws InterruptedException {
    var workspaceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    FlightMap inputParams = buildInputParams(workspaceUuid, DELETE);
    hook.endFlight(
        new FakeFlightContext(
            DeleteCloudContextFlight.class.getName(), inputParams, FlightStatus.ERROR));

    assertTrue(workspaceDao.getCloudContext(workspaceUuid, CloudPlatform.GCP).isEmpty());
    var changeDetailsAfterFailedFlight = activityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(changeDetailsAfterFailedFlight.isEmpty());
  }

  @Test
  void deleteGcpCloudContextFlightFails_cloudContextStillExist_notLogChangeDetails()
      throws InterruptedException {
    String fakeCloudContextJson =
        "{\"version\": 2, \"gcpProjectId\": \"terra-wsm-t-clean-berry-5152\", \"samPolicyOwner\": \"policy-owner\", \"samPolicyReader\": \"policy-reader\", \"samPolicyWriter\": \"policy-writer\", \"samPolicyApplication\": \"policy-application\"}";

    var workspace = WorkspaceFixtures.createDefaultMcWorkspace();
    var workspaceUuid = workspace.getWorkspaceId();
    var emptyChangeDetails = activityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    WorkspaceFixtures.createWorkspaceInDb(workspace, workspaceDao);

    var flightId = UUID.randomUUID().toString();
    var spendProfileId = new SpendProfileId("fake-spend-profile-id");
    workspaceDao.createCloudContextStart(
        workspaceUuid, CloudPlatform.GCP, spendProfileId, flightId);
    workspaceDao.createCloudContextSuccess(
        workspaceUuid, CloudPlatform.GCP, fakeCloudContextJson, flightId);

    FlightMap inputParams = buildInputParams(workspaceUuid, DELETE);
    hook.endFlight(
        new FakeFlightContext(
            DeleteCloudContextFlight.class.getName(), inputParams, FlightStatus.ERROR));

    assertTrue(workspaceDao.getCloudContext(workspaceUuid, CloudPlatform.GCP).isPresent());
    var changeDetailsAfterFailedFlight = activityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(changeDetailsAfterFailedFlight.isEmpty());
  }

  @Test
  void deleteResourceFlightFails_resourceNotExist_logChangeDetails() throws InterruptedException {
    var workspaceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    FlightMap inputParams = buildInputParams(workspaceUuid, DELETE);
    List<WsmResource> resourceToDelete = new ArrayList<>();
    resourceToDelete.add(
        ControlledGcpResourceFixtures.makeDefaultAiNotebookInstanceBuilder().build());
    inputParams.put(CONTROLLED_RESOURCES_TO_DELETE, resourceToDelete);
    hook.endFlight(
        new FakeFlightContext(
            DeleteControlledResourcesFlight.class.getName(), inputParams, FlightStatus.ERROR));

    ActivityLogChangeDetails changeDetailsAfterFailedFlight =
        activityLogDao.getLastUpdatedDetails(workspaceUuid).get();
    assertEquals(
        changeDetailsAfterFailedFlight,
        new ActivityLogChangeDetails(
            workspaceUuid,
            changeDetailsAfterFailedFlight.changeDate(),
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId(),
            DELETE,
            resourceToDelete.get(0).getResourceId().toString(),
            ActivityLogChangedTarget.RESOURCE));
  }

  @Test
  void deleteResourceFlightFails_resourceStillExist_notLogDelete() throws InterruptedException {
    UUID workspaceId = WorkspaceUnitTestUtils.createWorkspaceWithGcpContext(workspaceDao);
    Optional<ActivityLogChangeDetails> emptyChangeDetails =
        activityLogDao.getLastUpdatedDetails(workspaceId);
    assertTrue(emptyChangeDetails.isEmpty());
    List<WsmResource> resourcesToDelete = new ArrayList<>();
    // an AI notebook that is not "deleted" as it is put into the resource DAO.
    ControlledAiNotebookInstanceResource resource = createNotebookAndLog(workspaceId);
    resourcesToDelete.add(resource);

    FlightMap inputParams = buildInputParams(workspaceId, DELETE);
    inputParams.put(CONTROLLED_RESOURCES_TO_DELETE, resourcesToDelete);
    hook.endFlight(
        new FakeFlightContext(
            DeleteControlledResourcesFlight.class.getName(), inputParams, FlightStatus.ERROR));

    assertNotNull(resourceDao.getResource(workspaceId, resource.getResourceId()));
    var changeDetailsAfterFailedFlight = activityLogDao.getLastUpdatedDetails(workspaceId).get();
    assertNotEquals(DELETE, changeDetailsAfterFailedFlight.operationType());
  }

  @Test
  void deleteResourceFlightFails_resourcesPartialDelete_logChangeDetails()
      throws InterruptedException {
    UUID workspaceId = WorkspaceUnitTestUtils.createWorkspaceWithGcpContext(workspaceDao);
    Optional<ActivityLogChangeDetails> emptyChangeDetails =
        activityLogDao.getLastUpdatedDetails(workspaceId);
    assertTrue(emptyChangeDetails.isEmpty());
    List<WsmResource> resourcesToDelete = new ArrayList<>();
    // an AI notebook that is not "deleted" as it is put into the resource DAO.
    ControlledAiNotebookInstanceResource aiNotebook = createNotebookAndLog(workspaceId);
    resourcesToDelete.add(aiNotebook);
    // a dataset that is "deleted" as it is never put into the resource DAO.
    var dataset =
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceId).build();
    activityLogDao.writeActivity(
        workspaceId,
        new DbWorkspaceActivityLog(
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId(),
            OperationType.CREATE,
            dataset.getResourceId().toString(),
            ActivityLogChangedTarget.CONTROLLED_GCP_BIG_QUERY_DATASET));
    resourcesToDelete.add(dataset);

    FlightMap inputParams = buildInputParams(workspaceId, DELETE);
    inputParams.put(CONTROLLED_RESOURCES_TO_DELETE, resourcesToDelete);
    hook.endFlight(
        new FakeFlightContext(
            DeleteControlledResourcesFlight.class.getName(), inputParams, FlightStatus.ERROR));

    assertNotNull(resourceDao.getResource(workspaceId, aiNotebook.getResourceId()));
    var datasetLastLog =
        activityLogDao.getLastUpdatedDetails(workspaceId, dataset.getResourceId().toString()).get();
    assertEquals(
        new ActivityLogChangeDetails(
            workspaceId,
            datasetLastLog.changeDate(),
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId(),
            DELETE,
            dataset.getResourceId().toString(),
            ActivityLogChangedTarget.CONTROLLED_GCP_BIG_QUERY_DATASET),
        datasetLastLog);
  }

  private ControlledAiNotebookInstanceResource createNotebookAndLog(UUID workspaceId) {
    var aiNotebook =
        ControlledGcpResourceFixtures.makeDefaultAiNotebookInstanceBuilder(workspaceId).build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, aiNotebook);
    activityLogDao.writeActivity(
        workspaceId,
        new DbWorkspaceActivityLog(
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId(),
            OperationType.CREATE,
            aiNotebook.getResourceId().toString(),
            ActivityLogChangedTarget.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE));
    return aiNotebook;
  }

  @Test
  void deleteFolderFlightFails_folderDeleted_activityLogUpdated() throws InterruptedException {
    UUID workspaceId = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdatedDetails(workspaceId);
    assertTrue(emptyChangeDetails.isEmpty());

    Folder fooFolder =
        new Folder(
            /* folderId= */ UUID.randomUUID(),
            workspaceId,
            "foo",
            null,
            null,
            Map.of(),
            DEFAULT_USER_EMAIL,
            null);
    FlightMap inputParams = buildInputParams(workspaceId, DELETE);
    inputParams.put(FOLDER_ID, fooFolder.id());
    hook.endFlight(
        new FakeFlightContext(DeleteFolderFlight.class.getName(), inputParams, FlightStatus.ERROR));

    ActivityLogChangeDetails changeDetails =
        activityLogDao.getLastUpdatedDetails(workspaceId).get();
    assertEquals(
        changeDetails,
        new ActivityLogChangeDetails(
            workspaceId,
            changeDetails.changeDate(),
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId(),
            DELETE,
            fooFolder.id().toString(),
            ActivityLogChangedTarget.FOLDER));
  }

  @Test
  void deleteFolderFlightFails_folderNotDeleted_activityLogNotUpdated()
      throws InterruptedException {
    UUID workspaceId = WorkspaceUnitTestUtils.createWorkspaceWithGcpContext(workspaceDao);
    var emptyChangeDetails = activityLogDao.getLastUpdatedDetails(workspaceId);
    assertTrue(emptyChangeDetails.isEmpty());

    Folder fooFolder =
        folderDao.createFolder(
            new Folder(
                /* folderId= */ UUID.randomUUID(),
                workspaceId,
                "foo",
                null,
                null,
                Map.of(),
                DEFAULT_USER_EMAIL,
                null));
    FlightMap inputParams = buildInputParams(workspaceId, DELETE);
    inputParams.put(FOLDER_ID, fooFolder.id());
    hook.endFlight(
        new FakeFlightContext(DeleteFolderFlight.class.getName(), inputParams, FlightStatus.ERROR));

    Optional<ActivityLogChangeDetails> changeDetails =
        activityLogDao.getLastUpdatedDetails(workspaceId);
    assertTrue(changeDetails.isEmpty());
  }

  @Test
  void unknownFlightFails_activityLogNotUpdated() {
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    FlightMap inputParams = buildInputParams(workspaceUuid, DELETE);

    assertThrows(
        UnknownFlightClassNameException.class,
        () ->
            hook.endFlight(
                new FakeFlightContext("TestDeleteFlight", inputParams, FlightStatus.ERROR)));

    var changeDetailsAfterFailedFlight = activityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(changeDetailsAfterFailedFlight.isEmpty());
  }

  @Test
  void resourceDeletionFlightSucceed_logUpdated() throws InterruptedException {
    var workspaceUuid = UUID.randomUUID();
    var emptyChangeDetails = activityLogDao.getLastUpdatedDetails(workspaceUuid);
    assertTrue(emptyChangeDetails.isEmpty());

    FlightMap inputParams = buildInputParams(workspaceUuid, DELETE);
    List<WsmResource> resourceToDelete = new ArrayList<>();
    resourceToDelete.add(
        ControlledGcpResourceFixtures.makeDefaultAiNotebookInstanceBuilder().build());
    resourceToDelete.add(
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid).build());
    inputParams.put(CONTROLLED_RESOURCES_TO_DELETE, resourceToDelete);
    hook.endFlight(
        new FakeFlightContext(
            DeleteControlledResourcesFlight.class.getName(), inputParams, FlightStatus.SUCCESS));

    List<ActivityLogChangeDetails> logTrails =
        rawDaoTestFixture.readActivityLogs(
            workspaceUuid,
            resourceToDelete.stream().map(r -> r.getResourceId().toString()).toList());
    assertEquals(2, logTrails.size());
    assertThat(
        // Clear the changeDate for easier testing.
        logTrails.stream()
            .filter(log -> log.changeDate() != null)
            .map(log -> log.withChangeDate(null))
            .toList(),
        containsInAnyOrder(
            new ActivityLogChangeDetails(
                workspaceUuid,
                null,
                USER_REQUEST.getEmail(),
                USER_REQUEST.getSubjectId(),
                DELETE,
                resourceToDelete.get(0).getResourceId().toString(),
                ActivityLogChangedTarget.RESOURCE),
            new ActivityLogChangeDetails(
                workspaceUuid,
                null,
                USER_REQUEST.getEmail(),
                USER_REQUEST.getSubjectId(),
                DELETE,
                resourceToDelete.get(1).getResourceId().toString(),
                ActivityLogChangedTarget.RESOURCE)));
  }

  private FlightMap buildInputParams(UUID workspaceUuid, OperationType operationType) {
    FlightMap inputParams = new FlightMap();
    inputParams.put(WorkspaceFlightMapKeys.OPERATION_TYPE, operationType);
    inputParams.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    inputParams.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), USER_REQUEST);
    return inputParams;
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
    private final FlightMap inputParameters;

    private final FlightMap workingParams;
    private final FlightStatus status;

    FakeFlightContext(
        String flightClassName, FlightMap inputParameters, FlightStatus flightStatus) {
      this.flightClassName = flightClassName;
      this.inputParameters = inputParameters;
      this.workingParams = new FlightMap();
      this.status = flightStatus;
    }

    FakeFlightContext(
        String flightClassName,
        FlightMap inputParameters,
        FlightMap workingParams,
        FlightStatus flightStatus) {
      this.flightClassName = flightClassName;
      this.inputParameters = inputParameters;
      this.workingParams = workingParams;
      this.status = flightStatus;
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
      return inputParameters;
    }

    @Override
    public FlightMap getWorkingMap() {
      return workingParams;
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
    public void setProgressMeter(String name, long v1, long v2) {}
  }
}
