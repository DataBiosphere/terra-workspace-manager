package bio.terra.workspace.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.exception.DuplicateFolderDisplayNameException;
import bio.terra.workspace.db.exception.DuplicateFolderIdException;
import bio.terra.workspace.db.exception.FolderNotFoundException;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FolderDaoTest extends BaseUnitTest {

  @Autowired FolderDao folderDao;
  @Autowired WorkspaceDao workspaceDao;

  private UUID createWorkspace(UUID workspaceUuid) {
    Workspace workspace =
        Workspace.builder()
            .workspaceId(workspaceUuid)
            .userFacingId(workspaceUuid.toString())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceDao.createWorkspace(workspace);
    return workspace.getWorkspaceId();
  }

  @Test
  public void createFolder_returnSameFolder() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    var folder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);

    var createdFolder = folderDao.createFolder(folder);

    assertEquals(folder, createdFolder);
  }

  @Test
  public void createFolder_duplicateDisplayName_fails() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    var folder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);
    var secondFolder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);
    folderDao.createFolder(folder);

    assertThrows(
        DuplicateFolderDisplayNameException.class, () -> folderDao.createFolder(secondFolder));
  }

  @Test
  public void createFolder_duplicateFolderId_fails() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    var folder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);
    folderDao.createFolder(folder);

    assertThrows(DuplicateFolderIdException.class, () -> folderDao.createFolder(folder));
  }

  @Test
  public void createFolder_duplicateName_fails() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    var folder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);
    var secondFolder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);
    folderDao.createFolder(folder);

    assertThrows(
        DuplicateFolderDisplayNameException.class, () -> folderDao.createFolder(secondFolder));
  }

  @Test
  public void createFolder_multipleFoldersAndLayers() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    var folder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);
    var secondFolder = getFolder("bar", workspaceUuid, /*parentFolderId=*/ null);

    var createdFolder = folderDao.createFolder(folder);
    var createdSecondFolder = folderDao.createFolder(secondFolder);

    var thirdFolder = getFolder("foo", workspaceUuid, createdFolder.getId());
    var createdThirdFolder = folderDao.createFolder(thirdFolder);

    assertEquals(folder.withId(createdFolder.getId()), createdFolder);
    assertEquals(secondFolder.withId(createdSecondFolder.getId()), createdSecondFolder);
    assertEquals(thirdFolder.withId(createdThirdFolder.getId()), createdThirdFolder);
  }

  @Test
  public void listFolders_allFoldersInTheSameWorkspaceListed() {
    var workspaceUuid = UUID.randomUUID();
    var secondWorkspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    createWorkspace(secondWorkspaceUuid);
    var folder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);
    var secondFolder = getFolder("bar", workspaceUuid, /*parentFolderId=*/ null);
    var thirdFolder = getFolder("foo", secondWorkspaceUuid, /*parentFolderId=*/ null);

    var createdFolder = folderDao.createFolder(folder);
    var createdSecondFolder = folderDao.createFolder(secondFolder);
    var createdThirdFolder = folderDao.createFolder(thirdFolder);

    List<Folder> retrievedFoldersInWorkspace1 =
        folderDao.listFolders(workspaceUuid, /*parentFolderId=*/ null);
    var expectedFoldersInWorkspace1 = List.of(createdFolder, createdSecondFolder);
    assertEquals(expectedFoldersInWorkspace1, retrievedFoldersInWorkspace1);
    List<Folder> retrievedFolderInWorkspace2 =
        folderDao.listFolders(secondWorkspaceUuid, /*parentFolderId=*/ null);
    var expectedFoldersInWorkspace2 = List.of(createdThirdFolder);
    assertEquals(expectedFoldersInWorkspace2, retrievedFolderInWorkspace2);
  }

  @Test
  public void listFolders_listsSubFolders() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    var folder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);
    // second and third folders are under folder foo.
    var secondFolder = getFolder("bar", workspaceUuid, folder.getId());
    var thirdFolder = getFolder("garrr", workspaceUuid, folder.getId());

    var unused = folderDao.createFolder(folder);
    var createdSecondFolder = folderDao.createFolder(secondFolder);
    var createdThirdFolder = folderDao.createFolder(thirdFolder);

    List<Folder> retrievedFolders = folderDao.listFolders(workspaceUuid, folder.getId());
    var expectedFolders = List.of(createdSecondFolder, createdThirdFolder);
    assertEquals(expectedFolders, retrievedFolders);
  }

  @Test
  public void listFolders_parentFolderNotExist() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);

    ImmutableList<Folder> folders = folderDao.listFolders(workspaceUuid, UUID.randomUUID());
    assertTrue(folders.isEmpty());
  }

  @Test
  public void createFolder_workspaceNotExist() {
    var folder = getFolder("foo", /*workspaceUuid=*/ UUID.randomUUID(), /*parentFolderId=*/ null);
    assertThrows(WorkspaceNotFoundException.class, () -> folderDao.createFolder(folder));
  }

  @Test
  public void createFolder_parentFolderNotExist_returnsEmptyList() {
    var workspaceId = UUID.randomUUID();
    createWorkspace(workspaceId);
    var folder = getFolder("foo", workspaceId, UUID.randomUUID());

    assertThrows(FolderNotFoundException.class, () -> folderDao.createFolder(folder));
  }

  @Test
  public void updateFolder_updatesSuccessfully() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    Folder folder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);
    Folder secondFolder = getFolder("bar", workspaceUuid, /*parentFolderId=*/ null);
    Folder createdFolder = folderDao.createFolder(folder);
    Folder secondCreatedFolder = folderDao.createFolder(secondFolder);

    var newDescription = "This is a new description";
    var newName = "newFoo";
    boolean updated =
        folderDao.updateFolder(
            workspaceUuid,
            createdFolder.getId(),
            newName,
            newDescription,
            secondCreatedFolder.getId(),
            /*moveToTop=*/ false);

    assertTrue(updated);
    Folder updatedFolder = folderDao.getFolder(workspaceUuid, createdFolder.getId());
    assertEquals(newName, updatedFolder.getDisplayName());
    assertEquals(newDescription, updatedFolder.getDescription().get());
    assertEquals(secondCreatedFolder.getId(), updatedFolder.getParentFolderId().get());

    boolean secondUpdate =
        folderDao.updateFolder(
            workspaceUuid, createdFolder.getId(), null, null, null, /*moveToTop=*/ true);

    assertTrue(secondUpdate);
    Folder secondUpdatedFolder = folderDao.getFolder(workspaceUuid, createdFolder.getId());
    assertTrue(secondUpdatedFolder.getParentFolderId().isEmpty());
    // name and description not change.
    assertEquals(newName, secondUpdatedFolder.getDisplayName());
    assertEquals(newDescription, secondUpdatedFolder.getDescription().get());
  }

  @Test
  public void updateFolder_formCycle_throwsBadRequestException() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    Folder folder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);
    Folder secondFolder = getFolder("bar", workspaceUuid, /*parentFolderId=*/ folder.getId());
    Folder thirdFolder = getFolder("garr", workspaceUuid, /*parentFolderId=*/ secondFolder.getId());
    Folder createdFolder = folderDao.createFolder(folder);
    Folder secondCreatedFolder = folderDao.createFolder(secondFolder);
    Folder thirdCreatedFolder = folderDao.createFolder(thirdFolder);

    // foo -> gar -> bar -> foo
    assertThrows(
        BadRequestException.class,
        () ->
            folderDao.updateFolder(
                workspaceUuid,
                createdFolder.getId(),
                null,
                null,
                thirdCreatedFolder.getId(),
                null));

    // foo -> bar -> foo
    assertThrows(
        BadRequestException.class,
        () ->
            folderDao.updateFolder(
                workspaceUuid,
                createdFolder.getId(),
                null,
                null,
                secondCreatedFolder.getId(),
                null));

    // foo -> foo
    assertThrows(
        BadRequestException.class,
        () ->
            folderDao.updateFolder(
                workspaceUuid, createdFolder.getId(), null, null, createdFolder.getId(), null));

    // bar -> garr -> bar
    assertThrows(
        BadRequestException.class,
        () ->
            folderDao.updateFolder(
                workspaceUuid,
                secondCreatedFolder.getId(),
                null,
                null,
                thirdCreatedFolder.getId(),
                null));
  }

  @Test
  public void updateFolder_duplicateFolderName_throwsException() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    Folder folder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);
    Folder secondFolder = getFolder("bar", workspaceUuid, /*parentFolderId=*/ null);
    Folder createdFolder = folderDao.createFolder(folder);
    Folder unused = folderDao.createFolder(secondFolder);

    assertThrows(
        DuplicateFolderDisplayNameException.class,
        () ->
            folderDao.updateFolder(
                workspaceUuid,
                createdFolder.getId(),
                "bar", /*description*/
                null,
                /*parentFolderId=*/ null,
                /*moveToTop=*/ false));

    var updatedFolder = folderDao.getFolder(workspaceUuid, createdFolder.getId());
    assertEquals(createdFolder.getDisplayName(), updatedFolder.getDisplayName());
    assertEquals(createdFolder.getDescription().get(), updatedFolder.getDescription().get());
  }

  @Test
  public void updateFolder_noFieldsAreUpdated_throwMissingFieldsException() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    Folder folder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);
    Folder createdFolder = folderDao.createFolder(folder);

    assertThrows(
        MissingRequiredFieldException.class,
        () ->
            folderDao.updateFolder(
                workspaceUuid,
                createdFolder.getId(),
                null, /*description*/
                null,
                /*parentFolderId=*/ null,
                /*moveToTop=*/ false));
  }

  @Test
  public void getFolder_workspaceNotExistOrFolderNotExist_throwsFolderNotFoundException() {
    var workspaceId = UUID.randomUUID();
    assertThrows(
        FolderNotFoundException.class, () -> folderDao.getFolder(workspaceId, UUID.randomUUID()));

    createWorkspace(workspaceId);

    assertThrows(
        FolderNotFoundException.class, () -> folderDao.getFolder(workspaceId, UUID.randomUUID()));
  }

  @Test
  public void deleteFolder_subFolderDeleted() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    var folder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);
    // second and third folders are under folder foo.
    var secondFolder = getFolder("bar", workspaceUuid, folder.getId());
    var thirdFolder = getFolder("garrr", workspaceUuid, folder.getId());
    var createdFolder = folderDao.createFolder(folder);
    var createdSecondFolder = folderDao.createFolder(secondFolder);
    var createdThirdFolder = folderDao.createFolder(thirdFolder);

    boolean deleted = folderDao.deleteFolder(workspaceUuid, createdFolder.getId());

    assertTrue(deleted);
    assertTrue(folderDao.getFolderIfExists(workspaceUuid, createdFolder.getId()).isEmpty());
    assertTrue(folderDao.getFolderIfExists(workspaceUuid, createdSecondFolder.getId()).isEmpty());
    assertTrue(folderDao.getFolderIfExists(workspaceUuid, createdThirdFolder.getId()).isEmpty());
  }

  @Test
  public void deleteFolder_invalidFolder_nothingIsDeleted() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);

    assertFalse(folderDao.deleteFolder(workspaceUuid, UUID.randomUUID()));
  }

  private static Folder getFolder(
      String displayName, UUID workspaceUuid, @Nullable UUID parentFolderId) {
    return new Folder.Builder()
        .id(UUID.randomUUID())
        .displayName(displayName)
        .description(String.format("This is %s folder", displayName))
        .workspaceId(workspaceUuid)
        .parentFolderId(Optional.ofNullable(parentFolderId))
        .build();
  }
}
