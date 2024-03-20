package bio.terra.workspace.service.resource;

import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.flight.UpdateResourceFlight;
import bio.terra.workspace.service.resource.model.CommonUpdateParameters;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Support for cross-resource methods */
@Component
public class WsmResourceService {

  private final ResourceDao resourceDao;
  private final JobService jobService;

  @Autowired
  public WsmResourceService(ResourceDao resourceDao, JobService jobService) {
    this.resourceDao = resourceDao;
    this.jobService = jobService;
  }

  public List<WsmResource> enumerateResources(
      UUID workspaceUuid,
      @Nullable WsmResourceFamily cloudResourceType,
      @Nullable StewardshipType stewardshipType,
      int offset,
      int limit) {
    return resourceDao.enumerateResources(
        workspaceUuid, cloudResourceType, stewardshipType, offset, limit);
  }

  public WsmResource getResource(UUID workspaceUuid, UUID resourceUuid) {
    return resourceDao.getResource(workspaceUuid, resourceUuid);
  }

  public WsmResource getResourceByName(UUID workspaceUuid, String resourceName) {
    return resourceDao.getResourceByName(workspaceUuid, resourceName);
  }

  public void updateResource(
      AuthenticatedUserRequest userRequest,
      WsmResource resource,
      CommonUpdateParameters commonUpdateParameters,
      Object updateParameters) {

    String jobDescription =
        String.format(
            "Update resource - type %s; id %s; name %s",
            resource.getResourceType(), resource.getResourceId(), resource.getName());

    final JobBuilder jobBuilder =
        jobService
            .newJob()
            .description(jobDescription)
            .flightClass(UpdateResourceFlight.class)
            .resource(resource)
            .operationType(OperationType.UPDATE)
            .userRequest(userRequest)
            .workspaceId(resource.getWorkspaceId().toString())
            .resourceType(resource.getResourceType())
            .stewardshipType(resource.getStewardshipType())
            .addParameter(WorkspaceFlightMapKeys.ResourceKeys.UPDATE_PARAMETERS, updateParameters)
            .addParameter(
                WorkspaceFlightMapKeys.ResourceKeys.COMMON_UPDATE_PARAMETERS,
                commonUpdateParameters);
    jobBuilder.submitAndWait(Void.class);
  }

  public void updateResourceProperties(
      UUID workspaceUuid, UUID resourceUuid, Map<String, String> properties) {
    resourceDao.updateResourceProperties(workspaceUuid, resourceUuid, properties);
  }

  public void deleteResourceProperties(
      UUID workspaceUuid, UUID resourceUuid, List<String> propertyKeys) {
    resourceDao.deleteResourceProperties(workspaceUuid, resourceUuid, propertyKeys);
  }
}
