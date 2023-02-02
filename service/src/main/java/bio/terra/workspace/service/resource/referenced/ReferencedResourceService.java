package bio.terra.workspace.service.resource.referenced;

import bio.terra.common.exception.ConflictException;
import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsUpdateMode;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.flight.update.UpdateReferenceResourceFlight;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.OperationType;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.HashSet;
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
  private final TpsApiDispatch tpsApiDispatch;
  private final WorkspaceActivityLogService workspaceActivityLogService;

  @Autowired
  public ReferencedResourceService(
      JobService jobService,
      ResourceDao resourceDao,
      WorkspaceService workspaceService,
      FlightBeanBag beanBag,
      WorkspaceActivityLogService workspaceActivityLogService,
      TpsApiDispatch tpsApiDispatch) {
    this.jobService = jobService;
    this.resourceDao = resourceDao;
    this.workspaceService = workspaceService;
    this.beanBag = beanBag;
    this.tpsApiDispatch = tpsApiDispatch;
    this.workspaceActivityLogService = workspaceActivityLogService;
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
  public ReferencedResource createReferenceResourceForClone(
      ReferencedResource resourceToClone,
      ReferencedResource sourceReferencedResource,
      AuthenticatedUserRequest userRequest) {

    validateDestinationWorkspacePolicies(
        sourceReferencedResource.getWorkspaceId(),
        resourceToClone.getWorkspaceId(),
        sourceReferencedResource.getResourceType().getCloudPlatform());
    resourceDao.createReferencedResource(resourceToClone);
    var result =
        tpsApiDispatch.mergePao(
            sourceReferencedResource.getWorkspaceId(),
            resourceToClone.getWorkspaceId(),
            TpsUpdateMode.FAIL_ON_CONFLICT);

    // Logs CLONE in the source workspace for the source resource that is cloned.
    workspaceActivityLogService.writeActivity(
        userRequest,
        sourceReferencedResource.getWorkspaceId(),
        OperationType.CLONE,
        sourceReferencedResource.getResourceId().toString(),
        ActivityLogChangedTarget.RESOURCE);
    // Logs CREATE in the destination workspace for the new resource that is created.
    workspaceActivityLogService.writeActivity(
        userRequest,
        resourceToClone.getWorkspaceId(),
        OperationType.CREATE,
        resourceToClone.getResourceId().toString(),
        ActivityLogChangedTarget.RESOURCE);
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
    if (resourceDao.deleteResourceForResourceType(workspaceUuid, resourceId, resourceType)) {
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
  public List<ReferencedResource> enumerateReferences(
      UUID workspaceUuid, int offset, int limit, AuthenticatedUserRequest userRequest) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.READ);
    return resourceDao.enumerateReferences(workspaceUuid, offset, limit);
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
      String createdByEmail,
      AuthenticatedUserRequest userRequest) {
    ReferencedResource destinationResource =
        sourceReferencedResource
            .buildReferencedClone(
                destinationWorkspaceId,
                destinationResourceId,
                destinationFolderId,
                name,
                description,
                createdByEmail)
            .castToReferencedResource();

    return createReferenceResourceForClone(
        destinationResource, sourceReferencedResource, userRequest);
  }

  private void validateDestinationWorkspacePolicies(
      UUID sourceWorkspaceId, UUID destinationWorkspaceId, CloudPlatform platform) {
    TpsPaoUpdateResult dryRunResults =
        tpsApiDispatch.mergePao(sourceWorkspaceId, destinationWorkspaceId, TpsUpdateMode.DRY_RUN);

    if (!dryRunResults.getConflicts().isEmpty()) {
      throw new ConflictException("Policy merge has conflicts");
    }

    if (platform == CloudPlatform.ANY) {
      logger.warn("Cannot validate regions for CloudPlatform.ANY");
      return;
    }

    HashSet<String> validRegions = new HashSet<>();
    validRegions.addAll(
        tpsApiDispatch.listValidRegionsForPao(dryRunResults.getResultingPao(), platform));
    List<ControlledResource> existingResources =
        resourceDao.listControlledResources(destinationWorkspaceId, platform);

    for (var existingResource : existingResources) {
      if (!validRegions.contains(existingResource.getRegion())) {
        throw new ConflictException(
            "Workspace contains resources that would be outside of the merged policy.");
      }
    }
  }
}
