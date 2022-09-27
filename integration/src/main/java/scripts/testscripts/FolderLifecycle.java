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
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.Folder;
import bio.terra.workspace.model.GcpBigQueryDatasetAttributes;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.Property;
import bio.terra.workspace.model.UpdateFolderRequestBody;
import com.google.api.client.http.HttpStatusCodes;
import java.util.List;
import org.junit.jupiter.api.function.Executable;
import scripts.utils.BqDatasetUtils;
import scripts.utils.ClientTestUtils;
import scripts.utils.GcsBucketUtils;
import scripts.utils.TestUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class FolderLifecycle extends WorkspaceAllocateTestScriptBase {

  private static final String TERRA_FOLDER_ID = "terra-folder-id";
  private FolderApi folderApi;
  private ReferencedGcpResourceApi referencedGcpResourceApi;
  private ControlledGcpResourceApi controlledGcpResourceApi;
  private ResourceApi resourceApi;

  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    // initialize workspace
    super.doSetup(testUsers, workspaceApi);

    TestUserSpecification workspaceOwner = testUsers.get(0);

    ApiClient ownerApiClient = ClientTestUtils.getClientForTestUser(workspaceOwner, server);

    folderApi = new FolderApi(ownerApiClient);
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
        folderApi.createFolder(
            new CreateFolderRequestBody().displayName(displayName).description(description),
            getWorkspaceId());

    assertEquals(displayName, folderFoo.getDisplayName());
    assertEquals(description, folderFoo.getDescription());
    assertNull(folderFoo.getParentFolderId());

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
        folderApi.createFolder(
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
        folderApi.createFolder(
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

    var newDisplayName = TestUtils.appendRandomNumber("newBar");
    var newDescription = "This is an updated bar folder";
    // Update name and description of folder bar.
    Folder updatedFolder =
        folderApi.updateFolder(
            new UpdateFolderRequestBody().description(newDescription).displayName(newDisplayName),
            getWorkspaceId(),
            folderBar.getId());
    assertEquals(newDisplayName, updatedFolder.getDisplayName());
    assertEquals(newDescription, updatedFolder.getDescription());

    // Update folder bar to a top-level folder.
    Folder updatedFolder2 =
        folderApi.updateFolder(
            new UpdateFolderRequestBody().parentFolderId(null).updateParent(true),
            getWorkspaceId(),
            folderBar.getId());
    assertNull(updatedFolder2.getParentFolderId());
    assertEquals(newDisplayName, updatedFolder2.getDisplayName());
    assertEquals(newDescription, updatedFolder2.getDescription());

    // Get folder bar.
    Folder retrievedFolder2 = folderApi.getFolder(getWorkspaceId(), folderBar.getId());
    assertEquals(updatedFolder2, retrievedFolder2);

    // Delete folder bar.
    folderApi.deleteFolder(getWorkspaceId(), folderBar.getId());

    // All the resources in bar and its sub-folder loo are deleted.
    assertApiCallThrows404(() -> folderApi.getFolder(getWorkspaceId(), folderBar.getId()));
    assertApiCallThrows404(() -> folderApi.getFolder(getWorkspaceId(), folderLoo.getId()));
    assertApiCallThrows404(
        () ->
            controlledGcpResourceApi.getBigQueryDataset(
                getWorkspaceId(), controlledBqDatasetInBar.getMetadata().getResourceId()));
    assertApiCallThrows404(
        () ->
            controlledGcpResourceApi.getBigQueryDataset(
                getWorkspaceId(), referencedBqDatasetInLoo.getMetadata().getResourceId()));
  }

  private static void assertApiCallThrows404(Executable executable) {
    var ex = assertThrows(ApiException.class, executable);
    assertEquals(HttpStatusCodes.STATUS_CODE_NOT_FOUND, ex.getCode());
  }
}
