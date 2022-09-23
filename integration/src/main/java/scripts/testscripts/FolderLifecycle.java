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
import bio.terra.workspace.model.CreatedControlledGcpBigQueryDataset;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.Folder;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.Property;
import bio.terra.workspace.model.UpdateFolderRequestBody;
import com.google.api.client.http.HttpStatusCodes;
import java.util.List;
import scripts.utils.BqDatasetUtils;
import scripts.utils.ClientTestUtils;
import scripts.utils.GcsBucketUtils;
import scripts.utils.TestUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class FolderLifecycle extends WorkspaceAllocateTestScriptBase {

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
    CreatedControlledGcpGcsBucket createdControlledGcpGcsBucket = GcsBucketUtils.makeControlledGcsBucketUserShared(controlledGcpResourceApi, getWorkspaceId(), "my-shared-foo-bucket", CloningInstructionsEnum.DEFINITION);
    resourceApi.updateResourceProperties(List.of(new Property().key("terra-folder-id").value(folderFoo.getId().toString())), getWorkspaceId(), createdControlledGcpGcsBucket.getResourceId());

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

    // add a big query dataset to bar
    GcpBigQueryDatasetResource controlledGcpBigQueryDataset = BqDatasetUtils.makeControlledBigQueryDatasetUserShared(controlledGcpResourceApi, getWorkspaceId(), "bar-bq-dataset", null, CloningInstructionsEnum.DEFINITION);
    resourceApi.updateResourceProperties(List.of(new Property().key("terra-folder-id").value(folderBar.getId().toString())), getWorkspaceId(), controlledGcpBigQueryDataset.getMetadata()
        .getResourceId());

    var newDisplayName = TestUtils.appendRandomNumber("newBar");
    var newDescription = "This is an updated bar folder";

    Folder updatedFolder =
        folderApi.updateFolder(
            new UpdateFolderRequestBody().description(newDescription).displayName(newDisplayName),
            getWorkspaceId(),
            folderBar.getId());
    assertEquals(newDisplayName, updatedFolder.getDisplayName());
    assertEquals(newDescription, updatedFolder.getDescription());

    Folder updatedFolder2 =
        folderApi.updateFolder(
            new UpdateFolderRequestBody().parentFolderId(null).updateParent(true),
            getWorkspaceId(),
            folderBar.getId());
    assertNull(updatedFolder2.getParentFolderId());
    assertEquals(newDisplayName, updatedFolder2.getDisplayName());
    assertEquals(newDescription, updatedFolder2.getDescription());

    Folder retrievedFolder2 = folderApi.getFolder(getWorkspaceId(), folderBar.getId());
    assertEquals(updatedFolder2, retrievedFolder2);

    folderApi.deleteFolder(getWorkspaceId(), folderBar.getId());

    var ex =
        assertThrows(
            ApiException.class, () -> folderApi.getFolder(getWorkspaceId(), folderBar.getId()));
    assertEquals(HttpStatusCodes.STATUS_CODE_NOT_FOUND, ex.getCode());
    var ex2 = assertThrows(
        ApiException.class, () -> controlledGcpResourceApi.getBigQueryDataset(getWorkspaceId(), controlledGcpBigQueryDataset.getMetadata()
            .getResourceId())
    );
    assertEquals(HttpStatusCodes.STATUS_CODE_NOT_FOUND, ex2.getCode());
  }
}
