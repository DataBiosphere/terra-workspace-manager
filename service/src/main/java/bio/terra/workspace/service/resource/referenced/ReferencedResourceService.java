package bio.terra.workspace.service.resource.referenced;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.DbRetryUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.referenced.flight.create.CreateReferenceResourceFlight;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ReferencedResourceService {
  private final JobService jobService;
  private final ResourceDao resourceDao;
  private final WorkspaceService workspaceService;
  private final FlightBeanBag beanBag;

  @Autowired
  public ReferencedResourceService(
      JobService jobService,
      ResourceDao resourceDao,
      WorkspaceService workspaceService,
      FlightBeanBag beanBag) {
    this.jobService = jobService;
    this.resourceDao = resourceDao;
    this.workspaceService = workspaceService;
    this.beanBag = beanBag;
  }

  @Traced
  public ReferencedResource createReferenceResource(
      ReferencedResource resource, AuthenticatedUserRequest userRequest) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, resource.getWorkspaceId(), SamConstants.SAM_CREATE_REFERENCED_RESOURCE);
    resource.validate();

    String jobDescription =
        String.format(
            "Create reference %s; id %s; name %s",
            resource.getResourceType(), resource.getResourceId(), resource.getName());

    // The reason for separately passing in the ResourceType is to retrieve the class for this
    // particular request. In the flight, when we get the request object from the input parameters,
    // we can supply the right target class.
    JobBuilder createJob =
        jobService
            .newJob(
                jobDescription,
                UUID.randomUUID().toString(),
                CreateReferenceResourceFlight.class,
                resource,
                userRequest)
            .addParameter(
                WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_TYPE,
                resource.getResourceType().name());

    UUID resourceIdResult = createJob.submitAndWait(UUID.class);
    if (!resourceIdResult.equals(resource.getResourceId())) {
      throw new InvalidMetadataException("Input and output resource ids do not match");
    }

    return getReferenceResource(resource.getWorkspaceId(), resourceIdResult, userRequest);
  }

  /**
   * At this moment, the only updates on references we are doing are to name and description so this
   * is a common implementation. If we get more complicated, we can break it out.
   *
   * @param workspaceId workspace of interest
   * @param resourceId resource to update
   * @param name name to change - may be null
   * @param description description to change - may be null
   */
  public void updateReferenceResource(
      UUID workspaceId,
      UUID resourceId,
      @Nullable String name,
      @Nullable String description,
      AuthenticatedUserRequest userRequest) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SAM_UPDATE_REFERENCED_RESOURCE);
    DbRetryUtils.throwIfInterrupted(
        () -> resourceDao.updateResource(workspaceId, resourceId, name, description));
  }

  /**
   * Delete a reference. The only state we hold for a reference is in the metadata database so we
   * directly delete that.
   *
   * @param workspaceId workspace of interest
   * @param resourceId resource to delete
   * @param userRequest authenticated user
   */
  public void deleteReferenceResource(
      UUID workspaceId, UUID resourceId, AuthenticatedUserRequest userRequest) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SAM_DELETE_REFERENCED_RESOURCE);
    DbRetryUtils.throwIfInterrupted(() -> resourceDao.deleteResource(workspaceId, resourceId));
  }

  public ReferencedResource getReferenceResource(
      UUID workspaceId, UUID resourceId, AuthenticatedUserRequest userRequest) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);
    return DbRetryUtils.throwIfInterrupted(
        () -> resourceDao.getResource(workspaceId, resourceId).castToReferencedResource());
  }

  public ReferencedResource getReferenceResourceByName(
      UUID workspaceId, String name, AuthenticatedUserRequest userRequest) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);
    return DbRetryUtils.throwIfInterrupted(
        () -> resourceDao.getResourceByName(workspaceId, name).castToReferencedResource());
  }

  public List<ReferencedResource> enumerateReferences(
      UUID workspaceId, int offset, int limit, AuthenticatedUserRequest userRequest) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);
    return DbRetryUtils.throwIfInterrupted(
        () -> resourceDao.enumerateReferences(workspaceId, offset, limit));
  }

  public boolean checkAccess(
      UUID workspaceId, UUID resourceId, AuthenticatedUserRequest userRequest) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);
    ReferencedResource referencedResource =
        DbRetryUtils.throwIfInterrupted(
            () -> resourceDao.getResource(workspaceId, resourceId).castToReferencedResource());
    return referencedResource.checkAccess(beanBag, userRequest);
  }

  public ReferencedResource cloneReferencedResource(
      ReferencedResource sourceReferencedResource,
      UUID destinationWorkspaceId,
      @Nullable String name,
      @Nullable String description,
      AuthenticatedUserRequest userRequest) {
    final ReferencedResource destinationResource;
    switch (sourceReferencedResource.getResourceType()) {
      case GCS_BUCKET:
        destinationResource =
            buildDestinationGcsBucketReference(
                sourceReferencedResource.castToGcsBucketResource(),
                destinationWorkspaceId,
                name,
                description);
        break;
      case DATA_REPO_SNAPSHOT:
        destinationResource =
            buildDestinationDataRepoSnapshotReference(
                sourceReferencedResource.castToDataRepoSnapshotResource(),
                destinationWorkspaceId,
                name,
                description);
        break;
      case BIG_QUERY_DATASET:
        destinationResource =
            buildDestinationBigQueryDatasetReference(
                sourceReferencedResource.castToBigQueryDatasetResource(),
                destinationWorkspaceId,
                name,
                description);
        break;
      case AI_NOTEBOOK_INSTANCE:
      default:
        throw new BadRequestException(
            String.format(
                "Resource type %s not supported",
                sourceReferencedResource.getResourceType().toString()));
    }
    return createReferenceResource(destinationResource, userRequest);
  }

  /**
   * Create a clone of a reference, which is identical in all fields except workspace ID, resource
   * ID, and (possibly) name and description. This method reuses the createReferenceResource()
   * method on the ReferenceResourceService.
   *
   * @param sourceBucketResource - original resource to be cloned
   * @param destinationWorkspaceId - workspace ID for new reference
   * @param name - resource name for cloned reference. Will use original name if this is null.
   * @param description - resource description for cloned reference. Uses original if left null.
   * @return
   */
  private ReferencedResource buildDestinationGcsBucketReference(
      ReferencedGcsBucketResource sourceBucketResource,
      UUID destinationWorkspaceId,
      @Nullable String name,
      @Nullable String description) {

    final ReferencedGcsBucketResource.Builder resultBuilder =
        sourceBucketResource.toBuilder()
            .workspaceId(destinationWorkspaceId)
            .resourceId(UUID.randomUUID());
    // apply optional override variables
    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }

  private ReferencedResource buildDestinationBigQueryDatasetReference(
      ReferencedBigQueryDatasetResource sourceBigQueryResource,
      UUID destinationWorkspaceId,
      @Nullable String name,
      @Nullable String description) {
    // keep projectId and dataset name the same since they are for the referent
    final ReferencedBigQueryDatasetResource.Builder resultBuilder =
        sourceBigQueryResource.toBuilder()
            .workspaceId(destinationWorkspaceId)
            .resourceId(UUID.randomUUID());
    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }

  private ReferencedResource buildDestinationDataRepoSnapshotReference(
      ReferencedDataRepoSnapshotResource sourceReferencedDataRepoSnapshotResource,
      UUID destinationWorkspaceId,
      @Nullable String name,
      @Nullable String description) {
    final ReferencedDataRepoSnapshotResource.Builder resultBuilder =
        sourceReferencedDataRepoSnapshotResource.toBuilder()
            .workspaceId(destinationWorkspaceId)
            .resourceId(UUID.randomUUID());
    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }
}
