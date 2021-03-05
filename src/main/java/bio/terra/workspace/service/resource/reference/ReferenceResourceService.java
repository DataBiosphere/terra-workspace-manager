package bio.terra.workspace.service.resource.reference;

import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.reference.flight.create.CreateReferenceResourceFlight;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ReferenceResourceService {
  private final JobService jobService;
  private final ResourceDao resourceDao;
  private final SamService samService;
  private final WorkspaceService workspaceService;

  @Autowired
  public ReferenceResourceService(
      JobService jobService,
      ResourceDao resourceDao,
      SamService samService,
      WorkspaceService workspaceService) {
    this.jobService = jobService;
    this.resourceDao = resourceDao;
    this.samService = samService;
    this.workspaceService = workspaceService;
  }

  @Traced
  public ReferenceResource createReferenceResource(
      ReferenceResource resource, AuthenticatedUserRequest userReq) {
    // TODO: Fix this based on resolution of permission model issue.
    workspaceService.validateWorkspaceAndAction(
        userReq, resource.getWorkspaceId(), SamConstants.SAM_WORKSPACE_WRITE_ACTION);
    resource.validate();

    String jobDescription =
        String.format(
            "Create reference %s; id %s; name %s",
            resource.getResourceType(), resource.getResourceId(), resource.getName());
    JobBuilder createJob =
        jobService
            .newJob(
                jobDescription,
                UUID.randomUUID().toString(),
                CreateReferenceResourceFlight.class,
                resource,
                userReq)
            .addParameter(
                WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_TYPE, resource.getResourceType());

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
      String name,
      String description,
      AuthenticatedUserRequest userReq) {
    workspaceService.validateWorkspaceAndAction(
        userReq, workspaceId, SamConstants.SAM_WORKSPACE_WRITE_ACTION);
    resourceDao.updateReferenceResource(workspaceId, resourceId, name, description);
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
    // TODO: Fix this based on resolution of permission model issue.
    workspaceService.validateWorkspaceAndAction(
        userReq, workspaceId, SamConstants.SAM_WORKSPACE_WRITE_ACTION);
    resourceDao.deleteResource(workspaceId, resourceId);
  }

  public ReferenceResource getReferenceResource(
      UUID workspaceId, UUID resourceId, AuthenticatedUserRequest userReq) {
    // TODO: Fix this based on resolution of permission model issue.
    workspaceService.validateWorkspaceAndAction(
        userReq, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);
    WsmResource wsmResource = resourceDao.getResource(workspaceId, resourceId);
    return castReferenceResource(wsmResource);
  }

  public ReferenceResource getReferenceResourceByName(
      UUID workspaceId, String name, AuthenticatedUserRequest userReq) {
    // TODO: Fix this based on resolution of permission model issue.
    workspaceService.validateWorkspaceAndAction(
        userReq, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);
    WsmResource wsmResource = resourceDao.getResourceByName(workspaceId, name);
    return castReferenceResource(wsmResource);
  }

  public List<ReferenceResource> enumerateReferences(
      UUID workspaceId, int offset, int limit, AuthenticatedUserRequest userReq) {
    // TODO: Fix this based on resolution of permission model issue.
    workspaceService.validateWorkspaceAndAction(
        userReq, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);
    return resourceDao.enumerateReferences(workspaceId, offset, limit);
  }

  private ReferenceResource castReferenceResource(WsmResource wsmResource) {
    if (!(wsmResource instanceof ReferenceResource)) {
      throw new InvalidMetadataException("Returned resource is not a reference");
    }
    return (ReferenceResource) wsmResource;
  }
}
