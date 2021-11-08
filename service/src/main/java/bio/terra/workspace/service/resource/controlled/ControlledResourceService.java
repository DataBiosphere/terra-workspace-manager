package bio.terra.workspace.service.resource.controlled;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketUpdateParameters;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.CloneControlledGcsBucketResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.CloneControlledGcpBigQueryDatasetResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledBigQueryDatasetResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledGcsBucketResourceFlight;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.stage.StageService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * CRUD methods for controlled objects.
 */
@Component
public class ControlledResourceService {

  private static final int MAX_ASSIGNED_USER_LENGTH = 128;

  private final JobService jobService;
  private final WorkspaceService workspaceService;
  private final ResourceDao resourceDao;
  private final StageService stageService;
  private final SamService samService;
  private final GcpCloudContextService gcpCloudContextService;
  private final ControlledResourceMetadataManager controlledResourceMetadataManager;

  @Autowired
  public ControlledResourceService(
      JobService jobService,
      WorkspaceService workspaceService,
      ResourceDao resourceDao,
      StageService stageService,
      SamService samService,
      GcpCloudContextService gcpCloudContextService,
      ControlledResourceMetadataManager controlledResourceMetadataManager) {
    this.jobService = jobService;
    this.workspaceService = workspaceService;
    this.resourceDao = resourceDao;
    this.stageService = stageService;
    this.samService = samService;
    this.gcpCloudContextService = gcpCloudContextService;
    this.controlledResourceMetadataManager = controlledResourceMetadataManager;
  }

  /**
   * Starts a create controlled bucket resource, blocking until its job is finished.
   */
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

  /**
   * Clone a GCS Bucket to another workspace.
   *
   * @param sourceWorkspaceId - workspace ID fo source bucket
   * @param sourceResourceId - resource ID of source bucket
   * @param destinationWorkspaceId - workspace ID to clone into
   * @param jobControl - job service control structure
   * @param userRequest - incoming request
   * @param destinationResourceName - override value for resource name. Re-uses previous name if
   * null
   * @param destinationDescription - override value for resource description. Re-uses previous value
   * if null
   * @param destinationBucketName - GCS bucket name for cloned bucket. If null, a random name will
   * be generated
   * @param destinationLocation - location string for the destination bucket. If null, the source
   * bucket's location will be used.
   * @param cloningInstructionsOverride - cloning instructions for this operation. If null, the
   * source bucket's cloning instructions will be honored.
   * @return - Job ID of submitted flight
   */
  public String cloneGcsBucket(
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destinationWorkspaceId,
      ApiJobControl jobControl,
      AuthenticatedUserRequest userRequest,
      @Nullable String destinationResourceName,
      @Nullable String destinationDescription,
      @Nullable String destinationBucketName,
      @Nullable String destinationLocation,
      @Nullable ApiCloningInstructionsEnum cloningInstructionsOverride) {
    stageService.assertMcWorkspace(destinationWorkspaceId, "cloneGcsBucket");

    final ControlledResource sourceBucketResource =
        getControlledResource(sourceWorkspaceId, sourceResourceId, userRequest);

    // Verify user can read source resource in Sam
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest,
        sourceBucketResource.getWorkspaceId(),
        sourceBucketResource.getResourceId(),
        SamControlledResourceActions.READ_ACTION);

    // Write access to the target workspace will be established in the create flight
    final String jobDescription =
        String.format(
            "Clone controlled resource %s; id %s; name %s",
            sourceBucketResource.getResourceType(),
            sourceBucketResource.getResourceId(),
            sourceBucketResource.getName());

