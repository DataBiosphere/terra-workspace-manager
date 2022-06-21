package bio.terra.workspace.service.resource.referenced.cloud.gcp;

import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceActivityLogDao;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbWorkspaceActivityLog;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.WorkspaceCloneUtils;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.flight.create.CreateReferenceResourceFlight;
import bio.terra.workspace.service.resource.referenced.flight.update.UpdateReferenceResourceFlight;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.OperationType;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ReferencedResourceService {

  private final Logger logger = LoggerFactory.getLogger(ReferencedResourceService.class);

  private final JobService jobService;
  private final ResourceDao resourceDao;
  private final WorkspaceService workspaceService;
  private final FlightBeanBag beanBag;
  private final WorkspaceActivityLogDao workspaceActivityLogDao;

  @Autowired
  public ReferencedResourceService(
      JobService jobService,
      ResourceDao resourceDao,
      WorkspaceService workspaceService,
      FlightBeanBag beanBag,
      WorkspaceActivityLogDao workspaceActivityLogDao) {
    this.jobService = jobService;
    this.resourceDao = resourceDao;
    this.workspaceService = workspaceService;
    this.beanBag = beanBag;
    this.workspaceActivityLogDao = workspaceActivityLogDao;
  }

  @Traced
  public ReferencedResource createReferenceResource(
      ReferencedResource resource, AuthenticatedUserRequest userRequest) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, resource.getWorkspaceId(), SamConstants.SamWorkspaceAction.CREATE_REFERENCE);

    String jobDescription =
        String.format(
            "Create reference %s; id %s; name %s",
            resource.getResourceType(), resource.getResourceId(), resource.getName());

    // The reason for separately passing in the ResourceType is to retrieve the class for this
    // particular request. In the flight, when we get the request object from the input parameters,
    // we can supply the right target class.
    JobBuilder createJob =
        jobService
            .newJob()
            .description(jobDescription)
            .flightClass(CreateReferenceResourceFlight.class)
            .resource(resource)
            .userRequest(userRequest)
            .operationType(OperationType.CREATE)
            .workspaceId(resource.getWorkspaceId().toString())
            .resourceType(resource.getResourceType())
            .stewardshipType(StewardshipType.REFERENCED);

    UUID resourceIdResult = createJob.submitAndWait(UUID.class);
    if (!resourceIdResult.equals(resource.getResourceId())) {
      throw new InvalidMetadataException("Input and output resource ids do not match");
    }

    return getReferenceResource(resource.getWorkspaceId(), resourceIdResult, userRequest);
  }

  /**
   * Updates name and/or description of the reference resource.
   *
   * @param workspaceUuid workspace of interest
   * @param resourceId resource to update
   * @param name name to change - may be null
   * @param description description to change - may be null
   */
  public void updateReferenceResource(
      UUID workspaceUuid,
      UUID resourceId,
      AuthenticatedUserRequest userRequest,
      @Nullable String name,
      @Nullable String description) {
    updateReferenceResource(
        workspaceUuid,
        resourceId,
        userRequest,
        name,
        description,
        /*referencedResource=*/ null,
        /*cloningInstructions=*/ null);
  }

  /**
   * Updates name, description and/or referencing traget of the reference resource.
   *
   * @param workspaceUuid workspace of interest
   * @param resourceId resource to update
   * @param name name to change - may be null
   * @param description description to change - may be null
   * @param resource referencedResource to be updated to - may be null if not intending to update
   *     referencing target.
   * @param cloningInstructions cloning instructions to change - may be null. If resource is
   *     non-null, the cloning instructions will be taken from its metadata and this parameter will
   *     be ignored.
   */
  public void updateReferenceResource(
      UUID workspaceUuid,
      UUID resourceId,
      AuthenticatedUserRequest userRequest,
      @Nullable String name,
      @Nullable String description,
      @Nullable ReferencedResource resource,
      @Nullable CloningInstructions cloningInstructions) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.UPDATE_REFERENCE);
    // Name may be null if the user is not updating it in this request.
    if (name != null) {
      ResourceValidationUtils.validateResourceName(name);
    }
    // Description may also be null, but this validator accepts null descriptions.
    ResourceValidationUtils.validateResourceDescriptionName(description);
    boolean updated;
    if (resource != null) {
      JobBuilder updateJob =
          jobService
              .newJob()
              .description("Update reference target")
              .flightClass(UpdateReferenceResourceFlight.class)
              .resource(resource)
              .userRequest(userRequest)
              .operationType(OperationType.UPDATE)
              .workspaceId(workspaceUuid.toString())
              .resourceType(resource.getResourceType())
              .resourceName(name)
              .addParameter(ResourceKeys.RESOURCE_DESCRIPTION, description);
      updated = updateJob.submitAndWait(Boolean.class);
    } else {
      // we are not updating anything on the cloud, just the DB
      updated =
          resourceDao.updateResource(
              workspaceUuid, resourceId, name, description, cloningInstructions);
    }
    if (!updated) {
      logger.warn("There's no update to the referenced resource");
    }
  }
  /**
   * Delete a reference. The only state we hold for a reference is in the metadata database so we
   * directly delete that.
   *
   * @param workspaceUuid workspace of interest
   * @param resourceId resource to delete
   * @param userRequest authenticated user
   */
  public void deleteReferenceResource(
      UUID workspaceUuid, UUID resourceId, AuthenticatedUserRequest userRequest) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.DELETE_REFERENCE);
    if (resourceDao.deleteResource(workspaceUuid, resourceId)) {
      workspaceActivityLogDao.writeActivity(
          workspaceUuid, new DbWorkspaceActivityLog().operationType(OperationType.DELETE));
    }
  }

  /**
   * Delete a reference for the specified resource type. If the resource type stored in the metadata
   * database does not match with the specified type, we do not delete the data.
   *
   * @param workspaceUuid workspace of interest
   * @param resourceId resource to delete
   * @param userRequest authenticated user
   * @param resourceType wsm resource type that the to-be-deleted resource should have
   */
  public void deleteReferenceResourceForResourceType(
      UUID workspaceUuid,
      UUID resourceId,
      AuthenticatedUserRequest userRequest,
      WsmResourceType resourceType) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.DELETE_REFERENCE);
    if (resourceDao.deleteResourceForResourceType(workspaceUuid, resourceId, resourceType)) {
      workspaceActivityLogDao.writeActivity(
          workspaceUuid, new DbWorkspaceActivityLog().operationType(OperationType.DELETE));
    }
  }

  public ReferencedResource getReferenceResource(
      UUID workspaceUuid, UUID resourceId, AuthenticatedUserRequest userRequest) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.READ);
    return resourceDao.getResource(workspaceUuid, resourceId).castToReferencedResource();
  }

  public ReferencedResource getReferenceResourceByName(
      UUID workspaceUuid, String name, AuthenticatedUserRequest userRequest) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.READ);
    return resourceDao.getResourceByName(workspaceUuid, name).castToReferencedResource();
  }

  public List<ReferencedResource> enumerateReferences(
      UUID workspaceUuid, int offset, int limit, AuthenticatedUserRequest userRequest) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.READ);
    return resourceDao.enumerateReferences(workspaceUuid, offset, limit);
  }

  public boolean checkAccess(
      UUID workspaceUuid, UUID resourceId, AuthenticatedUserRequest userRequest) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.READ);
    ReferencedResource referencedResource =
        resourceDao.getResource(workspaceUuid, resourceId).castToReferencedResource();
    return referencedResource.checkAccess(beanBag, userRequest);
  }

  public ReferencedResource cloneReferencedResource(
      ReferencedResource sourceReferencedResource,
      UUID destinationWorkspaceId,
      @Nullable String name,
      @Nullable String description,
      AuthenticatedUserRequest userRequest) {
    final ReferencedResource destinationResource =
        WorkspaceCloneUtils.buildDestinationReferencedResource(
            sourceReferencedResource, destinationWorkspaceId, name, description);
    // launch the creation flight
    return createReferenceResource(destinationResource, userRequest);
  }
}
