package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiGcsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiIamRole;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceGcsBucketFlight;
import bio.terra.workspace.service.stage.StageService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** CRUD methods for controlled objects. */
@Component
public class ControlledResourceService {

  private final JobService jobService;
  private final WorkspaceService workspaceService;
  private final ResourceDao resourceDao;
  private final StageService stageService;

  @Autowired
  public ControlledResourceService(
      JobService jobService,
      WorkspaceService workspaceService,
      ResourceDao resourceDao,
      StageService stageService) {
    this.jobService = jobService;
    this.workspaceService = workspaceService;
    this.resourceDao = resourceDao;
    this.stageService = stageService;
  }

  public String createControlledResource(
      ControlledResource resource,
      ApiGcsBucketCreationParameters creationParameters,
      ApiIamRole creationApiIamRole,
      ApiJobControl jobControl,
      AuthenticatedUserRequest userRequest) {
    stageService.assertMcWorkspace(resource.getWorkspaceId(), "createControlledResource");
    workspaceService.validateWorkspaceAndAction(
        userRequest, resource.getWorkspaceId(), SamConstants.SAM_WORKSPACE_WRITE_ACTION);
    final String jobDescription =
        String.format(
            "Create controlled resource %s; id %s; name %s",
            resource.getResourceType(), resource.getResourceId(), resource.getName());

    final JobBuilder jobBuilder =
        jobService
            .newJob(
                jobDescription,
                jobControl.getId(),
                CreateControlledResourceFlight.class,
                resource,
                userRequest)
            .addParameter(ControlledResourceKeys.CREATION_PARAMETERS, creationParameters)
            .addParameter(ControlledResourceKeys.IAM_ROLE, creationApiIamRole);

    return jobBuilder.submit();
  }

  public ControlledResource getControlledResource(
      UUID workspaceId, UUID resourceId, AuthenticatedUserRequest userReq) {
    stageService.assertMcWorkspace(workspaceId, "getControlledResource");
    workspaceService.validateWorkspaceAndAction(
        userReq, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);
    WsmResource wsmResource = resourceDao.getResource(workspaceId, resourceId);
    return wsmResource.castControlledResource();
  }

  public String deleteControlledGcsBucket(
      ApiJobControl jobControl,
      UUID workspaceId,
      UUID resourceId,
      AuthenticatedUserRequest userRequest) {
    stageService.assertMcWorkspace(workspaceId, "deleteControlledGcsBucket");
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SAM_WORKSPACE_WRITE_ACTION);
    final String jobDescription =
        "Delete controlled GCS bucket resource; id: " + resourceId.toString();

    final JobBuilder jobBuilder =
        jobService
            .newJob(
                jobDescription,
                jobControl.getId(),
                DeleteControlledResourceGcsBucketFlight.class,
                null,
                userRequest)
            .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId.toString())
            .addParameter(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_ID, resourceId.toString());

    return jobBuilder.submit();
  }
}
