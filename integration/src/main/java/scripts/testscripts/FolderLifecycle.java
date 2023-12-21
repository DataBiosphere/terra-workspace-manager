package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.FolderApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateFolderRequestBody;
import bio.terra.workspace.model.CreatedControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.Folder;
import bio.terra.workspace.model.GcpBigQueryDatasetAttributes;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.JobReport;
import bio.terra.workspace.model.JobReport.StatusEnum;
import bio.terra.workspace.model.JobResult;
import bio.terra.workspace.model.Property;
import bio.terra.workspace.model.UpdateFolderRequestBody;
import com.google.api.client.http.HttpStatusCodes;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.function.Executable;
import scripts.utils.BqDatasetUtils;
import scripts.utils.ClientTestUtils;
import scripts.utils.GcsBucketUtils;
import scripts.utils.NotebookUtils;
import scripts.utils.TestUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class FolderLifecycle extends WorkspaceAllocateTestScriptBase {

  private static final String TERRA_FOLDER_ID = "terra-folder-id";
  private FolderApi folderOwnerApi;
  private FolderApi folderWriterApi;
  private ReferencedGcpResourceApi referencedGcpResourceApi;
  private ControlledGcpResourceApi controlledGcpResourceApi;
  private ResourceApi resourceApi;

  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    // initialize workspace
    super.doSetup(testUsers, workspaceApi);

    TestUserSpecification workspaceOwner = testUsers.get(0);

    TestUserSpecification secondUser = testUsers.get(1);

    ApiClient ownerApiClient = ClientTestUtils.getClientForTestUser(workspaceOwner, server);
    ApiClient writerApiClient = ClientTestUtils.getClientForTestUser(secondUser, server);
    ClientTestUtils.grantRole(workspaceApi, getWorkspaceId(), secondUser, IamRole.WRITER);

    folderOwnerApi = new FolderApi(ownerApiClient);
    folderWriterApi = new FolderApi(writerApiClient);
    referencedGcpResourceApi = new ReferencedGcpResourceApi(ownerApiClient);
    controlledGcpResourceApi = new ControlledGcpResourceApi(ownerApiClient);
    resourceApi = new ResourceApi(ownerApiClient);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    var displayName = TestUtils.appendRandomNumber("foo");
    var description = String.format("This is a top-level folder %s", displayName);
    Folder folderFoo =
        folderOwnerApi.createFolder(
            new CreateFolderRequestBody().displayName(displayName).description(description),
            getWorkspaceId());

    assertEquals(displayName, folderFoo.getDisplayName());
    assertEquals(description, folderFoo.getDescription());
    assertNull(folderFoo.getParentFolderId());

    // THIS TEST IS BROKEN AND NOT RUN: YOU NEED TO MAKE A CLOUD CONTEXT TO
    // BE ABLE TO MAKE CONTROLLED RESOURCES. AND NEED TO WAIT FOR PERMISSIONS
    // TO BE GRANTED AFTER THE CLOUD CONTEXT IS CREATED

    // Add a bucket to foo.
    CreatedControlledGcpGcsBucket controlledGcsBucketInFoo =
        GcsBucketUtils.makeControlledGcsBucketUserShared(
            controlledGcpResourceApi,
            getWorkspaceId(),
            "my-shared-foo-bucket",
            CloningInstructionsEnum.DEFINITION);
    resourceApi.updateResourceProperties(
        List.of(new Property().key(TERRA_FOLDER_ID).value(folderFoo.getId().toString())),
        getWorkspaceId(),
        controlledGcsBucketInFoo.getResourceId());

    // Create folder bar under foo.
    var displayNameBar = TestUtils.appendRandomNumber("bar");
    var descriptionBar = String.format("This is a second-level folder %s", displayNameBar);
    Folder folderBar =
        folderOwnerApi.createFolder(
            new CreateFolderRequestBody()
                .displayName(displayNameBar)
                .description(descriptionBar)
                .parentFolderId(folderFoo.getId()),
            getWorkspaceId());

    assertEquals(displayNameBar, folderBar.getDisplayName());
    assertEquals(descriptionBar, folderBar.getDescription());
    assertEquals(folderFoo.getId(), folderBar.getParentFolderId());

    // Add a big query dataset to bar.
    GcpBigQueryDatasetResource controlledBqDatasetInBar =
        BqDatasetUtils.makeControlledBigQueryDatasetUserShared(
            controlledGcpResourceApi,
            getWorkspaceId(),
            "bar-bq-dataset",
            null,
            CloningInstructionsEnum.DEFINITION);
    resourceApi.updateResourceProperties(
        List.of(new Property().key(TERRA_FOLDER_ID).value(folderBar.getId().toString())),
        getWorkspaceId(),
        controlledBqDatasetInBar.getMetadata().getResourceId());

    // Create folder Loo under Bar.
    var displayNameLoo = TestUtils.appendRandomNumber("Loo");
    var descriptionLoo = String.format("This is a third-level folder %s", displayNameLoo);
    Folder folderLoo =
        folderWriterApi.createFolder(
            new CreateFolderRequestBody()
                .displayName(displayNameLoo)
                .description(descriptionLoo)
                .parentFolderId(folderBar.getId()),
            getWorkspaceId());

    // Add a big query dataset to loo.
    GcpBigQueryDatasetResource referencedBqDatasetInLoo =
        BqDatasetUtils.makeBigQueryDatasetReference(
            new GcpBigQueryDatasetAttributes()
                .projectId("my-gcp-project")
                .datasetId("referenceddataset"),
            referencedGcpResourceApi,
            getWorkspaceId(),
            "loo-referenced-dataset");
    resourceApi.updateResourceProperties(
        List.of(new Property().key(TERRA_FOLDER_ID).value(folderLoo.getId().toString())),
        getWorkspaceId(),
        controlledBqDatasetInBar.getMetadata().getResourceId());
    // Add a private notebook to loo for the first user.
    CreatedControlledGcpAiNotebookInstanceResult firstUserPrivateBucket =
        NotebookUtils.makeControlledNotebookUserPrivate(
            getWorkspaceId(),
            /* instanceId= */ RandomStringUtils.randomAlphabetic(8).toLowerCase(),
            /* location= */ null,
            controlledGcpResourceApi,
            /* testValue= */ null,
            /* postStartupScript= */ null);
    resourceApi.updateResourceProperties(
        List.of(new Property().key(TERRA_FOLDER_ID).value(folderLoo.getId().toString())),
        getWorkspaceId(),
        firstUserPrivateBucket.getAiNotebookInstance().getMetadata().getResourceId());
    // Add a private notebook to loo for second user.
    CreatedControlledGcpAiNotebookInstanceResult secondUserPrivateBucket =
        NotebookUtils.makeControlledNotebookUserPrivate(
            getWorkspaceId(),
            /* instanceId= */ RandomStringUtils.randomAlphabetic(8).toLowerCase(),
            /* location= */ null,
            controlledGcpResourceApi,
            /* testValue= */ null,
            /* postStartupScript= */ null);
    resourceApi.updateResourceProperties(
        List.of(new Property().key(TERRA_FOLDER_ID).value(folderLoo.getId().toString())),
        getWorkspaceId(),
        secondUserPrivateBucket.getAiNotebookInstance().getMetadata().getResourceId());

    var newDisplayName = TestUtils.appendRandomNumber("newBar");
    var newDescription = "This is an updated bar folder";
    // Update name and description of folder bar.
    Folder updatedFolder =
        folderWriterApi.updateFolder(
            new UpdateFolderRequestBody().description(newDescription).displayName(newDisplayName),
            getWorkspaceId(),
            folderBar.getId());
    assertEquals(newDisplayName, updatedFolder.getDisplayName());
    assertEquals(newDescription, updatedFolder.getDescription());

    // Update folder bar to a top-level folder.
    Folder updatedFolder2 =
        folderOwnerApi.updateFolder(
            new UpdateFolderRequestBody().parentFolderId(null).updateParent(true),
            getWorkspaceId(),
            folderBar.getId());
    assertNull(updatedFolder2.getParentFolderId());
    assertEquals(newDisplayName, updatedFolder2.getDisplayName());
    assertEquals(newDescription, updatedFolder2.getDescription());

    // Get folder bar.
    Folder retrievedFolder2 = folderOwnerApi.getFolder(getWorkspaceId(), folderBar.getId());
    assertEquals(updatedFolder2, retrievedFolder2);

    // Second user unable to delete folder Loo. Loo has a private notebook owned by first user.
    // Second user is only workspace writer.
    assertApiCallThrows(
        () -> folderWriterApi.deleteFolderAsync(getWorkspaceId(), folderLoo.getId()),
        HttpStatusCodes.STATUS_CODE_FORBIDDEN);
    // Second user tries to delete foo and success
    JobReport deleteFolderFooJobReport = deleteFolderAndWaitForJobToFinish(folderFoo);
    assertEquals(StatusEnum.SUCCEEDED, deleteFolderFooJobReport.getStatus());
    assertApiCallThrows(
        () -> folderOwnerApi.getFolder(getWorkspaceId(), folderFoo.getId()),
        HttpStatusCodes.STATUS_CODE_NOT_FOUND);
    assertApiCallThrows(
        () ->
            controlledGcpResourceApi.getBucket(
                getWorkspaceId(), controlledGcsBucketInFoo.getResourceId()),
        HttpStatusCodes.STATUS_CODE_NOT_FOUND);

    // First user delete folder bar and success.
    JobReport deleteFolderBarJobReport = deleteFolderAndWaitForJobToFinish(folderBar);
    assertEquals(StatusEnum.SUCCEEDED, deleteFolderBarJobReport.getStatus());
    // All the resources in bar and its sub-folder loo are deleted.
    assertApiCallThrows(
        () -> folderOwnerApi.getFolder(getWorkspaceId(), folderBar.getId()),
        HttpStatusCodes.STATUS_CODE_NOT_FOUND);
    assertApiCallThrows(
        () -> folderOwnerApi.getFolder(getWorkspaceId(), folderLoo.getId()),
        HttpStatusCodes.STATUS_CODE_NOT_FOUND);
    assertApiCallThrows(
        () ->
            controlledGcpResourceApi.getBigQueryDataset(
                getWorkspaceId(), controlledBqDatasetInBar.getMetadata().getResourceId()),
        HttpStatusCodes.STATUS_CODE_NOT_FOUND);
    assertApiCallThrows(
        () ->
            controlledGcpResourceApi.getBigQueryDataset(
                getWorkspaceId(), referencedBqDatasetInLoo.getMetadata().getResourceId()),
        HttpStatusCodes.STATUS_CODE_NOT_FOUND);
    assertApiCallThrows(
        () ->
            controlledGcpResourceApi.getAiNotebookInstance(
                getWorkspaceId(),
                firstUserPrivateBucket.getAiNotebookInstance().getMetadata().getResourceId()),
        HttpStatusCodes.STATUS_CODE_NOT_FOUND);
    assertApiCallThrows(
        () ->
            controlledGcpResourceApi.getAiNotebookInstance(
                getWorkspaceId(),
                secondUserPrivateBucket.getAiNotebookInstance().getMetadata().getResourceId()),
        HttpStatusCodes.STATUS_CODE_NOT_FOUND);
  }

  private JobReport deleteFolderAndWaitForJobToFinish(Folder folderFoo)
      throws ApiException, InterruptedException {
    JobResult jobResult = folderWriterApi.deleteFolderAsync(getWorkspaceId(), folderFoo.getId());
    JobReport jobReport = jobResult.getJobReport();
    while (ClientTestUtils.jobIsRunning(jobReport)) {
      TimeUnit.SECONDS.sleep(10);
      jobReport =
          folderWriterApi
              .getDeleteFolderResult(getWorkspaceId(), folderFoo.getId(), jobReport.getId())
              .getJobReport();
    }
    return jobReport;
  }

  private static void assertApiCallThrows(Executable executable, int code) {
    var ex = assertThrows(ApiException.class, executable);
    assertEquals(code, ex.getCode());
  }
}
