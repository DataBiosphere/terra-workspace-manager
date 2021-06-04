package bio.terra.workspace.service.resource.controlled;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.db.DbRetryUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketUpdateParameters;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledGcsBucketResourceFlight;
import bio.terra.workspace.service.stage.StageService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
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
  private final ControlledResourceMetadataManager controlledResourceMetadataManager;

  @Autowired
  public ControlledResourceService(
      JobService jobService,
      WorkspaceService workspaceService,
      ResourceDao resourceDao,
      StageService stageService,
      SamService samService,
      ControlledResourceMetadataManager controlledResourceMetadataManager) {
    this.jobService = jobService;
    this.workspaceService = workspaceService;
    this.resourceDao = resourceDao;
    this.stageService = stageService;
    this.samService = samService;
    this.controlledResourceMetadataManager = controlledResourceMetadataManager;
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

  public ControlledGcsBucketResource updateGcsBucket(
      ControlledGcsBucketResource resource,
      @Nullable ApiGcpGcsBucketUpdateParameters updateParameters,
      AuthenticatedUserRequest userRequest,
      @Nullable String resourceName,
      @Nullable String resourceDescription) {
    final String jobDescription =
        String.format(
            "Update controlled GCS Bucket resource %s; id %s; name %s",
            resource.getBucketName(), resource.getResourceId(), resource.getName());
    final JobBuilder jobBuilder =
        jobService
            .newJob(
                jobDescription,
                UUID.randomUUID().toString(), // no need to track ID
                UpdateControlledGcsBucketResourceFlight.class,
                resource,
                userRequest)
            .addParameter(ControlledResourceKeys.UPDATE_PARAMETERS, updateParameters)
            .addParameter(ControlledResourceKeys.RESOURCE_NAME, resourceName)
            .addParameter(ControlledResourceKeys.RESOURCE_DESCRIPTION, resourceDescription);
    return jobBuilder.submitAndWait(ControlledGcsBucketResource.class);
  }

  public String cloneGcsBucket(
      ControlledGcsBucketResource sourceBucketResource,
      UUID destinationWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String location,
      @Nullable String name,
      @Nullable String description,
      @Nullable String bucketName,
      AuthenticatedUserRequest userRequest) {
    return null;
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
    if (privateResourceIamRoles.stream()
        .noneMatch(role -> role.equals(ControlledResourceIamRole.WRITER))) {
      throw new BadRequestException(
          "A private, controlled AI Notebook instance must have the writer role or else it is not useful.");
    }
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
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userReq, workspaceId, resourceId, SamControlledResourceActions.READ_ACTION);
    return DbRetryUtils.throwIfInterrupted(
        () -> resourceDao.getResource(workspaceId, resourceId).castToControlledResource());
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
    controlledResourceMetadataManager.validateControlledResourceAndAction(
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
