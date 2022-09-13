package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.FolderApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CreateFolderRequestBody;
import bio.terra.workspace.model.FolderDescription;
import bio.terra.workspace.model.UpdateFolderRequestBody;
import com.google.api.client.http.HttpStatusCodes;
import java.util.List;
import scripts.utils.ClientTestUtils;
import scripts.utils.TestUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class FolderLifecycle extends WorkspaceAllocateTestScriptBase {

  private FolderApi folderApi;

  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    // initialize workspace
    super.doSetup(testUsers, workspaceApi);

    TestUserSpecification workspaceOwner = testUsers.get(0);

    ApiClient ownerApiClient = ClientTestUtils.getClientForTestUser(workspaceOwner, server);

    folderApi = new FolderApi(ownerApiClient);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    var displayName = TestUtils.appendRandomNumber("foo");
    var description = String.format("This is a top-level folder %s", displayName);
    FolderDescription folderDescription =
        folderApi.createFolder(
            new CreateFolderRequestBody().displayName(displayName).description(description),
            getWorkspaceId());

    assertEquals(displayName, folderDescription.getDisplayName());
    assertEquals(description, folderDescription.getDescription());
    assertNull(folderDescription.getParentFolderId());

    var displayNameBar = TestUtils.appendRandomNumber("bar");
    var descriptionBar = String.format("This is a second-level folder %s", displayNameBar);
    FolderDescription folderBarDescription =
        folderApi.createFolder(
            new CreateFolderRequestBody()
                .displayName(displayNameBar)
                .description(descriptionBar)
                .parentFolderId(folderDescription.getId()),
            getWorkspaceId());

    assertEquals(displayNameBar, folderBarDescription.getDisplayName());
    assertEquals(descriptionBar, folderBarDescription.getDescription());
    assertEquals(folderDescription.getId(), folderBarDescription.getParentFolderId());

    var newDisplayName = TestUtils.appendRandomNumber("newBar");
    var newDescription = "This is an updated bar folder";

    FolderDescription updatedFolder =
        folderApi.updateFolder(
            new UpdateFolderRequestBody().description(newDescription).displayName(newDisplayName),
            getWorkspaceId(),
            folderBarDescription.getId());
    assertEquals(newDisplayName, updatedFolder.getDisplayName());
    assertEquals(newDescription, updatedFolder.getDescription());

    FolderDescription updatedFolder2 =
        folderApi.updateFolder(
            new UpdateFolderRequestBody().parentFolderId(null).updateParent(true),
            getWorkspaceId(),
            folderBarDescription.getId());
    assertNull(updatedFolder2.getParentFolderId());
    assertEquals(newDisplayName, updatedFolder2.getDisplayName());
    assertEquals(newDescription, updatedFolder2.getDescription());

    FolderDescription retrievedFolder2 =
        folderApi.getFolder(getWorkspaceId(), folderBarDescription.getId());
    assertEquals(updatedFolder2, retrievedFolder2);

    folderApi.deleteFolder(getWorkspaceId(), folderBarDescription.getId());

    var ex =
        assertThrows(
            ApiException.class,
            () -> folderApi.getFolder(getWorkspaceId(), folderBarDescription.getId()));
    assertEquals(HttpStatusCodes.STATUS_CODE_NOT_FOUND, ex.getCode());
  }
}
