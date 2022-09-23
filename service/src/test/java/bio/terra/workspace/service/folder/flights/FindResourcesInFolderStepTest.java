package bio.terra.workspace.service.folder.flights;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Stairway;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo.ReferencedGitRepoResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ReferencedResourceKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

@TestInstance(Lifecycle.PER_CLASS)
public class FindResourcesInFolderStepTest extends BaseUnitTest {

  @MockBean private FlightContext mockFlightContext;
  @MockBean private Stairway mockStairway;

  @Autowired FolderDao folderDao;
  @Autowired ResourceDao resourceDao;
  @Autowired WorkspaceDao workspaceDao;

  private FindResourcesInFolderStep findResourcesInFolderStep;
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

  @BeforeAll
  public void setUp() {
    workspaceId = WorkspaceFixtures.createGcpWorkspace(workspaceDao);
    fooFolder =
        folderDao.createFolder(
            new Folder(UUID.randomUUID(), workspaceId, "foo", null, null, Map.of()));
    fooBarFolder =
        folderDao.createFolder(
            new Folder(UUID.randomUUID(), workspaceId, "bar", null, fooFolder.id(), Map.of()));
    fooFooFolder =
        folderDao.createFolder(
            new Folder(UUID.randomUUID(), workspaceId, "foo", null, fooFolder.id(), Map.of()));
    fooBarLooFolder =
        folderDao.createFolder(
            new Folder(UUID.randomUUID(), workspaceId, "loo", null, fooBarFolder.id(), Map.of()));

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
            .location("us-east1")
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

  @AfterAll
  public void cleanUp() {
    workspaceDao.deleteCloudContext(workspaceId, CloudPlatform.GCP);
    workspaceDao.deleteWorkspace(workspaceId);
  }

  @Test
  public void doStep_success() throws InterruptedException {
    findResourcesInFolderStep =
        new FindResourcesInFolderStep(workspaceId, fooFolder.id(), folderDao, resourceDao);
    var workingMap = new FlightMap();
    when(mockFlightContext.getWorkingMap()).thenReturn(workingMap);
    when(mockFlightContext.getStairway()).thenReturn(mockStairway);
    when(mockStairway.createFlightId()).thenReturn(UUID.randomUUID().toString());
    List<WsmResource> controlledResources =
        List.of(controlledBucket, controlledBucket2, controlledBqDataset, controlledAiNotebook);
    List<WsmResource> referencedResources =
        List.of(
            referencedBqTable,
            referencedBucket,
            referencedGitRepoResource,
            referencedDataRepoSnapshotResource);

    findResourcesInFolderStep.doStep(mockFlightContext);

    ArrayList<WsmResource> controlledResourcesToDelete =
        workingMap.get(ControlledResourceKeys.RESOURCES_TO_DELETE, new TypeReference<>() {});
    ArrayList<WsmResource> referencedResourcesToDelete =
        workingMap.get(ReferencedResourceKeys.RESOURCES_TO_DELETE, new TypeReference<>() {});

    assertTrue(controlledResources.containsAll(controlledResourcesToDelete));
    assertEquals(4, controlledResourcesToDelete.size());
    assertTrue(referencedResources.containsAll(referencedResourcesToDelete));
    assertEquals(4, referencedResourcesToDelete.size());
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
