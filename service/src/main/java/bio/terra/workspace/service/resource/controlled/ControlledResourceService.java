package bio.terra.workspace.service.resource.controlled;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.LandingZone;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.ServiceUnavailableException;
import bio.terra.stairway.FlightState;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAwsSageMakerNotebookCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.grant.GrantService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.ControlledResourceSyncMapping.SyncMapping;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook.ControlledAwsSageMakerNotebookResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpPolicyBuilder;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.container.CloneControlledAzureStorageContainerResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.CloneControlledGcsBucketResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.SignedUrlListDataTransferFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.CloneControlledGcpBigQueryDatasetResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.flexibleresource.CloneControlledFlexibleResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import com.google.cloud.Policy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;

/** CRUD methods for controlled objects. */
@Component
public class ControlledResourceService {
  private static final Logger logger = LoggerFactory.getLogger(ControlledResourceService.class);
  // These are chosen to retry a maximum wait time, so we return under a 30 second
  // network timeout.
  private static final int RESOURCE_ROW_WAIT_SECONDS = 1;
  private static final Duration RESOURCE_ROW_MAX_WAIT_TIME = Duration.ofSeconds(28);
  private static final Supplier<InternalLogicException> BAD_STATE =
      () -> new InternalLogicException("Invalid cloud context");

  private final JobService jobService;
  private final ResourceDao resourceDao;
  private final ApplicationDao applicationDao;
  private final SamService samService;
  private final GcpCloudContextService gcpCloudContextService;
  private final FeatureConfiguration features;
  private final TpsApiDispatch tpsApiDispatch;
  private final GrantService grantService;

  @Autowired
  public ControlledResourceService(
      JobService jobService,
      ResourceDao resourceDao,
      ApplicationDao applicationDao,
      SamService samService,
      GcpCloudContextService gcpCloudContextService,
      FeatureConfiguration features,
      TpsApiDispatch tpsApiDispatch,
      GrantService grantService) {
    this.jobService = jobService;
    this.resourceDao = resourceDao;
    this.applicationDao = applicationDao;
    this.samService = samService;
    this.gcpCloudContextService = gcpCloudContextService;
    this.features = features;
    this.tpsApiDispatch = tpsApiDispatch;
    this.grantService = grantService;
  }

  public <T> ControlledResource createControlledResourceSync(
      ControlledResource resource,
      ControlledResourceIamRole privateResourceIamRole,
      AuthenticatedUserRequest userRequest,
      T creationParameters) {
    JobBuilder jobBuilder =
        commonCreationJobBuilder(resource, privateResourceIamRole, userRequest)
            .addParameter(ControlledResourceKeys.CREATION_PARAMETERS, creationParameters);
    return jobBuilder.submitAndWait(ControlledResource.class);
  }

  /** Simpler interface for synchronous controlled resource creation */
  private JobBuilder commonCreationJobBuilder(
      ControlledResource resource,
      ControlledResourceIamRole privateResourceIamRole,
      AuthenticatedUserRequest userRequest) {
    return commonCreationJobBuilder(resource, privateResourceIamRole, null, null, userRequest);
  }

  /** Create a JobBuilder for creating controlled resources with the common parameters populated. */
  private JobBuilder commonCreationJobBuilder(
      ControlledResource resource,
      @Nullable ControlledResourceIamRole privateResourceIamRole,
      @Nullable ApiJobControl jobControl,
      @Nullable String resultPath,
      AuthenticatedUserRequest userRequest) {

    final String jobDescription =
        String.format(
            "Create controlled resource %s; id %s; name %s",
            resource.getResourceType(), resource.getResourceId(), resource.getName());

    if (features.isTpsEnabled()) {
      ResourceValidationUtils.validateRegionAgainstPolicy(
          tpsApiDispatch,
          resource.getWorkspaceId(),
          resource.getRegion(),
          resource.getResourceType().getCloudPlatform());
    }

    return jobService
        .newJob()
        .description(jobDescription)
        .jobId(Optional.ofNullable(jobControl).map(ApiJobControl::getId).orElse(null))
        .flightClass(CreateControlledResourceFlight.class)
        .resource(resource)
        .userRequest(userRequest)
        .operationType(OperationType.CREATE)
        .workspaceId(resource.getWorkspaceId().toString())
        .resourceName(resource.getName())
        .resourceType(resource.getResourceType())
        .stewardshipType(resource.getStewardshipType())
        .addParameter(ControlledResourceKeys.PRIVATE_RESOURCE_IAM_ROLE, privateResourceIamRole)
        .addParameter(JobMapKeys.RESULT_PATH.getKeyName(), resultPath)
        .addParameter(ResourceKeys.RESOURCE_STATE_RULE, features.getStateRule());
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
  public @Nullable String getAssociatedApp(
      ManagedByType managedBy, AuthenticatedUserRequest userRequest) {
    if (managedBy != ManagedByType.MANAGED_BY_APPLICATION) {
      return null;
    }

    WsmApplication application =
        applicationDao.getApplicationByEmail(
            samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest));
    return application.getApplicationId();
  }

