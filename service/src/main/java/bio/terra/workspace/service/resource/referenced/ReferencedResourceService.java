package bio.terra.workspace.service.resource.referenced;

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
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ReferencedResourceService {
  private final JobService jobService;
  private final ResourceDao resourceDao;
  private final WorkspaceService workspaceService;

  @Autowired
  public ReferencedResourceService(
      JobService jobService, ResourceDao resourceDao, WorkspaceService workspaceService) {
    this.jobService = jobService;
    this.resourceDao = resourceDao;
    this.workspaceService = workspaceService;
  }

  @Traced
  public ReferencedResource createReferenceResource(
      ReferencedResource resource, AuthenticatedUserRequest userReq) {
    workspaceService.validateWorkspaceAndAction(
        userReq, resource.getWorkspaceId(), SamConstants.SAM_CREATE_REFERENCED_RESOURCE);
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
                userReq)
            .addParameter(
                WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_TYPE,
                resource.getResourceType().name());

    UUID resourceIdResult = createJob.submitAndWait(UUID.class);
    if (!resourceIdResult.equals(resource.getResourceId())) {
      throw new InvalidMetadataException("Input and output resource ids do not match");
    }

    return getReferenceResource(resource.getWorkspaceId(), resourceIdResult, userReq);
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
      AuthenticatedUserRequest userReq) {
    workspaceService.validateWorkspaceAndAction(
        userReq, workspaceId, SamConstants.SAM_UPDATE_REFERENCED_RESOURCE);
    DbRetryUtils.throwIfInterrupted(
        () -> resourceDao.updateResource(workspaceId, resourceId, name, description));
  }

  /**
   * Delete a reference. The only state we hold for a reference is in the metadata database so we
   * directly delete that.
   *
   * @param workspaceId workspace of interest
   * @param resourceId resource to delete
   * @param userReq authenticated user
   */
  public void deleteReferenceResource(
      UUID workspaceId, UUID resourceId, AuthenticatedUserRequest userReq) {
    workspaceService.validateWorkspaceAndAction(
        userReq, workspaceId, SamConstants.SAM_DELETE_REFERENCED_RESOURCE);
    DbRetryUtils.throwIfInterrupted(() -> resourceDao.deleteResource(workspaceId, resourceId));
  }

  public ReferencedResource getReferenceResource(
      UUID workspaceId, UUID resourceId, AuthenticatedUserRequest userReq) {
    workspaceService.validateWorkspaceAndAction(
        userReq, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);
    return DbRetryUtils.throwIfInterrupted(
        () -> resourceDao.getResource(workspaceId, resourceId).castToReferenceResource());
  }

  public ReferencedResource getReferenceResourceByName(
      UUID workspaceId, String name, AuthenticatedUserRequest userReq) {
    workspaceService.validateWorkspaceAndAction(
        userReq, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);
    return DbRetryUtils.throwIfInterrupted(
        () -> resourceDao.getResourceByName(workspaceId, name).castToReferenceResource());
  }

  public List<ReferencedResource> enumerateReferences(
      UUID workspaceId, int offset, int limit, AuthenticatedUserRequest userReq) {
    workspaceService.validateWorkspaceAndAction(
        userReq, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);
    return DbRetryUtils.throwIfInterrupted(
        () -> resourceDao.enumerateReferences(workspaceId, offset, limit));
  }
}
