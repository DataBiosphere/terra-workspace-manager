package bio.terra.workspace.service.workspace;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.exception.NotImplementedException;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InternalStairwayException;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.CloneSourceMetadata;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class WorkspaceCloneService {
  private static final Logger logger = LoggerFactory.getLogger(WorkspaceCloneService.class);
  private final JobService jobService;
  private final ResourceDao resourceDao;
  private final FolderDao folderDao;
  private final ApplicationDao applicationDao;
  private final WorkspaceDao workspaceDao;
  private final WorkspaceService workspaceService;
  private final StairwayComponent stairwayComponent;
  private final SamService samService;

  @Autowired
  public WorkspaceCloneService(
      JobService jobService,
      ResourceDao resourceDao,
      FolderDao folderDao,
      ApplicationDao applicationDao,
      WorkspaceDao workspaceDao,
      WorkspaceService workspaceService,
      StairwayComponent stairwayComponent,
      SamService samService) {
    this.jobService = jobService;
    this.resourceDao = resourceDao;
    this.folderDao = folderDao;
    this.applicationDao = applicationDao;
    this.workspaceDao = workspaceDao;
    this.workspaceService = workspaceService;
    this.stairwayComponent = stairwayComponent;
    this.samService = samService;
  }

  /**
   * Clone all accessible resources from a source workspace into a destination workspace
   * @param jobId job id to use for the async clone flight
   * @param sourceWorkspace workspace to clone
   * @param userRequest creds of the cloner
   * @param location destination location to clone to
   * @param destinationWorkspace destination workspace - we will create this
   * @return job id of the the async clone flight,
   * brought to you by the department of redundancy department
   */
  public String cloneWorkspace(
      String jobId,
      Workspace sourceWorkspace,
      AuthenticatedUserRequest userRequest,
      @Nullable String location,
      Workspace destinationWorkspace) {

    // Gather the source metadata in one transaction
    CloneSourceMetadata cloneSource = gatherSourceMetadata(sourceWorkspace);

    // Start the workspace create flight. For clone we kick off the create flight
    // and do other things while it is running.
    JobBuilder workspaceCreateJob = workspaceService.buildCreateWorkspaceJob(destinationWorkspace, null, userRequest);
    String workspaceCreateJobId  = workspaceCreateJob.submit();

    // NOTE: The next part of the process is preparing all of the clone operations we need to do
    // in the flight. This includes Sam requests. If we decide this all takes too long, then
    // we will need to immediately wait on the create job right here and launch a flight that
    // does the code below.
    prepareCloneOperation(cloneSource, userRequest, location, destinationWorkspace);

    // Wait for the create job to complete
    try {
      stairwayComponent.get().waitForFlight(workspaceCreateJobId, 2, 15);
    } catch (InterruptedException ex) {
      throw new InternalStairwayException("Unexpected interrupt in create workspace flight");
    }

    // TODO - Submit the clone flight here

    throw new NewCloneNotImplemented("Not implemented");
  }

  /**
   * Gather all of the source workspace metadata into a clone structure in a single transaction. It
   * gives us a transaction-consistent snapshot of the source workspace and eliminates the need to
   * lock the source.
   *
   * @param sourceWorkspace source workspace object
   * @return CloneStructure of the source
   */
  @ReadTransaction
  public CloneSourceMetadata gatherSourceMetadata(Workspace sourceWorkspace) {
    var sourceStructure = new CloneSourceMetadata();

    sourceStructure.setWorkspace(sourceWorkspace);

    // Retrieve the cloud contexts
    Optional<String> maybeGcpCloudContext =
        workspaceDao.getCloudContextWorker(sourceWorkspace.getWorkspaceId(), CloudPlatform.GCP);
    sourceStructure.setGcpCloudContext(
        maybeGcpCloudContext.map(GcpCloudContext::deserialize).orElse(null));

    Optional<String> maybeAzureCloudContext =
        workspaceDao.getCloudContextWorker(sourceWorkspace.getWorkspaceId(), CloudPlatform.AZURE);
    sourceStructure.setAzureCloudContext(
        maybeAzureCloudContext.map(AzureCloudContext::deserialize).orElse(null));

    // Retrieve the enabled applications
    sourceStructure.setApplications(
        applicationDao.listWorkspaceApplicationsForClone(sourceWorkspace.getWorkspaceId()));

    // Collect resources that can be cloned by stewardship type
    List<WsmResource> resources =
        resourceDao.enumerateResourcesForClone(sourceWorkspace.getWorkspaceId());
    sourceStructure.setReferencedResources(filterResources(resources, StewardshipType.REFERENCED));
    sourceStructure.setControlledResources(filterResources(resources, StewardshipType.CONTROLLED));

    // Retrieve the folders and mutate them into a map
    List<Folder> folders = folderDao.listFoldersInWorkspace(sourceWorkspace.getWorkspaceId());
    Map<UUID, Folder> folderMap =
        folders.stream().collect(Collectors.toMap(Folder::id, Function.identity()));
    sourceStructure.setFolders(folderMap);

    return sourceStructure;
  }

  private boolean canRead(WsmResource resource, AuthenticatedUserRequest userRequest) {
    ControlledResource controlledResource = resource.castToControlledResource();
    return SamRethrow.onInterrupted(
            () ->
                samService.isAuthorized(
                    userRequest,
                    controlledResource.getCategory().getSamResourceName(),
                    controlledResource.getResourceId().toString(),
                    SamConstants.SamControlledResourceActions.READ_ACTION),
            "isAuthorized");
  }

  private void prepareCloneOperation(CloneSourceMetadata cloneSource,
                                     AuthenticatedUserRequest userRequest,
                                     @Nullable String location,
                                     Workspace destinationWorkspace) {
    // Folders:
    // Build two structures: a map of source to destination folder id
    // A map of destination folder id to destination folder
    Map<UUID, UUID> mapFolderSourcedestination = new HashMap<>();
    Map<UUID, Folder> destinationFolders = generatedestinationFolders(
            cloneSource.getFolders(), mapFolderSourcedestination, destinationWorkspace.getWorkspaceId());

    // Resources:
    // Filter out controlled resources that the caller does not have access to
    List<WsmResource> cloneControlledResources =
        cloneSource.getControlledResources().stream().filter(r -> canRead(r, userRequest)).toList();

    // Next: implement the resource methods for creating reference and controlled resource
    // objects


  }

  private List<WsmResource> filterResources(
      List<WsmResource> resources, StewardshipType stewardshipType) {
    return resources.stream()
        .filter(
            r ->
                r.getResourceType().isCloneable()
                    && (r.getCloningInstructions() != CloningInstructions.COPY_NOTHING)
                    && (r.getStewardshipType() == stewardshipType))
        .toList();
  }

  private Map<UUID, Folder> generatedestinationFolders(
      Map<UUID, Folder> sourceFolders,
      Map<UUID, UUID> mapFolderSourcedestination,
      UUID destinationWorkspaceId) {

    Map<UUID, Folder> destinationFolders = new HashMap<>();

    // Generate the map from source folder id to destination folder id
    for (UUID key : sourceFolders.keySet()) {
      mapFolderSourcedestination.put(key, UUID.randomUUID());
    }

    // Generate the map from destination folder id to destination folder object
    for (Folder sourceFolder : sourceFolders.values()) {
      var destinationFolder =
          new Folder(
              mapFolderSourcedestination.get(sourceFolder.id()),
              destinationWorkspaceId,
              sourceFolder.displayName(),
              sourceFolder.description(),
              (sourceFolder.parentFolderId() != null
                  ? mapFolderSourcedestination.get(sourceFolder.parentFolderId())
                  : null),
              sourceFolder.properties());
      destinationFolders.put(destinationFolder.id(), destinationFolder);
    }

    return destinationFolders;
  }

  public static class NewCloneNotImplemented extends NotImplementedException {
    public NewCloneNotImplemented(String message) {
      super(message);
    }
  }
}
