package bio.terra.workspace.app.controller;

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
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderCloudName;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderResource;
import bio.terra.workspace.generated.model.ApiCreateControlledAwsS3StorageFolderRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledAwsS3StorageFolder;
import bio.terra.workspace.generated.model.ApiDeleteControlledAwsResourceRequestBody;
import bio.terra.workspace.generated.model.ApiDeleteControlledAwsResourceResult;
import bio.terra.workspace.generated.model.ApiGenerateAwsS3StorageFolderCloudNameRequestBody;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.AwsResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.aws.AwsResourceConstants;
import bio.terra.workspace.service.resource.controlled.cloud.aws.s3storageFolder.ControlledAwsS3StorageFolderHandler;
import bio.terra.workspace.service.resource.controlled.cloud.aws.s3storageFolder.ControlledAwsS3StorageFolderResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants;
import com.google.common.base.Strings;
import io.opencensus.contrib.spring.aop.Traced;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.Tag;

@Controller
public class ControlledAwsResourceApiController extends ControlledResourceControllerBase
    implements ControlledAwsResourceApi {

  private final Logger logger = LoggerFactory.getLogger(ControlledAwsResourceApiController.class);

  private final FeatureService featureService;
  private final WorkspaceService workspaceService;
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
      AwsCloudContextService awsCloudContextService) {
    super(
        authenticatedUserRequestFactory,
        request,
        samService,
        featureConfiguration,
        jobService,
        jobApiUtils,
        controlledResourceService,
        controlledResourceMetadataManager);
    this.featureService = featureService;
    this.workspaceService = workspaceService;
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

  @Traced
  @Override
  public ResponseEntity<ApiAwsS3StorageFolderCloudName> generateAwsS3StorageFolderCloudName(
      UUID workspaceId, ApiGenerateAwsS3StorageFolderCloudNameRequestBody body) {
    featureService.awsEnabledCheck();

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateMcWorkspaceAndAction(
        userRequest, workspaceId, SamWorkspaceAction.READ);

    String generatedCloudName =
        ControlledAwsS3StorageFolderHandler.getHandler()
            .generateCloudName(workspaceId, body.getAwsS3StorageFolderName());
    return new ResponseEntity<>(
        new ApiAwsS3StorageFolderCloudName()
            .generatedAwsS3StorageFolderCloudName(generatedCloudName),
        HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiCreatedControlledAwsS3StorageFolder> createAwsS3StorageFolder(
      UUID workspaceUuid, @Valid ApiCreateControlledAwsS3StorageFolderRequestBody body) {
    featureService.awsEnabledCheck();

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest,
            workspaceUuid,
            ControllerValidationUtils.samCreateAction(
                AccessScopeType.fromApi(body.getCommon().getAccessScope()),
                ManagedByType.fromApi(body.getCommon().getManagedBy())));

    String folderName = body.getAwsS3StorageFolder().getFolderName();
    if (StringUtils.isEmpty(folderName)) {
      folderName =
          ControlledAwsS3StorageFolderHandler.getHandler()
              .generateCloudName(workspaceUuid, body.getCommon().getName());
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

    LandingZone landingZone =
        awsCloudContextService
            .getLandingZone(awsCloudContext, Region.of(region))
            .orElseThrow(
                () -> {
                  throw new ValidationException(
                      String.format("Unsupported AWS region: '%s'.", region));
                });

    logger.info(
        "createAwsS3StorageFolder workspace: {}, bucketName: {}, prefix {}, region: {}",
        workspaceUuid.toString(),
        landingZone.getStorageBucket().name(),
        commonFields.getName(),
        region);

    ControlledAwsS3StorageFolderResource resource =
        ControlledAwsS3StorageFolderResource.builder()
            .common(commonFields)
            .bucketName(landingZone.getStorageBucket().name())
            .prefix(folderName)
            .build();

    ControlledAwsS3StorageFolderResource createdBucket =
        controlledResourceService
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, body.getAwsS3StorageFolder())
            .castByEnum(WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER);

    return new ResponseEntity<>(
        new ApiCreatedControlledAwsS3StorageFolder()
            .resourceId(createdBucket.getResourceId())
            .awsS3StorageFolder(createdBucket.toApiResource()),
        HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiAwsS3StorageFolderResource> getAwsS3StorageFolder(
      UUID workspaceUuid, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAwsS3StorageFolderResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest,
                workspaceUuid,
                resourceId,
                SamConstants.SamControlledResourceActions.READ_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiDeleteControlledAwsResourceResult> deleteAwsS3StorageFolder(
      UUID workspaceUuid, UUID resourceId, @Valid ApiDeleteControlledAwsResourceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest,
        workspaceUuid,
        resourceId,
        SamConstants.SamControlledResourceActions.DELETE_ACTION);
    ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "deleteAwsS3StorageFolder workspace: {}, resourceId: {}",
        workspaceUuid.toString(),
        resourceId.toString());
    String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl,
            workspaceUuid,
            resourceId,
            getAsyncResultEndpoint(jobControl.getId(), "delete-result"),
            userRequest);
    ApiDeleteControlledAwsResourceResult result = fetchStorageFolderDeleteResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Traced
  @Override
  public ResponseEntity<ApiDeleteControlledAwsResourceResult> getDeleteAwsS3StorageFolderResult(
      UUID workspaceUuid, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiDeleteControlledAwsResourceResult result = fetchStorageFolderDeleteResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  private ApiDeleteControlledAwsResourceResult fetchStorageFolderDeleteResult(String jobId) {
    JobApiUtils.AsyncJobResult<Void> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, Void.class);
    return new ApiDeleteControlledAwsResourceResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport());
  }

  @Traced
  @Override
  public ResponseEntity<ApiAwsCredential> getAwsS3StorageFolderCredential(
      UUID workspaceUuid,
      UUID resourceId,
      ApiAwsCredentialAccessScope accessScope,
      Integer duration) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager
        .validateControlledResourceAndAction(
            userRequest, workspaceUuid, resourceId, getSamAction(accessScope))
        .castByEnum(WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER);

    AwsResourceValidationUtils.validateAwsCredentialDurationSecond(duration);

    AwsCloudContext cloudContext = awsCloudContextService.getRequiredAwsCloudContext(workspaceUuid);
    ControlledAwsS3StorageFolderResource awsS3StorageFolderResource =
        controlledResourceService
            .getControlledResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER);

    SamUser user = getSamUser();
    Collection<Tag> tags = new HashSet<>();
    AwsUtils.appendUserTags(tags, user);
    AwsUtils.appendPrincipalTags(tags, cloudContext, awsS3StorageFolderResource);
    AwsUtils.appendRoleTags(tags, accessScope);

    Credentials awsCredentials =
        AwsUtils.getAssumeUserRoleCredentials(
            awsCloudContextService.getRequiredAuthentication(),
            awsCloudContextService.discoverEnvironment(),
            user,
            Duration.ofSeconds(duration),
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
}
