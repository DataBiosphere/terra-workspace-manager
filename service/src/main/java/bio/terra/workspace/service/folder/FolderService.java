package bio.terra.workspace.service.folder;

import bio.terra.workspace.db.FolderAndResourceDao;
import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.service.folder.model.Folder;
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.stereotype.Component;

@Component
public class FolderService {

  private final FolderDao folderDao;
  private final FolderAndResourceDao folderAndResourceDao;

  public FolderService(FolderDao folderDao,
      FolderAndResourceDao folderAndResourceDao) {
    this.folderDao = folderDao;
    this.folderAndResourceDao = folderAndResourceDao;
  }

  public Folder createFolder(Folder folder) {
    return folderDao.createFolder(folder);
  }

  public Folder updateFolder(
      UUID workspaceUuid,
      UUID folderId,
      @Nullable String displayName,
      @Nullable String description,
      @Nullable UUID parentFolderId) {
    folderDao.updateFolder(workspaceUuid, folderId, displayName, description, parentFolderId);
    return folderDao.getFolder(workspaceUuid, folderId);
  }

  public Folder getFolder(UUID workspaceUuid, UUID folderId) {
    return folderDao.getFolder(workspaceUuid, folderId);
  }

  public void deleteFolder(UUID workspaceUuid, UUID folderId) {
    folderDao.deleteFolder(workspaceUuid, folderId);
  }

  public void addResourceToFolder(UUID resourceId, UUID folderId) {
    folderAndResourceDao.addResourceToFolder(resourceId, folderId);
  }
}
