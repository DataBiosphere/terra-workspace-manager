package bio.terra.workspace.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.exception.DuplicateFolderDisplayNameException;
import bio.terra.workspace.db.exception.DuplicateFolderIdException;
import bio.terra.workspace.db.exception.FolderNotFoundException;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
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
    var folder =
        getFolder(
            "foo",
            workspaceUuid,
            /*parentFolderId=*/ null,
            Map.of("foo", "bar", "cake", "chocolate"));

    var createdFolder = folderDao.createFolder(folder);

    assertEquals("foo", createdFolder.displayName());
    assertNull(createdFolder.parentFolderId());
    Map<String, String> properties = createdFolder.properties();
    assertEquals("bar", properties.get("foo"));
    assertEquals("chocolate", properties.get("cake"));
  }

  @Test
  public void createFolder_duplicateDisplayName_fails() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    var folder = getFolder("foo", workspaceUuid);
    var secondFolder = getFolder("foo", workspaceUuid);
    folderDao.createFolder(folder);

    assertThrows(
        DuplicateFolderDisplayNameException.class, () -> folderDao.createFolder(secondFolder));
  }

  @Test
  public void createFolder_duplicateFolderId_fails() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    var folder = getFolder("foo", workspaceUuid);
    folderDao.createFolder(folder);

    assertThrows(DuplicateFolderIdException.class, () -> folderDao.createFolder(folder));
  }

  @Test
  public void createFolder_duplicateName_fails() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    var folder = getFolder("foo", workspaceUuid);
    var secondFolder = getFolder("foo", workspaceUuid);
    folderDao.createFolder(folder);

    assertThrows(
        DuplicateFolderDisplayNameException.class, () -> folderDao.createFolder(secondFolder));
  }

  @Test
  public void createFolder_multipleFoldersAndLayers() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    var folder = getFolder("foo", workspaceUuid);
    var secondFolder = getFolder("bar", workspaceUuid);

    var createdFolder = folderDao.createFolder(folder);
    var createdSecondFolder = folderDao.createFolder(secondFolder);

    var thirdFolder = getFolder("foo", workspaceUuid, createdFolder.id());
    var createdThirdFolder = folderDao.createFolder(thirdFolder);

    assertEquals(folder, createdFolder);
    assertEquals(secondFolder, createdSecondFolder);
    assertEquals(thirdFolder, createdThirdFolder);
  }

  @Test
  public void listFolders_allFoldersInTheSameWorkspaceListed() {
    var workspaceUuid = UUID.randomUUID();
    var secondWorkspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    createWorkspace(secondWorkspaceUuid);
    var folder = getFolder("foo", workspaceUuid);
    var secondFolder = getFolder("bar", workspaceUuid);
    var thirdFolder = getFolder("foo", secondWorkspaceUuid);

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
    var folder = getFolder("foo", workspaceUuid);
    // second and third folders are under folder foo.
    var secondFolder = getFolder("bar", workspaceUuid, folder.id());
    var thirdFolder = getFolder("garrr", workspaceUuid, folder.id());

    var unused = folderDao.createFolder(folder);
    var createdSecondFolder = folderDao.createFolder(secondFolder);
    var createdThirdFolder = folderDao.createFolder(thirdFolder);

    List<Folder> retrievedFolders = folderDao.listFolders(workspaceUuid, folder.id());
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
    var folder = getFolder("foo", /*workspaceUuid=*/ UUID.randomUUID());
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
    Folder folder = getFolder("foo", workspaceUuid);
    Folder secondFolder = getFolder("bar", workspaceUuid);
    Folder createdFolder = folderDao.createFolder(folder);
    Folder secondCreatedFolder = folderDao.createFolder(secondFolder);

    var newDescription = "This is a new description";
    var newName = "newFoo";
    boolean updated =
        folderDao.updateFolder(
            workspaceUuid,
            createdFolder.id(),
            newName,
            newDescription,
            secondCreatedFolder.id(),
            /*updateParent=*/ true);

    assertTrue(updated);
    Folder updatedFolder = folderDao.getFolder(workspaceUuid, createdFolder.id());
    assertEquals(newName, updatedFolder.displayName());
    assertEquals(newDescription, updatedFolder.description());
    assertEquals(secondCreatedFolder.id(), updatedFolder.parentFolderId());

    boolean secondUpdate =
        folderDao.updateFolder(
            workspaceUuid, createdFolder.id(), null, null, null, /*updateParent=*/ true);

    assertTrue(secondUpdate);
    Folder secondUpdatedFolder = folderDao.getFolder(workspaceUuid, createdFolder.id());
    assertNull(secondUpdatedFolder.parentFolderId());
    // name and description not change.
    assertEquals(newName, secondUpdatedFolder.displayName());
    assertEquals(newDescription, secondUpdatedFolder.description());
  }

  @Test
  public void updateFolderProperties_updatesSuccessfully() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    Folder folder = getFolder("foo", workspaceUuid);
    Folder createdFolder = folderDao.createFolder(folder);

    folderDao.updateFolderProperties(
        workspaceUuid, createdFolder.id(), Map.of("foo", "bar1", "cake", "lava"));

    Folder updatedFolder = folderDao.getFolder(workspaceUuid, createdFolder.id());
    assertEquals("bar1", updatedFolder.properties().get("foo"));
    assertEquals("lava", updatedFolder.properties().get("cake"));
  }

  @Test
  public void updateFolderProperties_folderDoesNotExist_throwsFolderNotFoundException() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);

    assertThrows(
        FolderNotFoundException.class,
        () ->
            folderDao.updateFolderProperties(
                workspaceUuid, UUID.randomUUID(), Map.of("foo", "bar1", "cake", "lava")));
  }

  @Test
  public void updateFolderProperties_noUpdate_throwsMissingRequiredFieldsException() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);

    assertThrows(
        MissingRequiredFieldsException.class,
        () -> folderDao.updateFolderProperties(workspaceUuid, UUID.randomUUID(), Map.of()));
  }

  @Test
  public void deleteFolderProperties_updatesSuccessfully() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    Folder folder = getFolder("foo", workspaceUuid);
    Folder createdFolder = folderDao.createFolder(folder);
    assertEquals("bar", createdFolder.properties().get("foo"));

    folderDao.deleteFolderProperties(workspaceUuid, createdFolder.id(), List.of("foo", "cake"));

    Folder updatedFolder = folderDao.getFolder(workspaceUuid, createdFolder.id());
    assertFalse(updatedFolder.properties().containsKey("foo"));
  }

  @Test
  public void deleteFolderProperties_folderNotExist_throwsFolderNotFoundException() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);

    assertThrows(
        FolderNotFoundException.class,
        () ->
            folderDao.deleteFolderProperties(
                workspaceUuid, UUID.randomUUID(), List.of("foo", "cake")));
  }

  @Test
  public void deleteFolderProperties_nothingToDelete_throwsMissingRequiredFieldsException() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);

    assertThrows(
        MissingRequiredFieldsException.class,
        () -> folderDao.deleteFolderProperties(workspaceUuid, UUID.randomUUID(), List.of()));
  }

  @Test
  public void updateFolder_formCycle_throwsBadRequestException() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    Folder folder = getFolder("foo", workspaceUuid);
    Folder secondFolder = getFolder("bar", workspaceUuid, /*parentFolderId=*/ folder.id());
    Folder thirdFolder = getFolder("garr", workspaceUuid, /*parentFolderId=*/ secondFolder.id());
    Folder createdFolder = folderDao.createFolder(folder);
    Folder secondCreatedFolder = folderDao.createFolder(secondFolder);
    Folder thirdCreatedFolder = folderDao.createFolder(thirdFolder);

    // foo -> gar -> bar -> foo
    assertThrows(
        BadRequestException.class,
        () ->
            folderDao.updateFolder(
                workspaceUuid, createdFolder.id(), null, null, thirdCreatedFolder.id(), null));

    // foo -> bar -> foo
    assertThrows(
        BadRequestException.class,
        () ->
            folderDao.updateFolder(
                workspaceUuid, createdFolder.id(), null, null, secondCreatedFolder.id(), null));

    // foo -> foo
    assertThrows(
        BadRequestException.class,
        () ->
            folderDao.updateFolder(
                workspaceUuid, createdFolder.id(), null, null, createdFolder.id(), null));

    // bar -> garr -> bar
    assertThrows(
        BadRequestException.class,
        () ->
            folderDao.updateFolder(
                workspaceUuid,
                secondCreatedFolder.id(),
                null,
                null,
                thirdCreatedFolder.id(),
                null));
  }

  @Test
  public void updateFolder_duplicateFolderName_throwsException() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    Folder folder = getFolder("foo", workspaceUuid);
    Folder secondFolder = getFolder("bar", workspaceUuid);
    Folder createdFolder = folderDao.createFolder(folder);
    Folder unused = folderDao.createFolder(secondFolder);

    assertThrows(
        DuplicateFolderDisplayNameException.class,
        () ->
            folderDao.updateFolder(
                workspaceUuid,
                createdFolder.id(),
                "bar",
                /*description=*/ null,
                /*parentFolderId=*/ null,
                /*updateParent=*/ false));

    var updatedFolder = folderDao.getFolder(workspaceUuid, createdFolder.id());
    assertEquals(createdFolder.displayName(), updatedFolder.displayName());
    assertEquals(createdFolder.description(), updatedFolder.description());
  }

  @Test
  public void updateFolder_noFieldsAreUpdated_throwMissingFieldsException() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);
    Folder folder = getFolder("foo", workspaceUuid);
    Folder createdFolder = folderDao.createFolder(folder);

    assertThrows(
        MissingRequiredFieldsException.class,
        () ->
            folderDao.updateFolder(
                workspaceUuid,
                createdFolder.id(),
                /*displayName=*/ null,
                /*description=*/ null,
                /*parentFolderId=*/ null,
                /*updateParent=*/ false));
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
    var folder = getFolder("foo", workspaceUuid);
    // second and third folders are under folder foo.
    var secondFolder = getFolder("bar", workspaceUuid, folder.id());
    var thirdFolder = getFolder("garrr", workspaceUuid, folder.id());
    var createdFolder = folderDao.createFolder(folder);
    var createdSecondFolder = folderDao.createFolder(secondFolder);
    var createdThirdFolder = folderDao.createFolder(thirdFolder);

    boolean deleted = folderDao.deleteFolder(workspaceUuid, createdFolder.id());

    assertTrue(deleted);
    assertTrue(folderDao.getFolderIfExists(workspaceUuid, createdFolder.id()).isEmpty());
    assertTrue(folderDao.getFolderIfExists(workspaceUuid, createdSecondFolder.id()).isEmpty());
    assertTrue(folderDao.getFolderIfExists(workspaceUuid, createdThirdFolder.id()).isEmpty());
  }

  @Test
  public void deleteFolder_invalidFolder_nothingIsDeleted() {
    var workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid);

    assertFalse(folderDao.deleteFolder(workspaceUuid, UUID.randomUUID()));
  }

  private static Folder getFolder(String displayName, UUID workspaceUuid) {
    return getFolder(displayName, workspaceUuid, null, Map.of("foo", "bar"));
  }

  private static Folder getFolder(String displayName, UUID workspaceUuid, UUID parentFolderId) {
    return getFolder(displayName, workspaceUuid, parentFolderId, Map.of("foo", "bar"));
  }

  private static Folder getFolder(
      String displayName,
      UUID workspaceUuid,
      @Nullable UUID parentFolderId,
      Map<String, String> properties) {
    return new Folder(
        UUID.randomUUID(),
        workspaceUuid,
        displayName,
        String.format("This is %s folder", displayName),
        parentFolderId,
        properties);
  }
}
