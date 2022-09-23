package bio.terra.workspace.service.folder;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.common.stairway.StairwayComponent;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceActivityLogDao;
import bio.terra.workspace.db.exception.FolderNotFoundException;
import bio.terra.workspace.service.folder.flights.DeleteFoldersStep;
import bio.terra.workspace.service.folder.flights.DeleteReferencedResourcesStep;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo.ReferencedGitRepoResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;


public class FolderServiceTest extends BaseConnectedTest {

  @Autowired private FolderService folderService;
  @Autowired private WorkspaceConnectedTestUtils workspaceConnectedTestUtils;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private ResourceDao resourceDao;
  @Autowired private JobService jobService;
  @Autowired private WorkspaceActivityLogDao workspaceActivityLogDao;

  private UUID workspaceId;
  // foo/
  private Folder fooFolder;
  // foo/bar/
  private Folder fooBarFolder;
  // foo/foo/
  private Folder fooFooFolder;
  // foo/bar/loo
  private Folder fooBarLooFolder;
  private ControlledGcsBucketResource controlledBucket;
  private ControlledGcsBucketResource controlledBucket2;
  private ControlledBigQueryDatasetResource controlledBqDataset;
  private ControlledAiNotebookInstanceResource controlledAiNotebook;
  private ReferencedGcsBucketResource referencedBucket;
  private ReferencedBigQueryDataTableResource referencedBqTable;
  private ReferencedDataRepoSnapshotResource referencedDataRepoSnapshotResource;
  private ReferencedGitRepoResource referencedGitRepoResource;