  public ControlledResource getControlledResource(UUID workspaceUuid, UUID resourceId) {
    return resourceDao.getResource(workspaceUuid, resourceId).castToControlledResource();
  }

  /** Synchronously delete a controlled resource. */
  public void deleteControlledResourceSync(
      UUID workspaceUuid, UUID resourceId, AuthenticatedUserRequest userRequest) {
    JobBuilder deleteJob =
        commonDeletionJobBuilder(
            UUID.randomUUID().toString(), workspaceUuid, resourceId, null, userRequest);
    // Delete flight does not produce a result, so the resultClass parameter here is never used.
    deleteJob.submitAndWait(Void.class);
  }

  /**
   * Asynchronously delete a controlled resource. Returns the ID of the flight running the delete
   * job.
   */
  public String deleteControlledResourceAsync(
      ApiJobControl jobControl,
      UUID workspaceUuid,
      UUID resourceId,
      String resultPath,
      AuthenticatedUserRequest userRequest) {

    JobBuilder deleteJob =
        commonDeletionJobBuilder(
            jobControl.getId(), workspaceUuid, resourceId, resultPath, userRequest);
    return deleteJob.submit();
  }

  /**
   * Creates and returns a JobBuilder object for deleting a controlled resource. Depending on the
   * type of resource being deleted, this job may need to run asynchronously.
   */
  private JobBuilder commonDeletionJobBuilder(
      String jobId,
      UUID workspaceUuid,
      UUID resourceId,
      String resultPath,
      AuthenticatedUserRequest userRequest) {
    WsmResource resource = resourceDao.getResource(workspaceUuid, resourceId);
    final String jobDescription = "Delete controlled resource; id: " + resourceId;

    List<WsmResource> resourceToDelete = new ArrayList<>();
    resourceToDelete.add(resource);
    return jobService
        .newJob()
        .description(jobDescription)
        .jobId(jobId)
        .flightClass(DeleteControlledResourcesFlight.class)
        .userRequest(userRequest)
        .workspaceId(workspaceUuid.toString())
        .operationType(OperationType.DELETE)
        // resourceType, resourceName, stewardshipType are set for flight job filtering.
        .resourceType(resource.getResourceType())
        .resourceName(resource.getName())
        .stewardshipType(resource.getStewardshipType())
        .workspaceId(workspaceUuid.toString())
        .addParameter(JobMapKeys.RESULT_PATH.getKeyName(), resultPath)
        .addParameter(ControlledResourceKeys.CONTROLLED_RESOURCES_TO_DELETE, resourceToDelete);
  }

  /**
   * For async resource creation, we do not want to return to the caller until the resource row is
   * in the database and, thus, visible to enumeration. This method waits for either the row to show
   * up (the expected success case) or the job to complete (the expected error case). If one of
   * those doesn't happen in the retry window, we throw SERVICE_UNAVAILABLE. The theory is for it
   * not to complete, either WSM is so busy that it cannot schedule the flight or something bad has
   * happened. Either way, SERVICE_UNAVAILABLE seems like a reasonable response.
   *
   * <p>There is no race condition between the two checks. For either termination test, we will make
   * the async return to the client. That path returns the current job state. If the job is
   * complete, the client calls the result endpoint and gets the full result.
   *
   * @param workspaceUuid workspace of the resource create
   * @param resourceId id of resource being created
   * @param jobId id of the create flight.
   */
  private void waitForResourceOrJob(UUID workspaceUuid, UUID resourceId, String jobId) {
    Instant exitTime = Instant.now().plus(RESOURCE_ROW_MAX_WAIT_TIME);
    try {
      while (Instant.now().isBefore(exitTime)) {
        if (resourceDao.resourceExists(workspaceUuid, resourceId)) {
          return;
        }
        FlightState flightState = jobService.getStairway().getFlightState(jobId);
        if (flightState.getCompleted().isPresent()) {
          return;
        }
        TimeUnit.SECONDS.sleep(RESOURCE_ROW_WAIT_SECONDS);
      }
    } catch (InterruptedException e) {
      // fall through to throw
    }

    throw new ServiceUnavailableException("Failed to make prompt progress on resource");
  }