    final JobBuilder jobBuilder =
        jobService
            .newJob(
                jobDescription,
                jobControl.getId(),
                CloneControlledGcsBucketResourceFlight.class,
                sourceBucketResource,
                userRequest)
            .addParameter(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, destinationWorkspaceId)
            .addParameter(ControlledResourceKeys.RESOURCE_NAME, destinationResourceName)
            .addParameter(ControlledResourceKeys.RESOURCE_DESCRIPTION, destinationDescription)
            .addParameter(ControlledResourceKeys.DESTINATION_BUCKET_NAME, destinationBucketName)
            .addParameter(ControlledResourceKeys.LOCATION, destinationLocation)
            .addParameter(
                ControlledResourceKeys.CLONING_INSTRUCTIONS,
                Optional.ofNullable(cloningInstructionsOverride)
                    .map(CloningInstructions::fromApiModel)
                    .orElse(sourceBucketResource.getCloningInstructions()));
    return jobBuilder.submit();
  }

  /**
   * Starts a create controlled BigQuery dataset resource, blocking until its job is finished.
   */
  public ControlledBigQueryDatasetResource createBigQueryDataset(
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

  /**
   * Starts an update controlled BigQuery dataset resource, blocking until its job is finished.
   */
  public ControlledBigQueryDatasetResource updateBqDataset(
      ControlledBigQueryDatasetResource resource,
      @Nullable ApiGcpBigQueryDatasetUpdateParameters updateParameters,
      AuthenticatedUserRequest userRequest,
      @Nullable String resourceName,
      @Nullable String resourceDescription) {
    final String jobDescription =
        String.format(
            "Update controlled BigQuery Dataset name %s ; resource id %s; resource name %s",
            resource.getDatasetName(), resource.getResourceId(), resource.getName());
    final JobBuilder jobBuilder =
        jobService
            .newJob(
                jobDescription,
                UUID.randomUUID().toString(), // no need to track ID
                UpdateControlledBigQueryDatasetResourceFlight.class,
                resource,
                userRequest)
            .addParameter(ControlledResourceKeys.UPDATE_PARAMETERS, updateParameters)
            .addParameter(ControlledResourceKeys.RESOURCE_NAME, resourceName)
            .addParameter(ControlledResourceKeys.RESOURCE_DESCRIPTION, resourceDescription);
    return jobBuilder.submitAndWait(ControlledBigQueryDatasetResource.class);
  }

  /**
   * Make a clone of a BigQuery dataset
   *
   * @param sourceWorkspaceId - workspace ID of original dataset
   * @param sourceResourceId - resource ID of original dataset
   * @param destinationWorkspaceId - destination (sink) workspace ID
   * @param jobControl - job control structure (should already have ID)
   * @param userRequest - request object for this call
   * @param destinationResourceName - resource name. Uses source name if null
   * @param destinationDescription - description string for cloned dataset. Source description if
   * null.
   * @param destinationDatasetName - name for new resource. Can equal source name. If null, a random
   * name will be generated
   * @param destinationLocation - location override. Uses source location if null
   * @param cloningInstructionsOverride - Cloning instructions for this clone operation, overriding
   * any existing instructions. Existing instructions are used if null.
   */
  public String cloneBigQueryDataset(
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destinationWorkspaceId,
      ApiJobControl jobControl,
      AuthenticatedUserRequest userRequest,
      @Nullable String destinationResourceName,
      @Nullable String destinationDescription,
      @Nullable String destinationDatasetName,
      @Nullable String destinationLocation,
      @Nullable ApiCloningInstructionsEnum cloningInstructionsOverride) {
    stageService.assertMcWorkspace(destinationWorkspaceId, "cloneGcpBigQueryDataset");
    final ControlledResource sourceDatasetResource =
        getControlledResource(sourceWorkspaceId, sourceResourceId, userRequest);

    // Verify user can read source resource in Sam
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest,
        sourceDatasetResource.getWorkspaceId(),
        sourceDatasetResource.getResourceId(),
        SamControlledResourceActions.READ_ACTION);

    // Write access to the target workspace will be established in the create flight
    final String jobDescription =
        String.format(
            "Clone controlled resource %s; id %s; name %s",
            sourceDatasetResource.getResourceType(),
            sourceDatasetResource.getResourceId(),
            sourceDatasetResource.getName());
    final JobBuilder jobBuilder =
        jobService
            .newJob(
                jobDescription,
                jobControl.getId(),
                CloneControlledGcpBigQueryDatasetResourceFlight.class,
                sourceDatasetResource,
                userRequest)
            .addParameter(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, destinationWorkspaceId)
            .addParameter(ControlledResourceKeys.RESOURCE_NAME, destinationResourceName)
            .addParameter(ControlledResourceKeys.RESOURCE_DESCRIPTION, destinationDescription)
            .addParameter(ControlledResourceKeys.LOCATION, destinationLocation)
            .addParameter(ControlledResourceKeys.DESTINATION_DATASET_NAME, destinationDatasetName)
            .addParameter(
                ControlledResourceKeys.CLONING_INSTRUCTIONS,
                // compute effective cloning instructions
                Optional.ofNullable(cloningInstructionsOverride)
                    .map(CloningInstructions::fromApiModel)
                    .orElse(sourceDatasetResource.getCloningInstructions()));
    return jobBuilder.submit();
  }

  /**
   * Starts a create controlled AI Notebook instance resource job, returning the job id.
   */
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
    String petSaEmail =
        SamRethrow.onInterrupted(
            () ->
                samService.getOrCreatePetSaEmail(
                    gcpCloudContextService.getRequiredGcpProject(resource.getWorkspaceId()),
                    userRequest),
            "enablePet");
    jobBuilder.addParameter(ControlledResourceKeys.CREATE_NOTEBOOK_PARAMETERS, creationParameters);
    jobBuilder.addParameter(ControlledResourceKeys.NOTEBOOK_PET_SERVICE_ACCOUNT, petSaEmail);
    return jobBuilder.submit();
  }

  /**
   * Create a JobBuilder for creating controlled resources with the common parameters populated.
   */
  private JobBuilder commonCreationJobBuilder(
      ControlledResource resource,
      List<ControlledResourceIamRole> privateResourceIamRoles,
      ApiJobControl jobControl,
      String resultPath,
      AuthenticatedUserRequest userRequest) {
    String userEmail =
        SamRethrow.onInterrupted(
            () -> samService.getUserEmailFromSam(userRequest), "commonCreationJobBuilder");
    // Pre-flight assertions
    validateCreateFlightPrerequisites(resource, userEmail, userRequest);

    final String jobDescription =
        String.format(
            "Create controlled resource %s; id %s; name %s",
            resource.getResourceType(), resource.getResourceId(), resource.getName());

    return jobService
        .newJob(
            jobDescription,
            jobControl.getId(),
            CreateControlledResourceFlight.class,
            resource,
            userRequest)
        .addParameter(ControlledResourceKeys.PRIVATE_RESOURCE_IAM_ROLES, privateResourceIamRoles)
        .addParameter(ControlledResourceKeys.PRIVATE_RESOURCE_USER_EMAIL, userEmail)
        .addParameter(JobMapKeys.RESULT_PATH.getKeyName(), resultPath);
  }

  private void validateCreateFlightPrerequisites(
      ControlledResource resource, String userEmail, AuthenticatedUserRequest userRequest) {
    stageService.assertMcWorkspace(resource.getWorkspaceId(), "createControlledResource");
    workspaceService.validateWorkspaceAndAction(
        userRequest,
        resource.getWorkspaceId(),
        resource.getCategory().getSamCreateResourceAction());
    validateOnlySelfAssignment(resource, userEmail);
  }

  public ControlledResource getControlledResource(
      UUID workspaceId, UUID resourceId, AuthenticatedUserRequest userRequest) {
    stageService.assertMcWorkspace(workspaceId, "getControlledResource");
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest, workspaceId, resourceId, SamControlledResourceActions.READ_ACTION);
    return resourceDao.getResource(workspaceId, resourceId).castToControlledResource();
  }

  /**
   * Synchronously delete a controlled resource.
   */
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

  private void validateOnlySelfAssignment(ControlledResource controlledResource, String userEmail) {
    if (!controlledResource.getAccessScope().equals(AccessScopeType.ACCESS_SCOPE_PRIVATE)) {
      // No need to handle SHARED resources
      return;
    }

    Optional<String> assignedUser = controlledResource.getAssignedUser();
    final boolean isAllowed =
        assignedUser.orElse("").length() <= MAX_ASSIGNED_USER_LENGTH &&
            // If there is no assigned user, this condition is satisfied.
            assignedUser.map(userEmail::equalsIgnoreCase).orElse(true);

    if (!isAllowed) {
      throw new BadRequestException(
          "User ("
              + userEmail
              + ") may only assign a private controlled resource to themselves ("
              + assignedUser.orElse("")
              .substring(0, Math.min(assignedUser.orElse("").length(), MAX_ASSIGNED_USER_LENGTH))
              + ").");
    }
  }
}