  @BeforeEach
  public void setUp() {
    workspaceId = workspaceConnectedTestUtils.createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest()).getWorkspaceId();
    fooFolder = createFolder("foo", null);
    fooBarFolder = createFolder("bar", fooFolder.id());
    fooFooFolder = createFolder("Foo", fooFolder.id());
    fooBarLooFolder = createFolder("loo", fooBarFolder.id());
    controlledBucket =
        ControlledGcsBucketResource.builder()
            .common(createControlledResourceCommonFieldWithFolderId(workspaceId, fooFolder.id()))
            .bucketName(TestUtils.appendRandomNumber("my-gcs-bucket"))
            .build();
    resourceDao.createControlledResource(controlledBucket);
    controlledBucket2 =
        ControlledGcsBucketResource.builder()
            .common(createControlledResourceCommonFieldWithFolderId(workspaceId, fooFooFolder.id()))
            .bucketName(TestUtils.appendRandomNumber("my-gcs-bucket-2"))
            .build();
    resourceDao.createControlledResource(controlledBucket2);
    controlledBqDataset =
        ControlledBigQueryDatasetResource.builder()
            .common(createControlledResourceCommonFieldWithFolderId(workspaceId, fooBarFolder.id()))
            .datasetName(TestUtils.appendRandomNumber("my_bq_dataset").replace("-", "_"))
            .projectId("my-gcp-project")
            .build();
    resourceDao.createControlledResource(controlledBqDataset);
    controlledAiNotebook =
        ControlledAiNotebookInstanceResource.builder()
            .common(
                makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(workspaceId)
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                    .managedBy(ManagedByType.MANAGED_BY_USER)
                    .assignedUser("yuhu@hello")
                    .properties(
                        Map.of(ResourceProperties.FOLDER_ID_KEY, fooBarLooFolder.id().toString()))
                    .build())
            .projectId("my-gcp-project")
            .instanceId(TestUtils.appendRandomNumber("my-ai-notebook-instance"))
            .location("us-east1-b")
            .build();
    resourceDao.createControlledResource(controlledAiNotebook);
    referencedBucket =
        ReferencedGcsBucketResource.builder()
            .wsmResourceFields(
                createWsmResourceCommonFieldsWithFolderId(workspaceId, fooFooFolder.id()))
            .bucketName("my-awesome-bucket")
            .build();
    resourceDao.createReferencedResource(referencedBucket);
    referencedBqTable =
        ReferencedBigQueryDataTableResource.builder()
            .wsmResourceFields(
                createWsmResourceCommonFieldsWithFolderId(workspaceId, fooFolder.id()))
            .projectId("my-gcp-project")
            .datasetId("my_special_dataset")
            .dataTableId("my_secret_table")
            .build();
    resourceDao.createReferencedResource(referencedBqTable);
    referencedDataRepoSnapshotResource =
        ReferencedDataRepoSnapshotResource.builder()
            .wsmResourceFields(
                createWsmResourceCommonFieldsWithFolderId(workspaceId, fooBarFolder.id()))
            .snapshotId("a_snapshot_id")
            .instanceName("a_instance_name")
            .build();
    resourceDao.createReferencedResource(referencedDataRepoSnapshotResource);
    referencedGitRepoResource =
        ReferencedGitRepoResource.builder()
            .wsmResourceFields(
                createWsmResourceCommonFieldsWithFolderId(workspaceId, fooBarLooFolder.id()))
            .gitRepoUrl("https://github.com/foorepo")
            .build();
    resourceDao.createReferencedResource(referencedGitRepoResource);
  }

  @AfterEach
  public void cleanUp() {
    jobService.setFlightDebugInfoForTest(null);
   workspaceConnectedTestUtils.deleteWorkspaceAndGcpContext(userAccessUtils.defaultUserAuthRequest(), workspaceId);
  }

  @Test
  void deleteFolder_successWithStepRetry() {
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(DeleteReferencedResourcesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(DeleteFoldersStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build()
    );

    folderService.deleteFolder(workspaceId, fooFolder.id(), userAccessUtils.defaultUserAuthRequest());

    List<Folder> folders = List.of(fooFolder, fooBarFolder, fooFooFolder, fooBarLooFolder);
      for(Folder f: folders) {
        assertThrows(
            FolderNotFoundException.class,
            () -> folderService.getFolder(workspaceId, f.id())
        );
      }
      assertTrue(
          resourceDao.enumerateResources(workspaceId, null, null, 0, 100).isEmpty());
  }

  @Test
  void deleteFolder_activityLog() {
    folderService.deleteFolder(workspaceId, fooFooFolder.id(), userAccessUtils.defaultUserAuthRequest());

  }

  @Test
  void deleteFolder_failsAtLastStep() {
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(DeleteReferencedResourcesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    //retrySteps.put(DeleteFoldersStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).lastStepFailure(true).build()
    );

    folderService.deleteFolder(workspaceId, fooFolder.id(), userAccessUtils.defaultUserAuthRequest());

    List<Folder> folders = List.of(fooFolder, fooBarFolder, fooFooFolder, fooBarLooFolder);
    for(Folder f: folders) {
      assertThrows(
          FolderNotFoundException.class,
          () -> folderService.getFolder(workspaceId, f.id())
      );
    }
    assertTrue(
        resourceDao.enumerateResources(workspaceId, null, null, 0, 100).isEmpty());
  }

  public Folder createFolder(String displayName, @Nullable UUID parentFolderId) {
    return folderService.createFolder(new Folder(UUID.randomUUID(), workspaceId, displayName, null, parentFolderId, Map.of()));
  }

  private static ControlledResourceFields createControlledResourceCommonFieldWithFolderId(
      UUID workspaceId, UUID folderId) {
    return makeDefaultControlledResourceFieldsBuilder()
        .workspaceUuid(workspaceId)
        .properties(Map.of(ResourceProperties.FOLDER_ID_KEY, folderId.toString()))
        .build();
  }

  private static WsmResourceFields createWsmResourceCommonFieldsWithFolderId(
      UUID workspaceId, UUID folderId) {
    return ReferenceResourceFixtures.makeDefaultWsmResourceFieldBuilder(workspaceId)
        .properties(Map.of(ResourceProperties.FOLDER_ID_KEY, folderId.toString()))
        .build();
  }
}
