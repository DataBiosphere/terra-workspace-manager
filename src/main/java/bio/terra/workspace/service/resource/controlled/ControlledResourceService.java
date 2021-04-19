package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.controlled.exception.InvalidControlledResourceException;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceGcsBucketFlight;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.stage.StageService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.List;
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
  private final SamService samService;

  @Autowired
  public ControlledResourceService(
      JobService jobService,
      WorkspaceService workspaceService,
      ResourceDao resourceDao,
      StageService stageService,
      SamService samService) {
    this.jobService = jobService;
    this.workspaceService = workspaceService;
    this.resourceDao = resourceDao;
    this.stageService = stageService;
    this.samService = samService;
  }

  /**
   * Convenience function that checks existence of a controlled resource within a workspace,
   * followed by an authorization check against that resource.
   *
   * <p>Throws ResourceNotFound from getResource if the workspace does not exist in the specified
   * workspace, regardless of the user's permission.
   *
   * <p>Throws
   *
   * <p>Throws InvalidControlledResourceException if the given resource is not controlled.
   *
   * <p>Throws UnauthorizedException if the user is not permitted to perform the specified action on
   * the resource in question.
   *
   * <p>Returns the controlled resource object if it exists and the user is permitted to perform the
   * specified action.
   *
   * @param userReq the user's authenticated request
   * @param workspaceId if of the workspace this resource exists in
   * @param resourceId id of the resource in question
   * @param action the action to authorize against the resource
   * @return the resource, if it exists and the user is permitted to perform the specified action.
   */
  @Traced
  public ControlledResource validateControlledResourceAndAction(
      AuthenticatedUserRequest userReq, UUID workspaceId, UUID resourceId, String action) {
    WsmResource resource = resourceDao.getResource(workspaceId, resourceId);
    // TODO(PF-640): also check that the user has
    if (resource.getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InvalidControlledResourceException(
          String.format("Resource %s is not a controlled resource.", resource.getResourceId()));
    }
    ControlledResource controlledResource = resource.castToControlledResource();
    samService.checkAuthz(
        userReq,
        controlledResource.getCategory().getSamResourceName(),
        resourceId.toString(),
        action);
    return controlledResource;
  }

  /** Starts a create controlled bucket resource, blocking until its job is finished. */
  public ControlledGcsBucketResource syncCreateBucket(
      ControlledGcsBucketResource resource,
      ApiGcpGcsBucketCreationParameters creationParameters,
      List<ControlledResourceIamRole> privateResourceIamRoles,
      AuthenticatedUserRequest userRequest) {
    JobBuilder jobBuilder =
        commonCreationJobBuilder(
                resource,
                privateResourceIamRoles,
                new ApiJobControl().id(UUID.randomUUID().toString()),
                null,
                userRequest)
            .addParameter(ControlledResourceKeys.CREATION_PARAMETERS, creationParameters);
    return jobBuilder.submitAndWait(ControlledGcsBucketResource.class);
  }

  /** Create a JobBuilder for creating controlled resources with the common parameters populated. */
  private JobBuilder commonCreationJobBuilder(
      ControlledResource resource,
      List<ControlledResourceIamRole> privateResourceIamRoles,
      ApiJobControl jobControl,
      String resultPath,
      AuthenticatedUserRequest userRequest) {
    stageService.assertMcWorkspace(resource.getWorkspaceId(), "createControlledResource");
    workspaceService.validateWorkspaceAndAction(
        userRequest,
        resource.getWorkspaceId(),
        resource.getCategory().getSamCreateResourceAction());
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
            .addParameter(
                ControlledResourceKeys.PRIVATE_RESOURCE_IAM_ROLES, privateResourceIamRoles)
            .addParameter(JobMapKeys.RESULT_PATH.getKeyName(), resultPath);
    return jobBuilder;
  }

  public ControlledResource getControlledResource(
      UUID workspaceId, UUID resourceId, AuthenticatedUserRequest userReq) {
    stageService.assertMcWorkspace(workspaceId, "getControlledResource");
    validateControlledResourceAndAction(
        userReq, workspaceId, resourceId, SamControlledResourceActions.READ_ACTION);
    WsmResource wsmResource = resourceDao.getResource(workspaceId, resourceId);
    return wsmResource.castToControlledResource();
  }

  public String deleteControlledGcsBucket(
      ApiJobControl jobControl,
      UUID workspaceId,
      UUID resourceId,
      String resultPath,
      AuthenticatedUserRequest userRequest) {
    stageService.assertMcWorkspace(workspaceId, "deleteControlledGcsBucket");
    validateControlledResourceAndAction(
        userRequest, workspaceId, resourceId, SamControlledResourceActions.DELETE_ACTION);
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
            .addParameter(ResourceKeys.RESOURCE_ID, resourceId.toString())
            .addParameter(JobMapKeys.RESULT_PATH.getKeyName(), resultPath);

    return jobBuilder.submit();
  }
}
