package bio.terra.workspace.db;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.utils.WorkspaceUnitTestUtils.createWorkspaceWithoutCloudContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

  @Test
  public void createFolder_returnSameFolder() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);
    var folder =
        getFolder(
            "foo",
            workspaceUuid,
            /* parentFolderId= */ null,
            Map.of("foo", "bar", "cake", "chocolate"));

    var createdFolder = folderDao.createFolder(folder);

    assertEquals(folder, createdFolder);
  }

  @Test
  public void getFolder_createdDateNotNull() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);
    var folder =
        getFolder(
            "foo",
            workspaceUuid,
            /* parentFolderId= */ null,
            Map.of("foo", "bar", "cake", "chocolate"));
    var createdFolder = folderDao.createFolder(folder);

    var getFolder = folderDao.getFolderRequired(workspaceUuid, folder.id());

    assertEquals(getFolder, createdFolder);
    assertNotNull(getFolder.createdDate());
  }

  @Test
  public void createFolder_duplicateDisplayName_fails() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);
    var folder = getFolder("foo", workspaceUuid);
    var secondFolder = getFolder("foo", workspaceUuid);
    folderDao.createFolder(folder);

    assertThrows(
        DuplicateFolderDisplayNameException.class, () -> folderDao.createFolder(secondFolder));
  }

  @Test
  public void createFolder_duplicateFolderId_fails() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);
    var folder = getFolder("foo", workspaceUuid);
    folderDao.createFolder(folder);

    assertThrows(DuplicateFolderIdException.class, () -> folderDao.createFolder(folder));
  }

  @Test
  public void createFolder_duplicateName_fails() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);
    var folder = getFolder("foo", workspaceUuid);
    var secondFolder = getFolder("foo", workspaceUuid);
    folderDao.createFolder(folder);

    assertThrows(
        DuplicateFolderDisplayNameException.class, () -> folderDao.createFolder(secondFolder));
  }

  @Test
  public void createFolder_multipleFoldersAndLayers() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);
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
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);
    UUID secondWorkspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);
    var folder = getFolder("foo", workspaceUuid);
    var secondFolder = getFolder("bar", workspaceUuid);
    var thirdFolder = getFolder("foo", secondWorkspaceUuid);

    var createdFolder = folderDao.createFolder(folder);
    var createdSecondFolder = folderDao.createFolder(secondFolder);
    var createdThirdFolder = folderDao.createFolder(thirdFolder);

    List<Folder> retrievedFoldersInWorkspace1 = folderDao.listFoldersInWorkspace(workspaceUuid);
    var expectedFoldersInWorkspace1 = List.of(createdFolder, createdSecondFolder);
    assertEquals(expectedFoldersInWorkspace1, retrievedFoldersInWorkspace1);
    List<Folder> retrievedFolderInWorkspace2 =
        folderDao.listFoldersInWorkspace(secondWorkspaceUuid);
    var expectedFoldersInWorkspace2 = List.of(createdThirdFolder);
    assertEquals(expectedFoldersInWorkspace2, retrievedFolderInWorkspace2);
  }

  @Test
  public void listFolders_listsSubFolders() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);
    var folder = getFolder("foo", workspaceUuid);
    // foo/bar
    var secondFolder = getFolder("bar", workspaceUuid, folder.id());
    // foo/bar/garrr
    var thirdFolder = getFolder("garrr", workspaceUuid, secondFolder.id());

    var unused = folderDao.createFolder(folder);
    var createdSecondFolder = folderDao.createFolder(secondFolder);
    var createdThirdFolder = folderDao.createFolder(thirdFolder);

    List<Folder> retrievedFolders = folderDao.listFoldersRecursively(folder.id());
    var expectedFolders = List.of(folder, createdSecondFolder, createdThirdFolder);
    assertEquals(expectedFolders, retrievedFolders);
  }

  @Test
  public void listFolders_parentFolderNotExist() {
    ImmutableList<Folder> folders = folderDao.listFoldersRecursively(UUID.randomUUID());
    assertTrue(folders.isEmpty());
  }

  @Test
  public void createFolder_workspaceNotExist() {
    var folder = getFolder("foo", /* workspaceUuid= */ UUID.randomUUID());
    assertThrows(WorkspaceNotFoundException.class, () -> folderDao.createFolder(folder));
  }

  @Test
  public void createFolder_parentFolderNotExist_returnsEmptyList() {
    UUID workspaceId = createWorkspaceWithoutCloudContext(workspaceDao);
    var folder = getFolder("foo", workspaceId, UUID.randomUUID());

    assertThrows(FolderNotFoundException.class, () -> folderDao.createFolder(folder));
  }

  @Test
  public void updateFolder_updatesSuccessfully() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);
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
            /* updateParent= */ true);

    assertTrue(updated);
    Folder updatedFolder = folderDao.getFolderRequired(workspaceUuid, createdFolder.id());
    assertEquals(newName, updatedFolder.displayName());
    assertEquals(newDescription, updatedFolder.description());
    assertEquals(secondCreatedFolder.id(), updatedFolder.parentFolderId());

    boolean secondUpdate =
        folderDao.updateFolder(
            workspaceUuid, createdFolder.id(), null, null, null, /* updateParent= */ true);

    assertTrue(secondUpdate);
    Folder secondUpdatedFolder = folderDao.getFolderRequired(workspaceUuid, createdFolder.id());
    assertNull(secondUpdatedFolder.parentFolderId());
    // name and description not change.
    assertEquals(newName, secondUpdatedFolder.displayName());
    assertEquals(newDescription, secondUpdatedFolder.description());
  }

  @Test
  public void updateFolderProperties_updatesSuccessfully() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);
    Folder folder = getFolder("foo", workspaceUuid);
    Folder createdFolder = folderDao.createFolder(folder);

    folderDao.updateFolderProperties(
        workspaceUuid, createdFolder.id(), Map.of("foo", "bar1", "cake", "lava"));

    Folder updatedFolder = folderDao.getFolderRequired(workspaceUuid, createdFolder.id());
    assertEquals("bar1", updatedFolder.properties().get("foo"));
    assertEquals("lava", updatedFolder.properties().get("cake"));
  }

  @Test
  public void updateFolderProperties_folderDoesNotExist_throwsFolderNotFoundException() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);

    assertThrows(
        FolderNotFoundException.class,
        () ->
            folderDao.updateFolderProperties(
                workspaceUuid, UUID.randomUUID(), Map.of("foo", "bar1", "cake", "lava")));
  }

  @Test
  public void updateFolderProperties_noUpdate_throwsMissingRequiredFieldsException() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);

    assertThrows(
        MissingRequiredFieldsException.class,
        () -> folderDao.updateFolderProperties(workspaceUuid, UUID.randomUUID(), Map.of()));
  }

  @Test
  public void deleteFolderProperties_updatesSuccessfully() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);
    Folder folder = getFolder("foo", workspaceUuid);
    Folder createdFolder = folderDao.createFolder(folder);
    assertEquals("bar", createdFolder.properties().get("foo"));

    folderDao.deleteFolderProperties(workspaceUuid, createdFolder.id(), List.of("foo", "cake"));

    Folder updatedFolder = folderDao.getFolderRequired(workspaceUuid, createdFolder.id());
    assertFalse(updatedFolder.properties().containsKey("foo"));
  }

  @Test
  public void deleteFolderProperties_folderNotExist_throwsFolderNotFoundException() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);

    assertThrows(
        FolderNotFoundException.class,
        () ->
            folderDao.deleteFolderProperties(
                workspaceUuid, UUID.randomUUID(), List.of("foo", "cake")));
  }

  @Test
  public void deleteFolderProperties_nothingToDelete_throwsMissingRequiredFieldsException() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);

    assertThrows(
        MissingRequiredFieldsException.class,
        () -> folderDao.deleteFolderProperties(workspaceUuid, UUID.randomUUID(), List.of()));
  }

  @Test
  public void updateFolder_formCycle_throwsBadRequestException() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);
    Folder folder = getFolder("foo", workspaceUuid);
    Folder secondFolder = getFolder("bar", workspaceUuid, /* parentFolderId= */ folder.id());
    Folder thirdFolder = getFolder("garr", workspaceUuid, /* parentFolderId= */ secondFolder.id());
    Folder createdFolder = folderDao.createFolder(folder);
    Folder secondCreatedFolder = folderDao.createFolder(secondFolder);
    Folder thirdCreatedFolder = folderDao.createFolder(thirdFolder);

    // foo -> garr -> bar -> foo
    assertThrows(
        BadRequestException.class,
        () ->
            folderDao.updateFolder(
                workspaceUuid,
                createdFolder.id(),
                /* displayName= */ null,
                /* description= */ null,
                thirdCreatedFolder.id(),
                /* updateParent= */ true));

    // foo -> bar -> foo
    assertThrows(
        BadRequestException.class,
        () ->
            folderDao.updateFolder(
                workspaceUuid,
                createdFolder.id(),
                /* displayName= */ null,
                /* description= */ null,
                secondCreatedFolder.id(),
                /* updateParent= */ true));

    // foo -> foo
    assertThrows(
        BadRequestException.class,
        () ->
            folderDao.updateFolder(
                workspaceUuid,
                createdFolder.id(),
                /* displayName= */ null,
                /* description= */ null,
                createdFolder.id(),
                /* updateParent= */ true));

    // bar -> garr -> bar
    assertThrows(
        BadRequestException.class,
        () ->
            folderDao.updateFolder(
                workspaceUuid,
                secondCreatedFolder.id(),
                /* displayName= */ null,
                /* description= */ null,
                thirdCreatedFolder.id(),
                /* updateParent= */ true));
  }

  @Test
  public void updateFolder_duplicateFolderName_throwsException() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);
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
                /* description= */ null,
                /* parentFolderId= */ null,
                /* updateParent= */ false));

    var updatedFolder = folderDao.getFolderRequired(workspaceUuid, createdFolder.id());
    assertEquals(createdFolder.displayName(), updatedFolder.displayName());
    assertEquals(createdFolder.description(), updatedFolder.description());
  }

  @Test
  public void updateFolder_noFieldsAreUpdated_throwMissingFieldsException() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);
    Folder folder = getFolder("foo", workspaceUuid);
    Folder createdFolder = folderDao.createFolder(folder);

    assertThrows(
        MissingRequiredFieldsException.class,
        () ->
            folderDao.updateFolder(
                workspaceUuid,
                createdFolder.id(),
                /* displayName= */ null,
                /* description= */ null,
                /* parentFolderId= */ null,
                /* updateParent= */ false));
  }

  @Test
  public void getFolder_workspaceNotExistOrFolderNotExist_throwsFolderNotFoundException() {

    assertThrows(
        FolderNotFoundException.class,
        () ->
            folderDao.getFolderRequired(
                /* workspaceId= */ UUID.randomUUID(), /* folderId= */ UUID.randomUUID()));

    UUID workspaceId = createWorkspaceWithoutCloudContext(workspaceDao);

    assertThrows(
        FolderNotFoundException.class,
        () -> folderDao.getFolderRequired(workspaceId, /* folderId= */ UUID.randomUUID()));
  }

  @Test
  public void deleteFoldersRecursive_subFolderDeleted() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);
    var folder = getFolder("foo", workspaceUuid);
    // second and third folders are under folder foo.
    var secondFolder = getFolder("bar", workspaceUuid, folder.id());
    var thirdFolder = getFolder("garrr", workspaceUuid, folder.id());
    var createdFolder = folderDao.createFolder(folder);
    var createdSecondFolder = folderDao.createFolder(secondFolder);
    var createdThirdFolder = folderDao.createFolder(thirdFolder);

    boolean deleted = folderDao.deleteFoldersRecursive(workspaceUuid, createdFolder.id());

    assertTrue(deleted);
    assertTrue(folderDao.getFolderIfExists(workspaceUuid, createdFolder.id()).isEmpty());
    assertTrue(folderDao.getFolderIfExists(workspaceUuid, createdSecondFolder.id()).isEmpty());
    assertTrue(folderDao.getFolderIfExists(workspaceUuid, createdThirdFolder.id()).isEmpty());
  }

  @Test
  public void deleteFoldersRecursive_invalidFolder_nothingIsDeleted() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);

    assertFalse(folderDao.deleteFoldersRecursive(workspaceUuid, UUID.randomUUID()));
  }

  @Test
  public void deleteAllFolders_noFolder_returnsFalse() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);

    assertFalse(folderDao.deleteAllFolders(workspaceUuid));
  }

  @Test
  public void deleteAllFolders_workspaceNotFound_returnsFalse() {
    assertFalse(folderDao.deleteAllFolders(UUID.randomUUID()));
  }

  @Test
  public void deleteAllFolders_allFoldersAreDeletedInTheWorkspace() {
    UUID workspaceUuid = createWorkspaceWithoutCloudContext(workspaceDao);
    var folder = getFolder("foo", workspaceUuid);
    // second and third folders are under folder foo.
    var secondFolder = getFolder("bar", workspaceUuid, folder.id());
    var thirdFolder = getFolder("garrr", workspaceUuid, folder.id());
    folderDao.createFolder(folder);
    folderDao.createFolder(secondFolder);
    folderDao.createFolder(thirdFolder);

    assertTrue(folderDao.deleteAllFolders(workspaceUuid));

    ImmutableList<Folder> folders = folderDao.listFoldersInWorkspace(workspaceUuid);
    assertTrue(folders.isEmpty());
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
        properties,
        DEFAULT_USER_EMAIL,
        null);
  }
}
