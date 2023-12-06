package bio.terra.workspace.app.controller;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.LandingZone;
import bio.terra.common.exception.ValidationException;
import bio.terra.common.iam.SamUser;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.ControlledAwsResourceApi;
import bio.terra.workspace.generated.model.ApiAwsCredential;
import bio.terra.workspace.generated.model.ApiAwsCredentialAccessScope;
import bio.terra.workspace.generated.model.ApiAwsResourceCloudName;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderResource;
import bio.terra.workspace.generated.model.ApiAwsSageMakerNotebookResource;
import bio.terra.workspace.generated.model.ApiCreateControlledAwsS3StorageFolderRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAwsSageMakerNotebookRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAwsSageMakerNotebookResult;
import bio.terra.workspace.generated.model.ApiCreatedControlledAwsS3StorageFolder;
import bio.terra.workspace.generated.model.ApiDeleteControlledAwsResourceRequestBody;
import bio.terra.workspace.generated.model.ApiDeleteControlledAwsResourceResult;
import bio.terra.workspace.generated.model.ApiGenerateAwsResourceCloudNameRequestBody;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiUpdateControlledAwsS3StorageFolderRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledAwsSageMakerNotebookRequestBody;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.AwsResourceValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.aws.AwsResourceConstants;
import bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder.ControlledAwsS3StorageFolderHandler;
import bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder.ControlledAwsS3StorageFolderResource;
import bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook.ControlledAwsSageMakerNotebookHandler;
import bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook.ControlledAwsSageMakerNotebookResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CommonUpdateParameters;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants;
import com.google.common.base.Strings;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sagemaker.model.InstanceType;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.Tag;

