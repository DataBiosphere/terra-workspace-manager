package bio.terra.workspace.service.folder;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder;
import static bio.terra.workspace.common.utils.MockMvcUtils.DEFAULT_USER_EMAIL;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import bio.terra.workspace.service.job.JobService.JobResultOrException;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo.ReferencedGitRepoResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;


@Tag("connected")
@TestInstance(Lifecycle.PER_CLASS)
public class FolderServiceTest extends BaseConnectedTest {

  private static final UUID FOO_FOLDER_ID = UUID.randomUUID();
  private static final UUID FOO_BAR_FOLDER_ID = UUID.randomUUID();
  private static final UUID FOO_FOO_FOLDER_ID = UUID.randomUUID();
  private static final UUID FOO_BAR_LOO_FOLDER_ID = UUID.randomUUID();
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
  private ControlledGcsBucketResource controlledBucket2InFooFoo;
  private ControlledAiNotebookInstanceResource controlledAiNotebookInFooBarLoo;
  private ReferencedGcsBucketResource referencedBucketInFooFoo;
  private ReferencedBigQueryDataTableResource referencedBqTableInFoo;
  private ReferencedDataRepoSnapshotResource referencedDataRepoSnapshotInFooBar;
  private ReferencedGitRepoResource referencedGitRepoInFooBarLoo;

  @BeforeAll
  public void setUpBeforeAll() throws InterruptedException {
    workspaceId =
        workspaceConnectedTestUtils
            .createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest())
            .getWorkspaceId();
    samService.grantWorkspaceRole(
        workspaceId,
        userAccessUtils.defaultUserAuthRequest(),
        WsmIamRole.WRITER,
        userAccessUtils.getSecondUserEmail());

