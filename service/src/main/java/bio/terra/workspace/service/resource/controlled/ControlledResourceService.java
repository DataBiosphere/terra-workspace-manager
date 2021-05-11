package bio.terra.workspace.service.resource.controlled;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.controlled.exception.InvalidControlledResourceException;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceFlight;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.stage.StageService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
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
    SamService.rethrowIfSamInterrupted(
        () ->
            samService.checkAuthz(
                userReq,
                controlledResource.getCategory().getSamResourceName(),
                resourceId.toString(),
                action),
        "checkAuthz");
    return controlledResource;
  }

  /** Starts a create controlled bucket resource, blocking until its job is finished. */
  public ControlledGcsBucketResource createBucket(
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

  /** Starts a create controlled BigQuery dataset resource, blocking until its job is finished. */
  public ControlledBigQueryDatasetResource createBqDataset(
      ControlledBigQueryDatasetResource resource,
      ApiGcpBigQueryDatasetCreationParameters creationParameters,
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
    return jobBuilder.submitAndWait(ControlledBigQueryDatasetResource.class);
  }

  /** Starts a create controlled AI Notebook instance resource job, returning the job id. */
  public String createAiNotebookInstance(
      ControlledAiNotebookInstanceResource resource,
      ApiGcpAiNotebookInstanceCreationParameters creationParameters,
      List<ControlledResourceIamRole> privateResourceIamRoles,
      ApiJobControl jobControl,
      String resultPath,
      AuthenticatedUserRequest userRequest) {
    JobBuilder jobBuilder =
        commonCreationJobBuilder(
            resource, privateResourceIamRoles, jobControl, resultPath, userRequest);
    jobBuilder.addParameter(ControlledResourceKeys.CREATE_NOTEBOOK_PARAMETERS, creationParameters);
    return jobBuilder.submit();
  }

  /** Create a JobBuilder for creating controlled resources with the common parameters populated. */
  private JobBuilder commonCreationJobBuilder(
      ControlledResource resource,
      List<ControlledResourceIamRole> privateResourceIamRoles,
      ApiJobControl jobControl,
      String resultPath,
      AuthenticatedUserRequest userRequest) {
    // Pre-flight assertions
    validateCreateFlightPrerequisites(resource, userRequest);

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

  private void validateCreateFlightPrerequisites(
      ControlledResource resource, AuthenticatedUserRequest userRequest) {
    stageService.assertMcWorkspace(resource.getWorkspaceId(), "createControlledResource");
    workspaceService.validateWorkspaceAndAction(
        userRequest,
        resource.getWorkspaceId(),
        resource.getCategory().getSamCreateResourceAction());
    validateOnlySelfAssignment(resource, userRequest);
  }

  public ControlledResource getControlledResource(
      UUID workspaceId, UUID resourceId, AuthenticatedUserRequest userReq) {
    stageService.assertMcWorkspace(workspaceId, "getControlledResource");
    validateControlledResourceAndAction(
        userReq, workspaceId, resourceId, SamControlledResourceActions.READ_ACTION);
    WsmResource wsmResource = resourceDao.getResource(workspaceId, resourceId);
    return wsmResource.castToControlledResource();
  }

  /**
   * Update the name and description metadata fields of a controlled resource. These are only stored
   * inside WSM, so this does not require any calls to clouds.
   *
   * @param workspaceId workspace of interest
   * @param resourceId resource to update
   * @param name name to change - may be null, in which case resource name will not be changed.
   * @param description description to change - may be null, in which case resource description will
   *     not be changed.
   */
  public void updateControlledResourceMetadata(
      UUID workspaceId,
      UUID resourceId,
      @Nullable String name,
      @Nullable String description,
      AuthenticatedUserRequest userReq) {
    stageService.assertMcWorkspace(workspaceId, "updateControlledResource");
    validateControlledResourceAndAction(
        userReq, workspaceId, resourceId, SamControlledResourceActions.EDIT_ACTION);
    // Name may be null if the user is not updating it in this request.
    if (name != null) {
      ValidationUtils.validateResourceName(name);
    }
    resourceDao.updateResource(workspaceId, resourceId, name, description);
  }

  /** Synchronously delete a controlled resource. */
  public void deleteControlledResourceSync(
      UUID workspaceId, UUID resourceId, AuthenticatedUserRequest userRequest) {

    JobBuilder deleteJob =
        commonDeletionJobBuilder(
            UUID.randomUUID().toString(), workspaceId, resourceId, null, userRequest);
    // Delete flight does not produce a result, so the resultClass parameter here is never used.
    deleteJob.submitAndWait(Void.class);
  }

  /**
   * Asynchronously delete a controlled resource. Returns the ID of the flight running the delete
   * job.
   */
  public String deleteControlledResourceAsync(
      ApiJobControl jobControl,
      UUID workspaceId,
      UUID resourceId,
      String resultPath,
      AuthenticatedUserRequest userRequest) {

    JobBuilder deleteJob =
        commonDeletionJobBuilder(
            jobControl.getId(), workspaceId, resourceId, resultPath, userRequest);
    return deleteJob.submit();
  }

  /**
   * Creates and returns a JobBuilder object for deleting a controlled resource. Depending on the
   * type of resource being deleted, this job may need to run asynchronously.
   */
  private JobBuilder commonDeletionJobBuilder(
      String jobId,
      UUID workspaceId,
      UUID resourceId,
      String resultPath,
      AuthenticatedUserRequest userRequest) {
    stageService.assertMcWorkspace(workspaceId, "deleteControlledResource");
    validateControlledResourceAndAction(
        userRequest, workspaceId, resourceId, SamControlledResourceActions.DELETE_ACTION);
    final String jobDescription = "Delete controlled resource; id: " + resourceId.toString();

    return jobService
        .newJob(jobDescription, jobId, DeleteControlledResourceFlight.class, null, userRequest)
        .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId.toString())
        .addParameter(ResourceKeys.RESOURCE_ID, resourceId.toString())
        .addParameter(JobMapKeys.RESULT_PATH.getKeyName(), resultPath);
  }

  private void validateOnlySelfAssignment(
      ControlledResource controlledResource, AuthenticatedUserRequest userRequest) {
    if (!controlledResource.getAccessScope().equals(AccessScopeType.ACCESS_SCOPE_PRIVATE)) {
      // No need to handle SHARED resources
      return;
    }
    final String requestUserEmail =
        SamService.rethrowIfSamInterrupted(
            () -> samService.getRequestUserEmail(userRequest), "validateOnlySelfAssignment");
    // If there is no assigned user, this condition is satisfied.
    //noinspection deprecation
    final boolean isAllowed =
        controlledResource.getAssignedUser().map(requestUserEmail::equals).orElse(true);
    if (!isAllowed) {
      throw new BadRequestException(
          "User may only assign a private controlled resource to themselves.");
    }
  }
}
