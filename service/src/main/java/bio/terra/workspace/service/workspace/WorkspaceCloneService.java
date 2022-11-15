package bio.terra.workspace.service.workspace;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.exception.NotImplementedException;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.exception.CloneInstructionNotSupportedException;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InternalStairwayException;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstant;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketHandler;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.CloneControlledGcsBucketResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.newclone.workspace.CloneControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.newclone.workspace.ControlledGcsBucketParameters;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.exception.InvalidCloningInstructionException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.flight.create.CreateReferenceResourceFlight;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.CloneIds;
import bio.terra.workspace.service.workspace.model.CloneSourceMetadata;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants;
import com.google.common.base.Strings;
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

import static bio.terra.workspace.service.resource.model.CloningInstructions.COPY_DEFINITION;
import static bio.terra.workspace.service.resource.model.CloningInstructions.COPY_RESOURCE;

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
  private final FeatureConfiguration features;

  @Autowired
  public WorkspaceCloneService(
      JobService jobService,
      ResourceDao resourceDao,
      FolderDao folderDao,
      ApplicationDao applicationDao,
      WorkspaceDao workspaceDao,
      WorkspaceService workspaceService,
      StairwayComponent stairwayComponent,
      SamService samService,
      FeatureConfiguration features) {
    this.jobService = jobService;
    this.resourceDao = resourceDao;
    this.folderDao = folderDao;
    this.applicationDao = applicationDao;
    this.workspaceDao = workspaceDao;
    this.workspaceService = workspaceService;
    this.stairwayComponent = stairwayComponent;
    this.samService = samService;
    this.features = features;
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
    JobBuilder workspaceCreateJob = workspaceService.buildCreateWorkspaceJob(destinationWorkspace, null, null, userRequest);
    String workspaceCreateJobId  = workspaceCreateJob.submit();

    // NOTE: The next part of the process is preparing all of the clone operations we need to do
    // in the flight. This includes Sam requests. If we decide this all takes too long, then
    // we will need to immediately wait on the create job right here and launch a flight that
    // does the code below.
    CloneIds cloneIds = prepareCloneOperation(cloneSource, userRequest, location, destinationWorkspace);

    // Wait for the create job to complete
    try {
      stairwayComponent.get().waitForFlight(workspaceCreateJobId, 2, 15);
    } catch (InterruptedException ex) {
      throw new InternalStairwayException("Unexpected interrupt in create workspace flight");
    }

    // TODO - Submit the clone flight here
    // addParameter(CLONE_IDS, cloneIds);

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

    // Collect resources that are not COPY_NOTHING.
    // We stash them in the sourceStructure map with the key COPY_NOTHING.
    // This is a bit of a hack, but easier than passing the list separately.
    // The map is overwritten in the prepare call below.
    List<WsmResource> resources = resourceDao.enumerateResourcesForClone(sourceWorkspace.getWorkspaceId());
    sourceStructure.setResourcesByInstruction(Map.of(CloningInstructions.COPY_NOTHING, resources));

    // Retrieve the folders and mutate them into a map
    List<Folder> folders = folderDao.listFoldersInWorkspace(sourceWorkspace.getWorkspaceId());
    Map<UUID, Folder> folderMap =
        folders.stream().collect(Collectors.toMap(Folder::id, Function.identity()));
    sourceStructure.setFolders(folderMap);

    return sourceStructure;
  }

  // Resource permission check.
  // If we are in this module, the caller has read access to the workspace, so has
  // access to the referenced resources. We have to check with Sam about access to the
  // controlled resources.
  private boolean canRead(WsmResource resource, AuthenticatedUserRequest userRequest) {
    if (resource.getStewardshipType() == StewardshipType.REFERENCED) {
      return true;
    }

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

  private CloneIds prepareCloneOperation(
      CloneSourceMetadata cloneSource,
      AuthenticatedUserRequest userRequest,
      @Nullable String location,
      Workspace destinationWorkspace) {

    // Pull out all of the resources from where we stashed them
    List<WsmResource> resources = cloneSource.getResourcesByInstruction().get(CloningInstructions.COPY_NOTHING);

    // If a resource is controlled, we need to check permissions
    // Then we want to split the list by cloning instruction. That may
    // put source controlled resources on the referenced list, if the
    // instruction is to make a reference.
    cloneSource.setResourcesByInstruction(
      resources.stream()
        .filter(r -> canRead(r, userRequest))
        .collect(Collectors.groupingBy(WsmResource::getCloningInstructions)));

    // Generate maps from source to target ids for folders and resources
    return generateIdMaps(cloneSource);
  }

  private CloneIds generateIdMaps(CloneSourceMetadata cloneSource) {
    Map<UUID, UUID> folderIdMap = new HashMap<>();
    for (UUID key : cloneSource.getFolders().keySet()) {
      folderIdMap.put(key, UUID.randomUUID());
    }

    Map<UUID, UUID> referencedResourceIdMap = new HashMap<>();
    for (WsmResource resource : cloneSource.getReferencedResources()) {
      referencedResourceIdMap.put(resource.getResourceId(), UUID.randomUUID());
    }

    Map<UUID, UUID> controlledResourceIdMap = new HashMap<>();
    for (WsmResource resource : cloneSource.getControlledResources()) {
      controlledResourceIdMap.put(resource.getResourceId(), UUID.randomUUID());
    }

    return new CloneIds(folderIdMap, referencedResourceIdMap, controlledResourceIdMap);
  }

  public static class NewCloneNotImplemented extends NotImplementedException {
    public NewCloneNotImplemented(String message) {
      super(message);
    }
  }

  // -- GCS Bucket Clone --
  // This will move to somewhere sensible, but for this prototype, I'm
  // keeping is here to simplify the number of files floating around.

  /**
   * Clone a GCS Bucket to another workspace.
   *
   * @param sourceWorkspaceUuid - workspace ID fo source bucket
   * @param sourceResourceId - resource ID of source bucket
   * @param destinationWorkspaceUuid - workspace ID to clone into
   * @param jobControl - job service control structure
   * @param userRequest - incoming request
   * @param destinationResourceName - override value for resource name. Re-uses previous name if
   *     null
   * @param destinationDescription - override value for resource description. Re-uses previous value
   *     if null
   * @param destinationBucketName - GCS bucket name for cloned bucket. If null, a random name will
   *     be generated
   * @param destinationLocation - location string for the destination bucket. If null, the source
   *     bucket's location will be used.
   * @param cloningInstructionsOverride - cloning instructions for this operation. If null, the
   *     source bucket's cloning instructions will be honored.
   * @return - Job ID of submitted flight
   */
  public String cloneGcsBucket(
    UUID sourceWorkspaceUuid,
    UUID sourceResourceId,
    UUID destinationWorkspaceUuid,
    UUID destinationResourceId,
    UUID destinationFolderId,
    ApiJobControl jobControl,
    AuthenticatedUserRequest userRequest,
    @Nullable String destinationResourceName,
    @Nullable String destinationDescription,
    @Nullable String destinationBucketName,
    @Nullable String destinationLocation,
    @Nullable CloningInstructions cloningInstructionsOverride) {

    ControlledGcsBucketResource sourceResource =
      resourceDao.getResource(sourceWorkspaceUuid, sourceResourceId).castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);

    CloningInstructions cloningInstructions =
      (cloningInstructionsOverride != null ? cloningInstructionsOverride : sourceResource.getCloningInstructions());
    // Do we need to generate a unique destination name if the workspace src = dest?
    String name = (destinationResourceName != null ? destinationResourceName : sourceResource.getName());
    String description = (destinationDescription != null ? destinationDescription : sourceResource.getDescription());

    switch (cloningInstructions) {
      case COPY_NOTHING:
        throw new InvalidCloningInstructionException("Requested clone with instruction COPY_NOTHING");

      case COPY_REFERENCE: {
        // Build the referenced resource and create it
        ReferencedResource destinationResource =
          sourceResource
            .buildReferencedClone(
              destinationWorkspaceUuid,
              destinationResourceId,
              destinationFolderId,
              name,
              description)
            .castToReferencedResource();
        return launchCreateReferencedResource(destinationResource, userRequest);
      }

      case COPY_RESOURCE:
      case COPY_DEFINITION:
        break;
    }

    // TODO: [need PR] No folder handling on the single resource clone

    ControlledResourceFields destinationFields = sourceResource.buildControlledResourceCommonFields(
      destinationWorkspaceUuid,
      destinationResourceId,
      destinationFolderId,
      destinationResourceName,
      destinationDescription);

    // Make a record for GCS resource-specific stuff to pass in
    // Must cover clone and create
    //
    // need to pass in destination bucket name
    // need to pass in location

    // Prep shared inputs
    String jobDescription =
      String.format(
        "Clone controlled resource %s; id %s; name %s",
        sourceResource.getResourceType(),
        sourceResource.getResourceId(),
        sourceResource.getName());
    // If TPS is enabled, then we want to merge policies when cloning a bucket
    boolean mergePolicies = features.isTpsEnabled();

    // Compute GCS bucket extras
    // Stuff that we need to eventually create the full controlled resource object.
    var bucketParameters = new ControlledGcsBucketParameters();

    // If the source bucket uses the auto-generated cloud name and the destination
    // bucket attempt to do the same, the name will crash as the bucket name must be
    // globally unique. Thus, we add cloned- as prefix to the bucket name to prevent
    // crashing.
    bucketParameters.setBucketName(
      destinationBucketName != null ? destinationBucketName :
          ControlledGcsBucketHandler.getHandler()
            .generateCloudName(destinationWorkspaceUuid, "cloned-" + sourceResource.getName()));

    bucketParameters.setLocation(destinationLocation);

    JobBuilder jobBuilder =
      jobService.newJob()
        .description(jobDescription)
        .jobId(jobControl.getId())
        .flightClass(CloneControlledResourceFlight.class)
        .resource(sourceResource)
        .userRequest(userRequest)
        .workspaceId(sourceWorkspaceUuid.toString())
        .operationType(OperationType.CLONE)
        .addParameter(WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_FIELDS, destinationFields)
        .addParameter(WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_PARAMETERS, bucketParameters)
        .addParameter(WorkspaceFlightMapKeys.MERGE_POLICIES, mergePolicies)
        .addParameter(WorkspaceFlightMapKeys.ControlledResourceKeys.CLONING_INSTRUCTIONS, cloningInstructions);

    return jobBuilder.submit();
  }

  private String launchCreateReferencedResource(ReferencedResource destinationResource, AuthenticatedUserRequest userRequest) {
    String jobDescription =
      String.format(
        "Create reference %s; id %s; name %s",
        destinationResource.getResourceType(), destinationResource.getResourceId(), destinationResource.getName());

    // TODO: This is not quite right, since this flight is always sync and doesn't have the
    //  matching behavior for GCS async. We'll need an async wrapper for create reference
    //  flights, I think.
    JobBuilder jobBuilder =
      jobService.newJob()
        .description(jobDescription)
        .flightClass(CreateReferenceResourceFlight.class)
        .userRequest(userRequest)
        .resource(destinationResource)
        .operationType(OperationType.CLONE)
        .workspaceId(destinationResource.getWorkspaceId().toString())
        .resourceType(destinationResource.getResourceType())
        .stewardshipType(StewardshipType.REFERENCED);

    return jobBuilder.submit();

  }
}