    // Second bucket is created by writer.
    controlledBucket2InFooFoo =
        ControlledGcsBucketResource.builder()
            .common(createControlledResourceCommonFieldWithFolderId(workspaceId, FOO_FOO_FOLDER_ID))
            .bucketName(randomAlphabetic(10).toLowerCase(Locale.ROOT))
            .build();

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
                        Map.of(ResourceProperties.FOLDER_ID_KEY, FOO_BAR_LOO_FOLDER_ID.toString()))
                    .build())
            .projectId("my-gcp-project")
            .instanceId(TestUtils.appendRandomNumber("my-ai-notebook-instance"))
            .location("us-east1-b")
            .build();
    // referenced bucket created by owner.
    referencedBucketInFooFoo =
        ReferencedGcsBucketResource.builder()
            .wsmResourceFields(
                createWsmResourceCommonFieldsWithFolderId(workspaceId, FOO_FOO_FOLDER_ID))
            .bucketName("my-awesome-bucket")
            .build();
    // Referenced bq table is created by writer.
    referencedBqTableInFoo =
        ReferencedBigQueryDataTableResource.builder()
            .wsmResourceFields(
                createWsmResourceCommonFieldsWithFolderId(workspaceId, FOO_FOLDER_ID))
            .projectId("my-gcp-project")
            .datasetId("my_special_dataset")
            .dataTableId("my_secret_table")
            .build();
    // data repo snapshot is created by writer.
    referencedDataRepoSnapshotInFooBar =
        ReferencedDataRepoSnapshotResource.builder()
            .wsmResourceFields(
                createWsmResourceCommonFieldsWithFolderId(workspaceId, FOO_BAR_FOLDER_ID))
            .snapshotId("a_snapshot_id")
            .instanceName("a_instance_name")
            .build();
    // Referenced git repo is created by owner.
    referencedGitRepoInFooBarLoo =
        ReferencedGitRepoResource.builder()
            .wsmResourceFields(
                createWsmResourceCommonFieldsWithFolderId(workspaceId, FOO_BAR_LOO_FOLDER_ID))
            .gitRepoUrl("https://github.com/foorepo")
            .build();
  }

  @AfterAll
  public void cleanUpAfterAll() {
    workspaceConnectedTestUtils.deleteWorkspaceAndGcpContext(
        userAccessUtils.defaultUserAuthRequest(), workspaceId);
  }

  @AfterEach
  public void cleanUp() {
    jobService.setFlightDebugInfoForTest(null);
  }

  @Test
  void deleteFolder_successWithStepRetry() {
    createFoldersAndResources();
    // foo/bar/loo contains a private notebook, thus the folder cannot be deleted by non-owner.
    assertThrows(
        ForbiddenException.class,
        () ->
            folderService.deleteFolder(
                workspaceId, FOO_BAR_LOO_FOLDER_ID, userAccessUtils.secondUserAuthRequest()));

    // foo/foo does not have private resource, thus can be deleted by non-owner
    var jobId =
        folderService.deleteFolder(
            workspaceId, FOO_FOO_FOLDER_ID, userAccessUtils.secondUserAuthRequest());

    jobService.waitForJob(jobId);
    JobResultOrException<Boolean> succeededJob = jobService.retrieveJobResult(jobId, Boolean.class);
    assertNull(succeededJob.getException());
    assertThrows(
        FolderNotFoundException.class,
        () -> folderService.getFolder(workspaceId, FOO_FOO_FOLDER_ID));

    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        DeleteReferencedResourcesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(DeleteFolderRecursiveStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    jobId =
        folderService.deleteFolder(
            workspaceId, FOO_FOLDER_ID, userAccessUtils.defaultUserAuthRequest());
    jobService.waitForJob(jobId);

    List<Folder> folders = List.of(fooFolder, fooBarFolder, fooFooFolder, fooBarLooFolder);
    for (Folder f : folders) {
      assertThrows(
          FolderNotFoundException.class, () -> folderService.getFolder(workspaceId, f.id()));
    }
    assertTrue(resourceDao.enumerateResources(workspaceId, null, null, 0, 100).isEmpty());
  }

  private void createFoldersAndResources() {
    fooFolder = createFolder("foo", FOO_FOLDER_ID, null);
    fooBarFolder = createFolder("bar", FOO_BAR_FOLDER_ID, FOO_FOLDER_ID);
    fooFooFolder = createFolder("Foo", FOO_FOO_FOLDER_ID, FOO_FOLDER_ID);
    fooBarLooFolder = createFolder("loo", FOO_BAR_LOO_FOLDER_ID, FOO_BAR_FOLDER_ID);

    controlledResourceService.createControlledResourceSync(
        controlledBucket2InFooFoo,
        /*privateResourceIamRole=*/ null,
        userAccessUtils.secondUserAuthRequest(),
        ControlledResourceFixtures.getGoogleBucketCreationParameters());

    controlledResourceService.createAiNotebookInstance(
        controlledAiNotebookInFooBarLoo,
        ControlledResourceFixtures.defaultNotebookCreationParameters(),
        ControlledResourceIamRole.EDITOR,
        new ApiJobControl().id(UUID.randomUUID().toString()),
        "falseResultPath",
        userAccessUtils.defaultUserAuthRequest());

    referencedResourceService.createReferenceResource(
        referencedBucketInFooFoo, userAccessUtils.defaultUserAuthRequest());

    referencedResourceService.createReferenceResource(
        referencedBqTableInFoo, userAccessUtils.secondUserAuthRequest());
    referencedResourceService.createReferenceResource(
        referencedDataRepoSnapshotInFooBar, userAccessUtils.secondUserAuthRequest());

    referencedResourceService.createReferenceResource(
        referencedGitRepoInFooBarLoo, userAccessUtils.defaultUserAuthRequest());
  }

  @Test
  void deleteFolder_failsAtLastStep_throwsInvalidResultsStateException() {
    fooFolder = createFolder("foo", FOO_FOLDER_ID, null);
    referencedResourceService.createReferenceResource(
        referencedBqTableInFoo, userAccessUtils.secondUserAuthRequest());
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        DeleteReferencedResourcesStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(DeleteFolderRecursiveStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().lastStepFailure(true).doStepFailures(retrySteps).build());

    var jobId =
        folderService.deleteFolder(
            workspaceId, FOO_FOLDER_ID, userAccessUtils.defaultUserAuthRequest());

    jobService.waitForJob(jobId);
    // Service methods which wait for a flight to complete will throw an
    // InvalidResultStateException when that flight fails without a cause, which occurs when a
    // flight fails via debugInfo.
    assertThrows(
        InvalidResultStateException.class,
        () -> jobService.retrieveJobResult(jobId, Boolean.class));
    assertThrows(
        FolderNotFoundException.class, () -> folderService.getFolder(workspaceId, FOO_FOLDER_ID));
    assertTrue(resourceDao.enumerateResources(workspaceId, null, null, 0, 100).isEmpty());
  }

  public Folder createFolder(String displayName, UUID folderId, @Nullable UUID parentFolderId) {
    return folderService.createFolder(
        new Folder(
            folderId,
            workspaceId,
            displayName,
            null,
            parentFolderId,
            Map.of(),
            DEFAULT_USER_EMAIL,
            null));
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
