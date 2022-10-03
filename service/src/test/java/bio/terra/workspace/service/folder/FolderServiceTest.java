package bio.terra.workspace.service.folder;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.exception.FolderNotFoundException;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.folder.flights.DeleteFolderRecursiveStep;
import bio.terra.workspace.service.folder.flights.DeleteReferencedResourcesStep;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo.ReferencedGitRepoResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FolderServiceTest extends BaseConnectedTest {

  @Autowired private FolderService folderService;
  @Autowired private WorkspaceConnectedTestUtils workspaceConnectedTestUtils;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private ResourceDao resourceDao;
  @Autowired private JobService jobService;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private ReferencedResourceService referencedResourceService;
  @Autowired private SamService samService;

  private UUID workspaceId;
  // foo/
  private Folder fooFolder;
  // foo/bar/
  private Folder fooBarFolder;
  // foo/foo/
  private Folder fooFooFolder;
  // foo/bar/loo
  private Folder fooBarLooFolder;
  private ControlledGcsBucketResource controlledBucketInFoo;
  private ControlledGcsBucketResource controlledBucket2InFooFoo;
  private ControlledBigQueryDatasetResource controlledBqDatasetInFooBar;
  private ControlledAiNotebookInstanceResource controlledAiNotebookInFooBarLoo;
  private ReferencedGcsBucketResource referencedBucketInFooFoo;
  private ReferencedBigQueryDataTableResource referencedBqTableInFoo;
  private ReferencedDataRepoSnapshotResource referencedDataRepoSnapshotInFooBar;
  private ReferencedGitRepoResource referencedGitRepoInFooBarLoo;

  @BeforeEach
  public void setUp() throws InterruptedException {
    workspaceId =
        workspaceConnectedTestUtils
            .createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest())
            .getWorkspaceId();
    samService.grantWorkspaceRole(
        workspaceId,
        userAccessUtils.defaultUserAuthRequest(),
        WsmIamRole.WRITER,
        userAccessUtils.getSecondUserEmail());
    fooFolder = createFolder("foo", null);
    fooBarFolder = createFolder("bar", fooFolder.id());
    fooFooFolder = createFolder("Foo", fooFolder.id());
    fooBarLooFolder = createFolder("loo", fooBarFolder.id());
    // First bucket is created by owner.
    controlledBucketInFoo =
        ControlledGcsBucketResource.builder()
            .common(createControlledResourceCommonFieldWithFolderId(workspaceId, fooFolder.id()))
            .bucketName(randomAlphabetic(10).toLowerCase(Locale.ROOT))
            .build();
    controlledResourceService.createControlledResourceSync(
        controlledBucketInFoo,
        /*privateResourceIamRole=*/ null,
        userAccessUtils.defaultUserAuthRequest(),
        ControlledResourceFixtures.getGoogleBucketCreationParameters());
    // Second bucket is created by writer.
    controlledBucket2InFooFoo =
        ControlledGcsBucketResource.builder()
            .common(createControlledResourceCommonFieldWithFolderId(workspaceId, fooFooFolder.id()))
            .bucketName(randomAlphabetic(10).toLowerCase(Locale.ROOT))
            .build();
    controlledResourceService.createControlledResourceSync(
        controlledBucket2InFooFoo,
        /*privateResourceIamRole=*/ null,
        userAccessUtils.secondUserAuthRequest(),
        ControlledResourceFixtures.getGoogleBucketCreationParameters());
    // bq dataset is created by owner.
    controlledBqDatasetInFooBar =
        ControlledBigQueryDatasetResource.builder()
            .common(createControlledResourceCommonFieldWithFolderId(workspaceId, fooBarFolder.id()))
            .datasetName(TestUtils.appendRandomNumber("my_bq_dataset"))
            .projectId("my-gcp-project")
            .build();
    controlledResourceService.createControlledResourceSync(
        controlledBqDatasetInFooBar,
        /*privateResourceIamRole=*/ null,
        userAccessUtils.defaultUserAuthRequest(),
        ControlledResourceFixtures.getGcpBigQueryDatasetCreationParameters());
    // First private AI notebook is created by owner.
    controlledAiNotebookInFooBarLoo =
        ControlledAiNotebookInstanceResource.builder()
            .common(
                makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(workspaceId)
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                    .managedBy(ManagedByType.MANAGED_BY_USER)
                    .assignedUser(userAccessUtils.getDefaultUserEmail())
                    .properties(
                        Map.of(ResourceProperties.FOLDER_ID_KEY, fooBarLooFolder.id().toString()))
                    .build())
            .projectId("my-gcp-project")
            .instanceId(TestUtils.appendRandomNumber("my-ai-notebook-instance"))
            .location("us-east1-b")
            .build();
    controlledResourceService.createAiNotebookInstance(
        controlledAiNotebookInFooBarLoo,
        ControlledResourceFixtures.defaultNotebookCreationParameters(),
        ControlledResourceIamRole.EDITOR,
        new ApiJobControl().id(UUID.randomUUID().toString()),
        "falseResultPath",
        userAccessUtils.defaultUserAuthRequest());

    // referenced bucket created by owner.
    referencedBucketInFooFoo =
        ReferencedGcsBucketResource.builder()
            .wsmResourceFields(
                createWsmResourceCommonFieldsWithFolderId(workspaceId, fooFooFolder.id()))
            .bucketName("my-awesome-bucket")
            .build();
    referencedResourceService.createReferenceResource(
        referencedBucketInFooFoo, userAccessUtils.defaultUserAuthRequest());
    // Referenced bq table is created by writer.
    referencedBqTableInFoo =
        ReferencedBigQueryDataTableResource.builder()
            .wsmResourceFields(
                createWsmResourceCommonFieldsWithFolderId(workspaceId, fooFolder.id()))
            .projectId("my-gcp-project")
            .datasetId("my_special_dataset")
            .dataTableId("my_secret_table")
            .build();
    referencedResourceService.createReferenceResource(
        referencedBqTableInFoo, userAccessUtils.secondUserAuthRequest());
    // data repo snapshot is created by writer.
    referencedDataRepoSnapshotInFooBar =
        ReferencedDataRepoSnapshotResource.builder()
            .wsmResourceFields(
                createWsmResourceCommonFieldsWithFolderId(workspaceId, fooBarFolder.id()))
            .snapshotId("a_snapshot_id")
            .instanceName("a_instance_name")
            .build();
    referencedResourceService.createReferenceResource(
        referencedDataRepoSnapshotInFooBar, userAccessUtils.secondUserAuthRequest());
    // Referenced git repo is created by owner.
    referencedGitRepoInFooBarLoo =
        ReferencedGitRepoResource.builder()
            .wsmResourceFields(
                createWsmResourceCommonFieldsWithFolderId(workspaceId, fooBarLooFolder.id()))
            .gitRepoUrl("https://github.com/foorepo")
            .build();
    referencedResourceService.createReferenceResource(
        referencedGitRepoInFooBarLoo, userAccessUtils.defaultUserAuthRequest());
  }

  @AfterEach
  public void cleanUp() {
    jobService.setFlightDebugInfoForTest(null);
    workspaceConnectedTestUtils.deleteWorkspaceAndGcpContext(
        userAccessUtils.defaultUserAuthRequest(), workspaceId);
  }

  @Test
  void deleteFolder_successWithStepRetry() {
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        DeleteReferencedResourcesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(DeleteFolderRecursiveStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    folderService.deleteFolder(
        workspaceId, fooFolder.id(), userAccessUtils.defaultUserAuthRequest());

    List<Folder> folders = List.of(fooFolder, fooBarFolder, fooFooFolder, fooBarLooFolder);
    for (Folder f : folders) {
      assertThrows(
          FolderNotFoundException.class, () -> folderService.getFolder(workspaceId, f.id()));
    }
    assertTrue(resourceDao.enumerateResources(workspaceId, null, null, 0, 100).isEmpty());
  }

  @Test
  void deleteFolder_writerHasNoAccessToOthersPrivateResource_throwsPermissionException() {
    assertThrows(
        ForbiddenException.class,
        () ->
            folderService.deleteFolder(
                workspaceId, fooBarLooFolder.id(), userAccessUtils.secondUserAuthRequest()));
  }

  @Test
  void deleteFolder_writerAndFolderDoesNotPrivateResource_success() {
    folderService.deleteFolder(
        workspaceId, fooFooFolder.id(), userAccessUtils.secondUserAuthRequest());

    assertThrows(
        FolderNotFoundException.class,
        () -> folderService.getFolder(workspaceId, fooFooFolder.id()));
  }

  @Test
  void deleteFolder_deleteSubFolder_success() {
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        DeleteReferencedResourcesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(DeleteFolderRecursiveStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    folderService.deleteFolder(
        workspaceId, fooBarFolder.id(), userAccessUtils.defaultUserAuthRequest());

    // assert foo/bar and foo/bar/loo are deleted.
    List<Folder> folders = List.of(fooBarFolder, fooBarLooFolder);
    for (Folder f : folders) {
      assertThrows(
          FolderNotFoundException.class, () -> folderService.getFolder(workspaceId, f.id()));
    }
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            resourceDao.getResource(
                workspaceId, referencedDataRepoSnapshotInFooBar.getResourceId()));
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            resourceDao.getResource(workspaceId, controlledAiNotebookInFooBarLoo.getResourceId()));
    assertThrows(
        ResourceNotFoundException.class,
        () -> resourceDao.getResource(workspaceId, referencedGitRepoInFooBarLoo.getResourceId()));
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            resourceDao.getResource(
                workspaceId, referencedDataRepoSnapshotInFooBar.getResourceId()));

    // assert foo and foo/foo and the resources in those are not deleted.
    assertEquals(4, resourceDao.enumerateResources(workspaceId, null, null, 0, 10).size());
    assertEquals(fooFolder, folderService.getFolder(workspaceId, fooFolder.id()));
    assertEquals(fooFooFolder, folderService.getFolder(workspaceId, fooFooFolder.id()));
  }

  @Test
  void deleteFolder_failsAtLastStep_throwsInvalidResultsStateException() {
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        DeleteReferencedResourcesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(DeleteFolderRecursiveStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().lastStepFailure(true).doStepFailures(retrySteps).build());

    // Service methods which wait for a flight to complete will throw an
    // InvalidResultStateException when that flight fails without a cause, which occurs when a
    // flight fails via debugInfo.
    assertThrows(
        InvalidResultStateException.class,
        () ->
            folderService.deleteFolder(
                workspaceId, fooFolder.id(), userAccessUtils.defaultUserAuthRequest()));

    List<Folder> folders = List.of(fooFolder, fooBarFolder, fooFooFolder, fooBarLooFolder);
    for (Folder f : folders) {
      assertThrows(
          FolderNotFoundException.class, () -> folderService.getFolder(workspaceId, f.id()));
    }
    assertTrue(resourceDao.enumerateResources(workspaceId, null, null, 0, 100).isEmpty());
  }

  public Folder createFolder(String displayName, @Nullable UUID parentFolderId) {
    return folderService.createFolder(
        new Folder(UUID.randomUUID(), workspaceId, displayName, null, parentFolderId, Map.of()));
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