  // GCP

  /**
   * Clone a GCS Bucket to another workspace.
   *
   * @param sourceWorkspaceId - workspace ID fo source bucket
   * @param sourceResourceId - resource ID of source bucket
   * @param destinationWorkspaceId - workspace ID to clone into
   * @param jobControl - job service control structure
   * @param userRequest - incoming request
   * @param destinationResourceName - override value for resource name. Re-uses previous name if
   *     null
   * @param destinationDescription - override value for resource description. Re-uses previous value
   *     if null
   * @param destinationBucketName - GCS bucket name for cloned bucket. If null, a random name will
   *     be generated
   * @param destinationLocation - location string for the destination bucket. If null, the source
   *     bucket's location will be used.
   * @param cloningInstructionsOverride - cloning instructions for this operation. If null, the
   *     source bucket's cloning instructions will be honored.
   * @return - Job ID of submitted flight
   */
  public String cloneGcsBucket(
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      ApiJobControl jobControl,
      AuthenticatedUserRequest userRequest,
      @Nullable String destinationResourceName,
      @Nullable String destinationDescription,
      @Nullable String destinationBucketName,
      @Nullable String destinationLocation,
      @Nullable ApiCloningInstructionsEnum cloningInstructionsOverride) {
    final ControlledResource sourceBucketResource =
        getControlledResource(sourceWorkspaceId, sourceResourceId);

    // Write access to the target workspace will be established in the create flight
    final String jobDescription =
        String.format(
            "Clone controlled resource %s; id %s; name %s",
            sourceBucketResource.getResourceType(),
            sourceBucketResource.getResourceId(),
            sourceBucketResource.getName());

    // If TPS is enabled, then we want to merge policies when cloning a bucket
    boolean mergePolicies = features.isTpsEnabled();

    final JobBuilder jobBuilder =
        jobService
            .newJob()
            .description(jobDescription)
            .jobId(jobControl.getId())
            .flightClass(CloneControlledGcsBucketResourceFlight.class)
            .resource(sourceBucketResource)
            .userRequest(userRequest)
            .workspaceId(sourceWorkspaceId.toString())
            .operationType(OperationType.CLONE)
            .addParameter(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, destinationWorkspaceId)
            .addParameter(ControlledResourceKeys.DESTINATION_RESOURCE_ID, destinationResourceId)
            .addParameter(ResourceKeys.RESOURCE_NAME, destinationResourceName)
            .addParameter(ResourceKeys.RESOURCE_DESCRIPTION, destinationDescription)
            .addParameter(ControlledResourceKeys.DESTINATION_BUCKET_NAME, destinationBucketName)
            .addParameter(ControlledResourceKeys.LOCATION, destinationLocation)
            .addParameter(WorkspaceFlightMapKeys.MERGE_POLICIES, mergePolicies)
            .addParameter(
                ResourceKeys.CLONING_INSTRUCTIONS,
                Optional.ofNullable(cloningInstructionsOverride)
                    .map(CloningInstructions::fromApiModel)
                    .orElse(sourceBucketResource.getCloningInstructions()));
    return jobBuilder.submit();
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
   *     null.
   * @param destinationDatasetName - name for new resource. Can equal source name. If null, a random
   *     name will be generated
   * @param destinationLocation - location override. Uses source location if null
   * @param cloningInstructionsOverride - Cloning instructions for this clone operation, overriding
   *     any existing instructions. Existing instructions are used if null.
   */
  public String cloneBigQueryDataset(
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      ApiJobControl jobControl,
      AuthenticatedUserRequest userRequest,
      @Nullable String destinationResourceName,
      @Nullable String destinationDescription,
      @Nullable String destinationDatasetName,
      @Nullable String destinationLocation,
      @Nullable ApiCloningInstructionsEnum cloningInstructionsOverride) {
    final ControlledResource sourceDatasetResource =
        getControlledResource(sourceWorkspaceId, sourceResourceId);
    // Authorization check is handled as the first flight step rather than before the flight, as
    // this flight is re-used for cloneWorkspace.

    // Write access to the target workspace will be established in the create flight
    final String jobDescription =
        String.format(
            "Clone controlled resource %s; id %s; name %s",
            sourceDatasetResource.getResourceType(),
            sourceDatasetResource.getResourceId(),
            sourceDatasetResource.getName());

    // If TPS is enabled, then we want to merge policies when cloning a bucket
    boolean mergePolicies = features.isTpsEnabled();

    final JobBuilder jobBuilder =
        jobService
            .newJob()
            .description(jobDescription)
            .jobId(jobControl.getId())
            .flightClass(CloneControlledGcpBigQueryDatasetResourceFlight.class)
            .resource(sourceDatasetResource)
            .userRequest(userRequest)
            .operationType(OperationType.CLONE)
            // TODO: fix resource name key for this case
            .resourceType(sourceDatasetResource.getResourceType())
            .stewardshipType(sourceDatasetResource.getStewardshipType())
            .workspaceId(sourceWorkspaceId.toString())
            .addParameter(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, destinationWorkspaceId)
            .addParameter(ControlledResourceKeys.DESTINATION_RESOURCE_ID, destinationResourceId)
            .addParameter(ResourceKeys.RESOURCE_NAME, destinationResourceName)
            .addParameter(ResourceKeys.RESOURCE_DESCRIPTION, destinationDescription)
            .addParameter(ControlledResourceKeys.LOCATION, destinationLocation)
            .addParameter(ControlledResourceKeys.DESTINATION_DATASET_NAME, destinationDatasetName)
            .addParameter(WorkspaceFlightMapKeys.MERGE_POLICIES, mergePolicies)
            .addParameter(
                ResourceKeys.CLONING_INSTRUCTIONS,
                // compute effective cloning instructions
                Optional.ofNullable(cloningInstructionsOverride)
                    .map(CloningInstructions::fromApiModel)
                    .orElse(sourceDatasetResource.getCloningInstructions()));
    return jobBuilder.submit();
  }

  /** Starts a create controlled AI Notebook instance resource job, returning the job id. */
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
                    resource.getProjectId(), userRequest.getRequiredToken()),
            "enablePet");
    jobBuilder.addParameter(ControlledResourceKeys.CREATE_NOTEBOOK_PARAMETERS, creationParameters);
    jobBuilder.addParameter(ControlledResourceKeys.NOTEBOOK_PET_SERVICE_ACCOUNT, petSaEmail);
    String jobId = jobBuilder.submit();
    waitForResourceOrJob(resource.getWorkspaceId(), resource.getResourceId(), jobId);
    return jobId;
  }

  public Policy configureGcpPolicyForResource(
      ControlledResource resource,
      GcpCloudContext cloudContext,
      Policy currentPolicy,
      AuthenticatedUserRequest userRequest)
      throws InterruptedException {

    GcpPolicyBuilder gcpPolicyBuilder =
        new GcpPolicyBuilder(resource, cloudContext.getGcpProjectId(), currentPolicy);

    List<SyncMapping> syncMappings = resource.getCategory().getSyncMappings();
    for (SyncMapping syncMapping : syncMappings) {
      String policyGroup = null;
      switch (syncMapping.getRoleSource()) {
        case RESOURCE:
          policyGroup =
              samService.syncResourcePolicy(
                  resource, syncMapping.getResourceRole().orElseThrow(BAD_STATE), userRequest);
          break;

        case WORKSPACE:
          switch (syncMapping.getWorkspaceRole().orElseThrow(BAD_STATE)) {
            case OWNER -> policyGroup = cloudContext.getSamPolicyOwner();
            case WRITER -> policyGroup = cloudContext.getSamPolicyWriter();
            case READER -> policyGroup = cloudContext.getSamPolicyReader();
            case APPLICATION -> policyGroup = cloudContext.getSamPolicyApplication();
            default -> {
            }
          }
          break;
      }
      if (policyGroup == null) {
        throw new InternalLogicException("Policy group not set");
      }

      gcpPolicyBuilder.addResourceBinding(
          syncMapping.getTargetRole(), GcpUtils.toGroupMember(policyGroup));
    }

    if (features.isTemporaryGrantEnabled()) {
      // Get the user emails we are granting
      String userEmail = samService.getUserEmailFromSam(userRequest);
      String userMember =
          (grantService.isUserGrantAllowed(userEmail)) ? GcpUtils.toUserMember(userEmail) : null;
      String petMember =
          GcpUtils.toSaMember(
              samService.getOrCreatePetSaEmail(
                  gcpCloudContextService.getRequiredGcpProject(resource.getWorkspaceId()),
                  userRequest.getRequiredToken()));

      // NOTE: We always set the role to EDITOR and that is currently always the right
      // role from the sync mappings. If we change the mappings, we may need to change
      // this code. Since this is a temporary measure, I don't think it is worth
      // restructuring at this time.
      gcpPolicyBuilder.addResourceBinding(ControlledResourceIamRole.EDITOR, petMember);
      if (userMember != null) {
        gcpPolicyBuilder.addResourceBinding(ControlledResourceIamRole.EDITOR, userMember);
      }

      // Store the temporary grant - it will be revoked in the background
      grantService.recordResourceGrant(
          resource.getWorkspaceId(),
          userMember,
          petMember,
          gcpPolicyBuilder.getCustomRole(ControlledResourceIamRole.EDITOR),
          resource.getResourceId());
    }

    return gcpPolicyBuilder.build();
  }

  // Azure

  public String createAzureVm(
      ControlledAzureVmResource resource,
      ApiAzureVmCreationParameters creationParameters,
      ControlledResourceIamRole privateResourceIamRole,
      ApiJobControl jobControl,
      String resultPath,
      AuthenticatedUserRequest userRequest) {
    JobBuilder jobBuilder =
        commonCreationJobBuilder(
                resource, privateResourceIamRole, jobControl, resultPath, userRequest)
            .addParameter(ControlledResourceKeys.CREATION_PARAMETERS, creationParameters);

    String jobId = jobBuilder.submit();
    waitForResourceOrJob(resource.getWorkspaceId(), resource.getResourceId(), jobId);
    return jobId;
  }

  public String cloneAzureContainer(
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      ApiJobControl jobControl,
      AuthenticatedUserRequest userRequest,
      @Nullable String destinationResourceName,
      @Nullable String destinationDescription,
      @Nullable String destinationContainerName,
      @Nullable ApiCloningInstructionsEnum cloningInstructionsOverride,
      @Nullable List<String> prefixesToClone) {
    final ControlledResource sourceContainer =
        getControlledResource(sourceWorkspaceId, sourceResourceId);

    // Write access to the target workspace will be established in the create flight
    final String jobDescription =
        String.format(
            "Clone controlled resource %s; id %s; name %s",
            sourceContainer.getResourceType(),
            sourceContainer.getResourceId(),
            sourceContainer.getName());

    // If TPS is enabled, then we want to merge policies when cloning a container
    boolean mergePolicies = features.isTpsEnabled();

    final JobBuilder jobBuilder =
        jobService
            .newJob()
            .description(jobDescription)
            .jobId(jobControl.getId())
            .flightClass(CloneControlledAzureStorageContainerResourceFlight.class)
            .resource(sourceContainer)
            .userRequest(userRequest)
            .workspaceId(sourceWorkspaceId.toString())
            .operationType(OperationType.CLONE)
            .addParameter(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, destinationWorkspaceId)
            .addParameter(ControlledResourceKeys.DESTINATION_RESOURCE_ID, destinationResourceId)
            .addParameter(ResourceKeys.RESOURCE_NAME, destinationResourceName)
            .addParameter(ResourceKeys.RESOURCE_DESCRIPTION, destinationDescription)
            .addParameter(
                ControlledResourceKeys.DESTINATION_CONTAINER_NAME, destinationContainerName)
            .addParameter(WorkspaceFlightMapKeys.MERGE_POLICIES, mergePolicies)
            .addParameter(
                ResourceKeys.CLONING_INSTRUCTIONS,
                Optional.ofNullable(cloningInstructionsOverride)
                    .map(CloningInstructions::fromApiModel)
                    .orElse(sourceContainer.getCloningInstructions()))
            .addParameter(ControlledResourceKeys.PREFIXES_TO_CLONE, prefixesToClone);
    return jobBuilder.submit();
  }

  // AWS

  /**
   * Starts a create controlled AWS SageMaker Notebook instance resource job, returning the job id.
   *
   * <p>Data fields are required from AWS Environment, as well as the landing zone specific to the
   * resource's region. Hence, add the entire AWS environment to the job
   */
  public String createAwsSageMakerNotebookInstance(
      ControlledAwsSageMakerNotebookResource resource,
      ApiAwsSageMakerNotebookCreationParameters creationParameters,
      Environment environment,
      @Nullable ControlledResourceIamRole privateResourceIamRole,
      @Nullable ApiJobControl jobControl,
      String resultPath,
      AuthenticatedUserRequest userRequest) {
    // Special check for notebooks: READER is not a useful role
    if (privateResourceIamRole == ControlledResourceIamRole.READER) {
      throw new BadRequestException(
          "A private, controlled Notebook instance must have the writer or editor role or else it is not useful.");
    }

    LandingZone landingZone =
        environment.getLandingZone(Region.of(resource.getRegion())).orElseThrow();

    JobBuilder jobBuilder =
        commonCreationJobBuilder(
            resource, privateResourceIamRole, jobControl, resultPath, userRequest);
    jobBuilder.addParameter(ControlledResourceKeys.CREATE_NOTEBOOK_PARAMETERS, creationParameters);
    jobBuilder.addParameter(
        ControlledResourceKeys.AWS_ENVIRONMENT_NOTEBOOK_ROLE_ARN,
        environment.getNotebookRoleArn().toString());
    jobBuilder.addParameter(
        ControlledResourceKeys.AWS_LANDING_ZONE_KMS_KEY_ARN,
        landingZone.getKmsKey().arn().toString());

    // Configurations expected to be ordered: version-ASC, pick latest version
    landingZone.getNotebookLifecycleConfigurations().stream()
        .reduce((first, second) -> second)
        .ifPresent(
            lifecycleConfiguration ->
                jobBuilder.addParameter(
                    ControlledResourceKeys.AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN,
                    lifecycleConfiguration.arn().toString()));

    String jobId = jobBuilder.submit();
    waitForResourceOrJob(resource.getWorkspaceId(), resource.getResourceId(), jobId);
    return jobId;
  }

  // Flexible

  public ControlledFlexibleResource cloneFlexResource(
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      AuthenticatedUserRequest userRequest,
      @Nullable String destinationResourceName,
      @Nullable String destinationDescription,
      @Nullable ApiCloningInstructionsEnum cloningInstructionsOverride) {
    final ControlledResource sourceFlexResource =
        getControlledResource(sourceWorkspaceId, sourceResourceId);

    final String jobDescription =
        String.format(
            "Clone controlled flex resource id %s; name %s",
            sourceResourceId, sourceFlexResource.getName());

    final CloningInstructions effectiveCloningInstructions =
        Optional.ofNullable(cloningInstructionsOverride)
            .map(CloningInstructions::fromApiModel)
            .orElse(sourceFlexResource.getCloningInstructions());

    // If TPS is enabled, then we want to merge policies when cloning a flex resource.
    boolean mergePolicies = features.isTpsEnabled();

    final JobBuilder jobBuilder =
        jobService
            .newJob()
            .description(jobDescription)
            .flightClass(CloneControlledFlexibleResourceFlight.class)
            .resourceType(WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE)
            .resource(sourceFlexResource)
            .workspaceId(destinationWorkspaceId.toString())
            .operationType(OperationType.CLONE)
            .addParameter(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, destinationWorkspaceId)
            .addParameter(ControlledResourceKeys.DESTINATION_RESOURCE_ID, destinationResourceId)
            .addParameter(ResourceKeys.RESOURCE_NAME, destinationResourceName)
            .addParameter(ResourceKeys.RESOURCE_DESCRIPTION, destinationDescription)
            .addParameter(ResourceKeys.CLONING_INSTRUCTIONS, effectiveCloningInstructions)
            .addParameter(ResourceKeys.RESOURCE, sourceFlexResource)
            .addParameter(WorkspaceFlightMapKeys.MERGE_POLICIES, mergePolicies)
            .addParameter(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    return jobBuilder.submitAndWait(ControlledFlexibleResource.class);
  }

  public String transferUrlListToGcsBucket(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      ControlledGcsBucketResource destinationBucket,
      String signedUrlList) {
    JobBuilder jobBuilder =
        jobService
            .newJob()
            .description("Transfer signed url lists to a gcs bucket")
            .flightClass(SignedUrlListDataTransferFlight.class)
            .resourceType(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET)
            .resource(destinationBucket)
            .workspaceId(workspaceId.toString())
            .operationType(OperationType.DATA_TRANSFER)
            .addParameter(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, workspaceId)
            .addParameter(
                ControlledResourceKeys.DESTINATION_BUCKET_NAME_FOR_SIGNED_URL_LIST,
                destinationBucket.getBucketName())
            .addParameter(ControlledResourceKeys.SIGNED_URL_LIST, signedUrlList)
            .addParameter(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);

    return jobBuilder.submit();
  }
}