@Controller
public class ControlledAwsResourceApiController extends ControlledResourceControllerBase
    implements ControlledAwsResourceApi {

  private final Logger logger = LoggerFactory.getLogger(ControlledAwsResourceApiController.class);

  private final WsmResourceService wsmResourceService;
  private final AwsCloudContextService awsCloudContextService;

  @Autowired
  public ControlledAwsResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      FeatureConfiguration featureConfiguration,
      FeatureService featureService,
      JobService jobService,
      JobApiUtils jobApiUtils,
      ControlledResourceService controlledResourceService,
      ControlledResourceMetadataManager controlledResourceMetadataManager,
      WorkspaceService workspaceService,
      WsmResourceService wsmResourceService,
      AwsCloudContextService awsCloudContextService) {
    super(
        authenticatedUserRequestFactory,
        request,
        samService,
        featureConfiguration,
        featureService,
        jobService,
        jobApiUtils,
        controlledResourceService,
        controlledResourceMetadataManager,
        workspaceService);
    this.wsmResourceService = wsmResourceService;
    this.awsCloudContextService = awsCloudContextService;
  }

  private String getSamAction(ApiAwsCredentialAccessScope accessScope) {
    return (accessScope == ApiAwsCredentialAccessScope.WRITE_READ)
        ? SamConstants.SamControlledResourceActions.WRITE_ACTION
        : SamConstants.SamControlledResourceActions.READ_ACTION;
  }

  private String getResourceRegion(Workspace workspace, String requestedRegion) {
    return Strings.isNullOrEmpty(requestedRegion)
        ? workspace
            .getProperties()
            .getOrDefault(
                WorkspaceConstants.Properties.DEFAULT_RESOURCE_LOCATION,
                AwsResourceConstants.DEFAULT_REGION)
        : requestedRegion;
  }

  private ApiDeleteControlledAwsResourceResult getAwsResourceDeleteResult(String jobId) {
    JobApiUtils.AsyncJobResult<Void> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, Void.class);
    return new ApiDeleteControlledAwsResourceResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport());
  }

  private ResponseEntity<ApiDeleteControlledAwsResourceResult> deleteAwsResource(
      UUID workspaceUuid,
      UUID resourceUuid,
      WsmResourceType wsmResourceType,
      ApiDeleteControlledAwsResourceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResource awsResource =
        controlledResourceMetadataManager.validateControlledResourceAndAction(
            userRequest, workspaceUuid, resourceUuid, SamControlledResourceActions.DELETE_ACTION);
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.AWS);

    // sanity check that resource is of expected type
    if (awsResource.getResourceType() != wsmResourceType) {
      throw new ValidationException(
          String.format(
              "Delete requested on resource: %s, expected type: %s, actual type: %s",
              awsResource.getResourceId(), wsmResourceType, awsResource.getResourceType()));
    }

    ApiJobControl jobControl = body.getJobControl();

    logger.info(
        "deleteAwsResource workspaceUuid: {}, resourceUuid: {}",
        workspaceUuid.toString(),
        resourceUuid.toString());

    String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl.getId(),
            workspaceUuid,
            resourceUuid,
            /* forceDelete= */ false,
            getAsyncResultEndpoint(jobControl.getId(), "delete-result"),
            userRequest);

    ApiDeleteControlledAwsResourceResult result = getAwsResourceDeleteResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  private ResponseEntity<ApiDeleteControlledAwsResourceResult> getDeleteAwsResourceResult(
      UUID workspaceUuid, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiDeleteControlledAwsResourceResult result = getAwsResourceDeleteResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  private <T extends ControlledResource> ResponseEntity<ApiAwsCredential> getAwsResourceCredential(
      UUID workspaceUuid,
      ApiAwsCredentialAccessScope accessScope,
      Integer durationSeconds,
      T awsResource,
      String userEmail) {
    AwsResourceValidationUtils.validateAwsCredentialDurationSecond(durationSeconds);

    AwsCloudContext cloudContext = awsCloudContextService.getRequiredAwsCloudContext(workspaceUuid);
    SamUser user = getSamUser();
    Collection<Tag> tags = new HashSet<>();
    AwsUtils.appendUserTags(tags, user);
    AwsUtils.appendPrincipalTags(tags, cloudContext, awsResource);
    AwsUtils.appendRoleTags(tags, accessScope);
    Credentials awsCredentials =
        AwsUtils.getAssumeUserRoleCredentials(
            awsCloudContextService.getRequiredAuthentication(),
            awsCloudContextService.discoverEnvironment(userEmail),
            user,
            Duration.ofSeconds(durationSeconds),
            tags);

    // version: 1 as per
    // https://docs.aws.amazon.com/sdkref/latest/guide/feature-process-credentials.html#feature-process-credentials-output
    return new ResponseEntity<>(
        new ApiAwsCredential()
            .version(1)
            .accessKeyId(awsCredentials.accessKeyId())
            .secretAccessKey(awsCredentials.secretAccessKey())
            .sessionToken(awsCredentials.sessionToken())
            .expiration(awsCredentials.expiration().atOffset(ZoneOffset.UTC)),
        HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiAwsResourceCloudName> generateAwsS3StorageFolderCloudName(
      UUID workspaceUuid, ApiGenerateAwsResourceCloudNameRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest, workspaceUuid, SamWorkspaceAction.READ);

    String generatedCloudName =
        ControlledAwsS3StorageFolderHandler.getHandler()
            .generateCloudName(workspace.getUserFacingId(), body.getAwsResourceName());
    return new ResponseEntity<>(
        new ApiAwsResourceCloudName().awsResourceCloudName(generatedCloudName), HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCreatedControlledAwsS3StorageFolder> createAwsS3StorageFolder(
      UUID workspaceUuid, @Valid ApiCreateControlledAwsS3StorageFolderRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest,
            workspaceUuid,
            ControllerValidationUtils.samCreateAction(
                AccessScopeType.fromApi(body.getCommon().getAccessScope()),
                ManagedByType.fromApi(body.getCommon().getManagedBy())));
    workspaceService.validateWorkspaceAndContextState(workspace, CloudPlatform.AWS);

    String folderName = body.getAwsS3StorageFolder().getFolderName();
    if (StringUtils.isEmpty(folderName)) {
      folderName =
          ControlledAwsS3StorageFolderHandler.getHandler()
              .generateCloudName(workspace.getUserFacingId(), body.getCommon().getName());
    }
    AwsResourceValidationUtils.validateAwsS3StorageFolderName(folderName);

    String region =
        getResourceRegion(workspace, StringUtils.trim(body.getAwsS3StorageFolder().getRegion()));

    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            region,
            userRequest,
            WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER);

    AwsCloudContext awsCloudContext =
        awsCloudContextService.getRequiredAwsCloudContext(workspaceUuid);
    Environment environment =
        awsCloudContextService.discoverEnvironment(
            samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest));
    LandingZone landingZone =
        AwsCloudContextService.getLandingZone(environment, awsCloudContext, Region.of(region))
            .orElseThrow(
                () -> {
                  throw new ValidationException(
                      String.format("Unsupported AWS region: %s.", region));
                });

    logger.info(
        "createAwsS3StorageFolder workspace: {}, region: {}, bucketName: {}, folderName {}, cloudName {}",
        workspaceUuid.toString(),
        region,
        landingZone.getStorageBucket().name(),
        commonFields.getName(),
        folderName);

    ControlledAwsS3StorageFolderResource resource =
        ControlledAwsS3StorageFolderResource.builder()
            .common(commonFields)
            .bucketName(landingZone.getStorageBucket().name())
            .prefix(folderName)
            .build();

    ControlledAwsS3StorageFolderResource createdResource =
        controlledResourceService
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, body.getAwsS3StorageFolder())
            .castByEnum(WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER);

    return new ResponseEntity<>(
        new ApiCreatedControlledAwsS3StorageFolder()
            .resourceId(createdResource.getResourceId())
            .awsS3StorageFolder(createdResource.toApiResource()),
        HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiAwsS3StorageFolderResource> getAwsS3StorageFolder(
      UUID workspaceUuid, UUID resourceUuid) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAwsS3StorageFolderResource resource =
        controlledResourceMetadataManager
            .validateWorkspaceOrControlledResourceReadAccess(
                userRequest, workspaceUuid, resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiAwsS3StorageFolderResource> updateAwsS3StorageFolder(
      UUID workspaceUuid,
      UUID resourceUuid,
      @Valid ApiUpdateControlledAwsS3StorageFolderRequestBody body) {
    logger.info(
        "Updating AWS S3 Storage Folder resourceId {} workspaceUuid {}",
        resourceUuid,
        workspaceUuid);
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.AWS);

    ControlledAwsS3StorageFolderResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest,
                workspaceUuid,
                resourceUuid,
                SamConstants.SamControlledResourceActions.EDIT_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER);

    CommonUpdateParameters commonUpdateParameters =
        new CommonUpdateParameters().setName(body.getName()).setDescription(body.getDescription());

    wsmResourceService.updateResource(userRequest, resource, commonUpdateParameters, null);

    ControlledAwsS3StorageFolderResource updatedResource =
        controlledResourceService
            .getControlledResource(workspaceUuid, resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER);

    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiDeleteControlledAwsResourceResult> deleteAwsS3StorageFolder(
      UUID workspaceUuid,
      UUID resourceUuid,
      @Valid ApiDeleteControlledAwsResourceRequestBody body) {
    return deleteAwsResource(
        workspaceUuid, resourceUuid, WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER, body);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiDeleteControlledAwsResourceResult> getDeleteAwsS3StorageFolderResult(
      UUID workspaceUuid, String jobId) {
    return getDeleteAwsResourceResult(workspaceUuid, jobId);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiAwsCredential> getAwsS3StorageFolderCredential(
      UUID workspaceUuid,
      UUID resourceUuid,
      ApiAwsCredentialAccessScope accessScope,
      Integer durationSeconds) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAwsS3StorageFolderResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceUuid, getSamAction(accessScope))
            .castByEnum(WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER);
    return getAwsResourceCredential(
        workspaceUuid,
        accessScope,
        durationSeconds,
        resource,
        samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest));
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiAwsResourceCloudName> generateAwsSageMakerNotebookCloudName(
      UUID workspaceUuid, @Valid ApiGenerateAwsResourceCloudNameRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest, workspaceUuid, SamWorkspaceAction.READ);

    String generatedCloudName =
        ControlledAwsSageMakerNotebookHandler.getHandler()
            .generateCloudName(workspace.getUserFacingId(), body.getAwsResourceName());
    return new ResponseEntity<>(
        new ApiAwsResourceCloudName().awsResourceCloudName(generatedCloudName), HttpStatus.OK);
  }

  private ApiCreateControlledAwsSageMakerNotebookResult getAwsSageMakerNotebookCreateResult(
      String jobId) {
    JobApiUtils.AsyncJobResult<ControlledAwsSageMakerNotebookResource> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, ControlledAwsSageMakerNotebookResource.class);

    ApiAwsSageMakerNotebookResource apiResource = null;
    if (jobResult.getJobReport().getStatus().equals(ApiJobReport.StatusEnum.SUCCEEDED)) {
      apiResource = jobResult.getResult().toApiResource();
    }

    return new ApiCreateControlledAwsSageMakerNotebookResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .awsSageMakerNotebook(apiResource);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCreateControlledAwsSageMakerNotebookResult> createAwsSageMakerNotebook(
      UUID workspaceUuid, @Valid ApiCreateControlledAwsSageMakerNotebookRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest,
            workspaceUuid,
            ControllerValidationUtils.samCreateAction(
                AccessScopeType.fromApi(body.getCommon().getAccessScope()),
                ManagedByType.fromApi(body.getCommon().getManagedBy())));
    workspaceService.validateWorkspaceAndContextState(workspace, CloudPlatform.AWS);

    InstanceType instanceType =
        InstanceType.fromValue(body.getAwsSageMakerNotebook().getInstanceType());
    if (instanceType == null || instanceType == InstanceType.UNKNOWN_TO_SDK_VERSION) {
      throw new ValidationException(
          String.format("Unsupported AWS SageMaker Notebook InstanceType: %s.", instanceType));
    }
    String instanceName = body.getAwsSageMakerNotebook().getInstanceName();
    if (StringUtils.isEmpty(instanceName)) {
      instanceName =
          ControlledAwsSageMakerNotebookHandler.getHandler()
              .generateCloudName(workspace.getUserFacingId(), body.getCommon().getName());
    }
    AwsResourceValidationUtils.validateAwsSageMakerNotebookName(instanceName);

    String region =
        getResourceRegion(workspace, StringUtils.trim(body.getAwsSageMakerNotebook().getRegion()));

    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            region,
            userRequest,
            WsmResourceType.CONTROLLED_AWS_SAGEMAKER_NOTEBOOK);

    AwsCloudContext awsCloudContext =
        awsCloudContextService.getRequiredAwsCloudContext(workspaceUuid);
    Environment environment =
        awsCloudContextService.discoverEnvironment(
            samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest));
    AwsCloudContextService.getLandingZone(environment, awsCloudContext, Region.of(region))
        .orElseThrow(
            () -> {
              throw new ValidationException(String.format("Unsupported AWS region: %s.", region));
            });

    logger.info(
        "createAwsSageMakerNotebook workspace: {}, region: {}, instanceName: {}, instanceType {}, cloudName {}",
        workspaceUuid.toString(),
        region,
        commonFields.getName(),
        instanceType,
        instanceName);

    ControlledAwsSageMakerNotebookResource resource =
        ControlledAwsSageMakerNotebookResource.builder()
            .common(commonFields)
            .instanceName(instanceName)
            .instanceType(body.getAwsSageMakerNotebook().getInstanceType())
            .build();

    String jobId =
        controlledResourceService.createAwsSageMakerNotebookInstance(
            resource,
            body.getAwsSageMakerNotebook(),
            environment,
            commonFields.getIamRole(),
            body.getJobControl(),
            getAsyncResultEndpoint(body.getJobControl().getId(), "create-result"),
            userRequest);

    ApiCreateControlledAwsSageMakerNotebookResult result =
        getAwsSageMakerNotebookCreateResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode((result.getJobReport())));
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCreateControlledAwsSageMakerNotebookResult>
      getCreateAwsSageMakerNotebookResult(UUID workspaceUuid, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiCreateControlledAwsSageMakerNotebookResult result =
        getAwsSageMakerNotebookCreateResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiAwsSageMakerNotebookResource> getAwsSageMakerNotebook(
      UUID workspaceUuid, UUID resourceUuid) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAwsSageMakerNotebookResource resource =
        controlledResourceMetadataManager
            .validateWorkspaceOrControlledResourceReadAccess(
                userRequest, workspaceUuid, resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_AWS_SAGEMAKER_NOTEBOOK);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiAwsSageMakerNotebookResource> updateAwsSageMakerNotebook(
      UUID workspaceUuid,
      UUID resourceUuid,
      @Valid ApiUpdateControlledAwsSageMakerNotebookRequestBody body) {
    logger.info(
        "Updating AWS SageMaker Notebook resourceId {} workspaceUuid {}",
        resourceUuid,
        workspaceUuid);
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.AWS);

    ControlledAwsSageMakerNotebookResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest,
                workspaceUuid,
                resourceUuid,
                SamConstants.SamControlledResourceActions.EDIT_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_AWS_SAGEMAKER_NOTEBOOK);

    CommonUpdateParameters commonUpdateParameters =
        new CommonUpdateParameters().setName(body.getName()).setDescription(body.getDescription());

    wsmResourceService.updateResource(userRequest, resource, commonUpdateParameters, null);

    ControlledAwsSageMakerNotebookResource updatedResource =
        controlledResourceService
            .getControlledResource(workspaceUuid, resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_AWS_SAGEMAKER_NOTEBOOK);

    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiDeleteControlledAwsResourceResult> deleteAwsSageMakerNotebook(
      UUID workspaceUuid,
      UUID resourceUuid,
      @Valid ApiDeleteControlledAwsResourceRequestBody body) {
    return deleteAwsResource(
        workspaceUuid, resourceUuid, WsmResourceType.CONTROLLED_AWS_SAGEMAKER_NOTEBOOK, body);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiDeleteControlledAwsResourceResult> getDeleteAwsSageMakerNotebookResult(
      UUID workspaceUuid, String jobId) {
    return getDeleteAwsResourceResult(workspaceUuid, jobId);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiAwsCredential> getAwsSageMakerNotebookCredential(
      UUID workspaceUuid,
      UUID resourceUuid,
      ApiAwsCredentialAccessScope accessScope,
      Integer durationSeconds) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAwsSageMakerNotebookResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceUuid, getSamAction(accessScope))
            .castByEnum(WsmResourceType.CONTROLLED_AWS_SAGEMAKER_NOTEBOOK);
    return getAwsResourceCredential(
        workspaceUuid,
        accessScope,
        durationSeconds,
        resource,
        samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest));
  }
}
