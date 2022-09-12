package bio.terra.workspace.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.exception.DuplicateFolderDisplayNameException;
import bio.terra.workspace.db.exception.FolderNotFoundException;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

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
  public void createFolder_returnFolderWithId() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);

    var folder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);
    var createdFolder = folderDao.createFolder(folder);

    assertEquals(folder, createdFolder);
  }

  @Test
  public void createFolder_duplicateId_fails() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    var folder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);
    folderDao.createFolder(folder);

    assertThrows(DuplicateFolderDisplayNameException.class, () -> folderDao.createFolder(folder));
  }

  @Test
  public void createFolder_duplicateName_fails() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    var folder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);
    folderDao.createFolder(folder);

    assertThrows(DuplicateFolderDisplayNameException.class, () -> folderDao.createFolder(folder));
  }


  @Test
  public void createFolder_multipleFolders() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    var folder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);
    var secondFolder = getFolder("bar", workspaceUuid, /*parentFolderId=*/ null);

    var createdFolder = folderDao.createFolder(folder);
    var createdSecondFolder = folderDao.createFolder(secondFolder);

    var thirdFolder = getFolder("foo", workspaceUuid, createdFolder.getId());
    var createdThirdFolder = folderDao.createFolder(thirdFolder);

    assertEquals(folder, createdFolder);
    assertEquals(secondFolder, createdSecondFolder);
    assertEquals(thirdFolder, createdThirdFolder);
  }

  @Test
  public void createFolder_workspaceNotExist() {
    var folder = getFolder("foo", /*workspaceUuid=*/ UUID.randomUUID(), /*parentFolderId=*/ null);
    assertThrows(DataIntegrityViolationException.class, () -> folderDao.createFolder(folder));
  }

  @Test
  public void createFolder_parentFolderNotExist() {
    var workspaceId = UUID.randomUUID();
    createWorkspace(workspaceId);
    var folder = getFolder("foo", workspaceId, UUID.randomUUID());

    assertThrows(FolderNotFoundException.class, () -> folderDao.createFolder(folder));
  }

  @Test
  public void updateFolder() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    var folder = getFolder("foo", workspaceUuid, /*parentFolderId=*/ null);
    var secondFolder = getFolder("bar", workspaceUuid, /*parentFolderId=*/ null);
    var createdFolder = folderDao.createFolder(folder);
    var secondCreatedFolder = folderDao.createFolder(secondFolder);

    var newDescription = "This is a new description";
    var newName = "newFoo";
    folderDao.updateFolder(
        workspaceUuid, createdFolder.getId(), newName, newDescription, secondCreatedFolder.getId());

    var updatedFolder = folderDao.getFolder(workspaceUuid, createdFolder.getId());
    assertEquals(newName, updatedFolder.getDisplayName());
    assertEquals(newDescription, updatedFolder.getDescription().get());
    assertEquals(secondCreatedFolder.getId(), updatedFolder.getParentFolderId().get());
  }

  private static Folder getFolder(
      String displayName, UUID workspaceUuid, @Nullable UUID parentFolderId) {
    return new Folder.Builder()
        .displayName(displayName)
        .description(String.format("This is %s folder", displayName))
        .workspaceId(workspaceUuid)
        .id(UUID.randomUUID())
        .parentFolderId(Optional.ofNullable(parentFolderId))
        .build();
  }
}
