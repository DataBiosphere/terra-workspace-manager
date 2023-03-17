package bio.terra.workspace.service.resource.referenced;

import static bio.terra.workspace.service.resource.model.WsmResourceState.BROKEN;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.exception.ResourceStateConflictException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.exception.ResourceIsBusyException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.flight.clone.CloneReferencedResourceFlight;
import bio.terra.workspace.service.resource.referenced.flight.update.UpdateReferenceResourceFlight;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.stage.StageService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.OperationType;
import io.opencensus.contrib.spring.aop.Traced;
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
  private final FeatureConfiguration features;
  private final SamService samService;
  private final TpsApiDispatch tpsApiDispatch;
  private final WorkspaceActivityLogService workspaceActivityLogService;
  private final StageService stageService;

  @Autowired
  public ReferencedResourceService(
      JobService jobService,
      ResourceDao resourceDao,
      WorkspaceService workspaceService,
      FlightBeanBag beanBag,
      WorkspaceActivityLogService workspaceActivityLogService,
      FeatureConfiguration features,
      SamService samService,
      TpsApiDispatch tpsApiDispatch,
      StageService stageService) {
    this.jobService = jobService;
    this.resourceDao = resourceDao;
    this.workspaceService = workspaceService;
    this.beanBag = beanBag;
    this.features = features;
    this.samService = samService;
    this.tpsApiDispatch = tpsApiDispatch;
    this.workspaceActivityLogService = workspaceActivityLogService;
    this.stageService = stageService;
  }

  @Traced
  public ReferencedResource createReferenceResource(
      ReferencedResource resource, AuthenticatedUserRequest userRequest) {
    resourceDao.createReferencedResource(resource);
    workspaceActivityLogService.writeActivity(
        userRequest,
        resource.getWorkspaceId(),
        OperationType.CREATE,
        resource.getResourceId().toString(),
        ActivityLogChangedTarget.RESOURCE);
    return getReferenceResource(resource.getWorkspaceId(), resource.getResourceId());
  }

  @Traced
  public ReferencedResource createReferenceResourceForClone(ReferencedResource resourceToClone) {
    resourceDao.createReferencedResource(resourceToClone);
    return getReferenceResource(resourceToClone.getWorkspaceId(), resourceToClone.getResourceId());
  }

  /**
   * Updates name and/or description of the reference resource.
   *
   * @param workspaceUuid workspace of interest
   * @param resourceId resource to update
   * @param name name to change - may be null
   * @param description description to change - may be null
   */
  @Traced
  public void updateReferenceResource(
      UUID workspaceUuid,
      UUID resourceId,
      @Nullable String name,
      @Nullable String description,
      AuthenticatedUserRequest userRequest) {
    updateReferenceResource(
        workspaceUuid,
        resourceId,
        name,
        description,
        /*referencedResource=*/ null,
        /*cloningInstructions=*/ null,
        userRequest);
  }

  /**
   * Updates name, description and/or referencing target of the reference resource.
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
  @Traced
  public void updateReferenceResource(
      UUID workspaceUuid,
      UUID resourceId,
      @Nullable String name,
      @Nullable String description,
      @Nullable ReferencedResource resource,
      @Nullable CloningInstructions cloningInstructions,
      AuthenticatedUserRequest userRequest) {
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
              .operationType(OperationType.UPDATE)
              .userRequest(userRequest)
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
      if (updated) {
        workspaceActivityLogService.writeActivity(
            userRequest,
            workspaceUuid,
            OperationType.UPDATE,
            resourceId.toString(),
            ActivityLogChangedTarget.RESOURCE);
      }
    }
    if (!updated) {
      logger.warn("There's no update to the referenced resource");
    }
  }

  /**
   * Delete a reference for the specified resource type. If the resource type stored in the metadata
   * database does not match with the specified type, we do not delete the data.
   *
   * @param workspaceUuid workspace of interest
   * @param resourceId resource to delete
   * @param resourceType wsm resource type that the to-be-deleted resource should have
   */
  @Traced
  public void deleteReferenceResourceForResourceType(
      UUID workspaceUuid,
      UUID resourceId,
      WsmResourceType resourceType,
      AuthenticatedUserRequest userRequest) {
    if (resourceDao.deleteReferencedResourceForResourceType(
        workspaceUuid, resourceId, resourceType)) {
      workspaceActivityLogService.writeActivity(
          userRequest,
          workspaceUuid,
          OperationType.DELETE,
          resourceId.toString(),
          ActivityLogChangedTarget.RESOURCE);
    }
  }

  @Traced
  public ReferencedResource getReferenceResource(UUID workspaceId, UUID resourceId) {
    return resourceDao.getResource(workspaceId, resourceId).castToReferencedResource();
  }

  @Traced
  public ReferencedResource getReferenceResourceByName(UUID workspaceUuid, String name) {
    return resourceDao.getResourceByName(workspaceUuid, name).castToReferencedResource();
  }

  @Traced
  public boolean checkAccess(
      UUID workspaceUuid, UUID resourceId, AuthenticatedUserRequest userRequest) {
    ReferencedResource referencedResource =
        resourceDao.getResource(workspaceUuid, resourceId).castToReferencedResource();
    return referencedResource.checkAccess(beanBag, userRequest);
  }

  @Traced
  public ReferencedResource cloneReferencedResource(
      ReferencedResource sourceReferencedResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId,
      @Nullable String name,
      @Nullable String description,
      @Nullable String email,
      CloningInstructions cloningInstructions,
      AuthenticatedUserRequest userRequest) {

    ReferencedResource destinationResource =
        sourceReferencedResource
            .buildReferencedClone(
                destinationWorkspaceId,
                destinationResourceId,
                destinationFolderId,
                name,
                description,
                email)
            .castToReferencedResource();

    final String jobDescription =
        String.format(
            "Clone referenced resource %s; id %s; name %s",
            sourceReferencedResource.getResourceType(), destinationResourceId, name);

    // If TPS is enabled, then we want to merge policies when cloning a bucket
    boolean mergePolicies = features.isTpsEnabled();

    final JobBuilder jobBuilder =
        jobService
            .newJob()
            .description(jobDescription)
            .flightClass(CloneReferencedResourceFlight.class)
            .resource(sourceReferencedResource)
            .workspaceId(destinationWorkspaceId.toString())
            .operationType(OperationType.CLONE)
            .addParameter(
                WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
                destinationWorkspaceId)
            .addParameter(ResourceKeys.RESOURCE, sourceReferencedResource)
            .addParameter(ResourceKeys.DESTINATION_RESOURCE, destinationResource)
            .addParameter(WorkspaceFlightMapKeys.MERGE_POLICIES, mergePolicies)
            .addParameter(ResourceKeys.CLONING_INSTRUCTIONS, cloningInstructions)
            .addParameter(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    return jobBuilder.submitAndWait(ReferencedResource.class);
  }

  /**
   * Convenience function that checks existence of a referenced resource within a workspace,
   * followed by an authorization check against the workspace.
   *
   * <p>Throws ResourceNotFound from getResource if the resource does not exist in the specified
   * workspace, regardless of the user's permission.
   *
   * <p>????
   *
   * <p>Throws InvalidControlledResourceException if the given resource is not controlled.
   *
   * <p>Throws ForbiddenException if the user is not permitted to perform the specified action on
   * the resource in question.
   *
   * @param userRequest the user's authenticated request
   * @param workspaceUuid id of the workspace this resource exists in
   * @param resourceId id of the resource in question
   * @param action the action to authorize against the resource
   * @return validated resource
   */
  @Traced
  public ReferencedResource validateReferencedResourceAndAction(
      AuthenticatedUserRequest userRequest, UUID workspaceUuid, UUID resourceId, String action) {
    workspaceService.validateWorkspaceAndAction(userRequest, workspaceUuid, action);
    WsmResource resource = resourceDao.getResource(workspaceUuid, resourceId);
    ReferencedResource referencedResource = resource.castToReferencedResource();

    switch (referencedResource.getState()) {
      case READY:
        return referencedResource;

      case CREATING, DELETING, UPDATING:
        throw new ResourceIsBusyException(
            "Another operation is running on the resource; try again later");

      case BROKEN:
        if (!SamConstants.SamWorkspaceAction.DELETE_REFERENCE.equals(action)) {
          throw new ResourceStateConflictException(
              "Delete is the only operation allowed on a resource in the broken state");
        }
        return referencedResource;

      default:
        throw new InternalLogicException("Unexpected case: " + referencedResource.getState());
    }
  }
}
