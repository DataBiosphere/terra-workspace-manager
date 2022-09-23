package bio.terra.workspace.service.folder;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.FOLDER_ID;
import static bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties.FOLDER_ID_KEY;

import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.folder.flights.FolderDeleteFlight;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ReferencedResourceKeys;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FolderService {

  private static final Logger logger = LoggerFactory.getLogger(FolderService.class);
  private final FolderDao folderDao;
  private final ResourceDao resourceDao;
  private final JobService jobService;

  public FolderService(FolderDao folderDao, ResourceDao resourceDao, JobService jobService) {
    this.folderDao = folderDao;
    this.resourceDao = resourceDao;
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
    List<WsmResource> referencedResources = new ArrayList<>();
    List<WsmResource> controlledResources = new ArrayList<>();
    getResourcesInFolder(workspaceUuid, folderId, controlledResources, referencedResources);
    boolean deleted =
        jobService
            .newJob()
            .description(String.format("Delete folder %s in workspace %s", folderId, workspaceUuid))
            .jobId(UUID.randomUUID().toString())
            .flightClass(FolderDeleteFlight.class)
            .workspaceId(workspaceUuid.toString())
            .userRequest(userRequest)
            .operationType(OperationType.DELETE)
            .addParameter(FOLDER_ID, folderId)
            .addParameter(ControlledResourceKeys.RESOURCES_TO_DELETE, controlledResources)
            .addParameter(ReferencedResourceKeys.RESOURCES_TO_DELETE, referencedResources)
            .submitAndWait(Boolean.class);
    if (!deleted) {
      logger.warn(
          String.format("Failed to delete folder %s in workspace %s", folderId, workspaceUuid));
    }
  }

  public void updateFolderProperties(
      UUID workspaceUuid, UUID folderUuid, Map<String, String> properties) {
    folderDao.updateFolderProperties(workspaceUuid, folderUuid, properties);
  }

  public void deleteFolderProperties(
      UUID workspaceUuid, UUID folderUuid, List<String> propertyKeys) {
    folderDao.deleteFolderProperties(workspaceUuid, folderUuid, propertyKeys);
  }

  private void getResourcesInFolder(
      UUID workspaceId,
      UUID folderId,
      List<WsmResource> controlledResources,
      List<WsmResource> referencedResources) {
    Set<UUID> folderIds = new HashSet<>();
    getAllSubFolderIds(workspaceId, folderId, folderIds);
    var offset = 0;
    var limit = 100;
    List<WsmResource> batch;
    do {
      batch = resourceDao.enumerateResources(workspaceId, null, null, offset, limit);
      offset += limit;
      batch.stream()
          .filter(resource -> isInFolder(resource, folderIds))
          .forEach(
              resource -> {
                if (StewardshipType.REFERENCED == resource.getStewardshipType()) {
                  referencedResources.add(resource);
                } else if (StewardshipType.CONTROLLED == resource.getStewardshipType()) {
                  controlledResources.add(resource);
                }
              });
    } while (batch.size() == limit);
  }

  private void getAllSubFolderIds(UUID workspaceId, UUID folderId, Set<UUID> folderIds) {
    folderIds.add(folderId);
    List<Folder> subFolders = folderDao.listFolders(workspaceId, folderId);
    if (subFolders.isEmpty()) {
      return;
    }
    for (Folder f : subFolders) {
      getAllSubFolderIds(workspaceId, f.id(), folderIds);
    }
  }

  private static boolean isInFolder(WsmResource resource, Set<UUID> folderIds) {
    return resource.getProperties().containsKey(FOLDER_ID_KEY)
        && folderIds.contains(UUID.fromString(resource.getProperties().get(FOLDER_ID_KEY)));
  }
}
