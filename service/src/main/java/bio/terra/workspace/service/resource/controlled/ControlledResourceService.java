package bio.terra.workspace.service.resource.controlled;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.ApplicationDao;
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
import bio.terra.workspace.service.resource.controlled.mappings.ControlledResourceSyncMapping.SyncMapping;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.stage.StageService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import com.google.cloud.Policy;
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

  private static final int MAX_ASSIGNED_USER_LENGTH = 254;

  private final JobService jobService;
  private final WorkspaceService workspaceService;
  private final ResourceDao resourceDao;
  private final ApplicationDao applicationDao;
  private final StageService stageService;
  private final SamService samService;
  private final GcpCloudContextService gcpCloudContextService;
  private final ControlledResourceMetadataManager controlledResourceMetadataManager;

  @Autowired
  public ControlledResourceService(
      JobService jobService,
      WorkspaceService workspaceService,
      ResourceDao resourceDao,
      ApplicationDao applicationDao,
      StageService stageService,
      SamService samService,
      GcpCloudContextService gcpCloudContextService,
      ControlledResourceMetadataManager controlledResourceMetadataManager) {
    this.jobService = jobService;
    this.workspaceService = workspaceService;
    this.resourceDao = resourceDao;
    this.applicationDao = applicationDao;
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
      ControlledResourceIamRole privateResourceIamRole,
      AuthenticatedUserRequest userRequest) {
    JobBuilder jobBuilder =
        commonCreationJobBuilder(resource, privateResourceIamRole, userRequest)
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
            .addParameter(ResourceKeys.RESOURCE_NAME, resourceName)
            .addParameter(ResourceKeys.RESOURCE_DESCRIPTION, resourceDescription);
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
    // Authorization check is handled as the first flight step rather than before the flight, as
    // this flight is re-used for cloneWorkspace.

    final ControlledResource sourceBucketResource =
        getControlledResource(sourceWorkspaceId, sourceResourceId, userRequest);

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
            .addParameter(ResourceKeys.RESOURCE_NAME, destinationResourceName)
            .addParameter(ResourceKeys.RESOURCE_DESCRIPTION, destinationDescription)
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
   * Starts a job to create controlled BigQuery dataset resource, blocking until its job is
   * finished.
   */
  public ControlledBigQueryDatasetResource createBigQueryDataset(
      ControlledBigQueryDatasetResource resource,
      ApiGcpBigQueryDatasetCreationParameters creationParameters,
      ControlledResourceIamRole privateResourceIamRole,
      AuthenticatedUserRequest userRequest) {
    JobBuilder jobBuilder =
        commonCreationJobBuilder(resource, privateResourceIamRole, userRequest)
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
            .addParameter(ResourceKeys.RESOURCE_NAME, resourceName)
            .addParameter(ResourceKeys.RESOURCE_DESCRIPTION, resourceDescription);
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
    // Authorization check is handled as the first flight step rather than before the flight, as
    // this flight is re-used for cloneWorkspace.

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
            .addParameter(ResourceKeys.RESOURCE_NAME, destinationResourceName)
            .addParameter(ResourceKeys.RESOURCE_DESCRIPTION, destinationDescription)
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
      @Nullable ControlledResourceIamRole privateResourceIamRole,
      @Nullable ApiJobControl jobControl,
      String resultPath,
      AuthenticatedUserRequest userRequest) {

    // Special check for notebooks: READER is not a useful role
    if (privateResourceIamRole == ControlledResourceIamRole.READER) {
      throw new BadRequestException(
          "A private, controlled AI Notebook instance must have the writer or editor role or else it is not useful.");
    }

    JobBuilder jobBuilder =
        commonCreationJobBuilder(
            resource, privateResourceIamRole, jobControl, resultPath, userRequest);
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
   * Simpler interface for synchronous controlled resource creation
   */
  private JobBuilder commonCreationJobBuilder(
      ControlledResource resource,
      ControlledResourceIamRole privateResourceIamRole,
      AuthenticatedUserRequest userRequest) {
    return commonCreationJobBuilder(resource, privateResourceIamRole, null, null, userRequest);
  }

  /**
   * Create a JobBuilder for creating controlled resources with the common parameters populated.
   */
  private JobBuilder commonCreationJobBuilder(
      ControlledResource resource,
      @Nullable ControlledResourceIamRole privateResourceIamRole,
      @Nullable ApiJobControl jobControl,
      @Nullable String resultPath,
      AuthenticatedUserRequest userRequest) {

    // Pre-flight assertions
    validateCreateFlightPrerequisites(resource, userRequest);

    final String jobDescription =
        String.format(
            "Create controlled resource %s; id %s; name %s",
            resource.getResourceType(), resource.getResourceId(), resource.getName());

    // Get or create a job id for the flight
    String jobId =
        Optional.ofNullable(jobControl)
            .map(ApiJobControl::getId)
            .orElse(UUID.randomUUID().toString());

    return jobService
        .newJob(jobDescription, jobId, CreateControlledResourceFlight.class, resource, userRequest)
        .addParameter(ControlledResourceKeys.PRIVATE_RESOURCE_IAM_ROLE, privateResourceIamRole)
        .addParameter(JobMapKeys.RESULT_PATH.getKeyName(), resultPath);
  }

  private void validateCreateFlightPrerequisites(
      ControlledResource resource, AuthenticatedUserRequest userRequest) {
    stageService.assertMcWorkspace(resource.getWorkspaceId(), "createControlledResource");
    workspaceService.validateWorkspaceAndAction(
        userRequest,
        resource.getWorkspaceId(),
        resource.getCategory().getSamCreateResourceAction());
  }

  /**
   * When creating application-owned resources, we need to hold the UUID of the application in the
   * ControlledResource object. On the one hand, maybe this should be done as part of the create
   * flight. On the other hand, the pattern we have is to assemble the complete ControlledResource
   * in the controller and have that class be immutable. So we do this lookup and error check early
   * on. Throws ApplicationNotFound if there is no matching application record.
   *
   * @param managedBy the managed by type
   * @param userRequest the user request
   * @return null if not an application managed resource; application UUID otherwise
   */
  public @Nullable
  UUID getAssociatedApp(
      ManagedByType managedBy, AuthenticatedUserRequest userRequest) {
    if (managedBy != ManagedByType.MANAGED_BY_APPLICATION) {
      return null;
    }

    String applicationEmail =
        SamRethrow.onInterrupted(
            () -> samService.getUserEmailFromSam(userRequest), "get application email");

    WsmApplication application = applicationDao.getApplicationByEmail(applicationEmail);
    return application.getApplicationId();
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

  public Policy configureGcpPolicyForResource(
      ControlledResource resource,
      String projectId,
      Policy currentPolicy,
      AuthenticatedUserRequest userRequest)
      throws InterruptedException {

    GcpPolicyBuilder gcpPolicyBuilder = new GcpPolicyBuilder(resource, projectId, currentPolicy);

    List<SyncMapping> syncMappings = resource.getCategory().getSyncMappings();
    for (SyncMapping syncMapping : syncMappings) {
      String policyGroup = syncPolicyGroup(resource, syncMapping, userRequest);
      gcpPolicyBuilder.addResourceBinding(syncMapping.getTargetRole(), policyGroup);
    }

    return gcpPolicyBuilder.build();
  }

  private String syncPolicyGroup(
      ControlledResource resource, SyncMapping syncMapping, AuthenticatedUserRequest userRequest)
      throws InterruptedException {

    switch (syncMapping.getRoleSource()) {
      case RESOURCE:
        return samService.syncResourcePolicy(
            resource,
            syncMapping
                .getResourceRole()
                .orElseThrow(() -> new InternalLogicException("Mismatched syncMapping")),
            userRequest);

      case WORKSPACE:
        return samService.syncWorkspacePolicy(
            resource.getWorkspaceId(),
            syncMapping
                .getWorkspaceRole()
                .orElseThrow(() -> new InternalLogicException("Mismatched syncMapping")),
            userRequest);
    }
    throw new InternalLogicException("Invalid role source");
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
}
