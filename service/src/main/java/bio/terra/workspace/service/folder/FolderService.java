package bio.terra.workspace.service.folder;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.FOLDER_ID;

import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.service.folder.flights.FolderDeleteFlight;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.stereotype.Component;

@Component
public class FolderService {

  private final FolderDao folderDao;
  private final JobService jobService;

  public FolderService(FolderDao folderDao, JobService jobService) {
    this.folderDao = folderDao;
    this.jobService = jobService;
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
      @Nullable boolean updateParent) {
    folderDao.updateFolder(
        workspaceUuid, folderId, displayName, description, parentFolderId, updateParent);
    return folderDao.getFolder(workspaceUuid, folderId);
  }

  public Folder getFolder(UUID workspaceUuid, UUID folderId) {
    return folderDao.getFolder(workspaceUuid, folderId);
  }

  public ImmutableList<Folder> listFolders(UUID workspaceId) {
    return folderDao.listFolders(workspaceId, /*parentFolderId=*/ null);
  }

  /** Delete folder and all the resources and subfolder under it. */
  public void deleteFolder(
      UUID workspaceUuid, UUID folderId, AuthenticatedUserRequest userRequest) {
    jobService
        .newJob()
        .description(String.format("Delete folder %s in workspace %s", folderId, workspaceUuid))
        .jobId(UUID.randomUUID().toString())
        .flightClass(FolderDeleteFlight.class)
        .workspaceId(workspaceUuid.toString())
        .userRequest(userRequest)
        .operationType(OperationType.DELETE)
        .addParameter(FOLDER_ID, folderId)
        .submitAndWait(null);
  }

  public void updateFolderProperties(
      UUID workspaceUuid, UUID folderUuid, Map<String, String> properties) {
    folderDao.updateFolderProperties(workspaceUuid, folderUuid, properties);
  }

  public void deleteFolderProperties(
      UUID workspaceUuid, UUID folderUuid, List<String> propertyKeys) {
    folderDao.deleteFolderProperties(workspaceUuid, folderUuid, propertyKeys);
  }
}
