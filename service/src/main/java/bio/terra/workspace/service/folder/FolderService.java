package bio.terra.workspace.service.folder;

import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.db.exception.FolderNotFoundException;
import bio.terra.workspace.service.folder.model.Folder;
import com.google.common.collect.ImmutableList;
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.stereotype.Component;

@Component
public class FolderService {

  private final FolderDao folderDao;

  public FolderService(FolderDao folderDao) {
    this.folderDao = folderDao;
  }

  public Folder createFolder(Folder folder) {
    return folderDao.createFolder(folder);
  }

  public Folder updateFolder(
      UUID workspaceUuid,
      UUID folderId,
      @Nullable String displayName,
      @Nullable String description,
      @Nullable UUID parentFolderId,
      @Nullable boolean moveToTopLevel) {
    folderDao.updateFolder(
        workspaceUuid, folderId, displayName, description, parentFolderId, moveToTopLevel);
    return folderDao.getFolder(workspaceUuid, folderId);
  }

  public Folder getFolder(UUID workspaceUuid, UUID folderId) {
    return folderDao
        .getFolderIfExists(workspaceUuid, folderId)
        .orElseThrow(
            () ->
                new FolderNotFoundException(
                    String.format(
                        "Failed to find folder %s in workspace %s", folderId, workspaceUuid)));
  }

  public ImmutableList<Folder> listFolders(UUID workspaceId) {
    return folderDao.listFolders(workspaceId, /*parentFolderId=*/ null);
  }

  public void deleteFolder(UUID workspaceUuid, UUID folderId) {
    folderDao.deleteFolder(workspaceUuid, folderId);
    // TODO: start a flight to update resource properties
  }
}
