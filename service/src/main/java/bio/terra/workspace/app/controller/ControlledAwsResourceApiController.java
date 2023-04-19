package bio.terra.workspace.app.controller;

import bio.terra.aws.resource.discovery.LandingZone;
import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.ControlledAwsResourceApi;
import bio.terra.workspace.generated.model.ApiAwsCredential;
import bio.terra.workspace.generated.model.ApiAwsCredentialAccessScope;
import bio.terra.workspace.generated.model.ApiAwsStorageFolderCreationParameters;
import bio.terra.workspace.generated.model.ApiAwsStorageFolderResource;
import bio.terra.workspace.generated.model.ApiCreateControlledAwsStorageFolderRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledAwsStorageFolder;
import bio.terra.workspace.generated.model.ApiDeleteControlledAwsResourceRequestBody;
import bio.terra.workspace.generated.model.ApiDeleteControlledAwsResourceResult;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.AwsResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.aws.storageFolder.ControlledAwsStorageFolderResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.model.Credentials;

@Controller
public class ControlledAwsResourceApiController extends ControlledResourceControllerBase
    implements ControlledAwsResourceApi {

  private final Logger logger = LoggerFactory.getLogger(ControlledAwsResourceApiController.class);

  private final WorkspaceService workspaceService;
  private final AwsCloudContextService awsCloudContextService;

  @Autowired
  public ControlledAwsResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      FeatureConfiguration features,
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
        features,
        jobService,
        jobApiUtils,
        controlledResourceService,
        controlledResourceMetadataManager);
    this.workspaceService = workspaceService;
    this.awsCloudContextService = awsCloudContextService;
  }

  private String getSamAction(ApiAwsCredentialAccessScope accessScope) {
    return (accessScope == ApiAwsCredentialAccessScope.WRITE_READ)
        ? SamConstants.SamControlledResourceActions.WRITE_ACTION
        : SamConstants.SamControlledResourceActions.READ_ACTION;
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAwsStorageFolder> createAwsStorageFolder(
      UUID workspaceUuid, @Valid ApiCreateControlledAwsStorageFolderRequestBody body) {
    features.awsEnabledCheck();

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            body.getAwsStorageFolder().getRegion(),
            userRequest,
            WsmResourceType.CONTROLLED_AWS_STORAGE_FOLDER);
    workspaceService.validateMcWorkspaceAndAction(
        userRequest, workspaceUuid, ControllerValidationUtils.samCreateAction(commonFields));

    AwsCloudContext awsCloudContext =
        awsCloudContextService.getRequiredAwsCloudContext(workspaceUuid);
    ApiAwsStorageFolderCreationParameters creationParameters = body.getAwsStorageFolder();

    AwsResourceValidationUtils.validateAwsStorageFolderName(commonFields.getName());

    LandingZone landingZone =
        awsCloudContextService
            .getLandingZone(awsCloudContext, Region.of(creationParameters.getRegion()))
            .orElseThrow(
                () -> {
                  throw new ValidationException(
                      String.format(
                          "Unsupported AWS region: '%s'.", creationParameters.getRegion()));
                });

    logger.info(
        "createAwsStorageFolder workspace: {}, bucketName: {}, prefix {}, region: {}",
        workspaceUuid.toString(),
        landingZone.getStorageBucket().name(),
        commonFields.getName(),
        creationParameters.getRegion());

    ControlledAwsStorageFolderResource resource =
        ControlledAwsStorageFolderResource.builder()
            .common(commonFields)
            .bucketName(landingZone.getStorageBucket().name())
            .prefix(commonFields.getName())
            .build();

    ControlledAwsStorageFolderResource createdBucket =
        controlledResourceService
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, body.getAwsStorageFolder())
            .castByEnum(WsmResourceType.CONTROLLED_AWS_STORAGE_FOLDER);

    ApiCreatedControlledAwsStorageFolder response =
        new ApiCreatedControlledAwsStorageFolder()
            .resourceId(createdBucket.getResourceId())
            .awsStorageFolder(createdBucket.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAwsStorageFolderResource> getAwsStorageFolder(
      UUID workspaceUuid, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAwsStorageFolderResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest,
                workspaceUuid,
                resourceId,
                SamConstants.SamControlledResourceActions.READ_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_AWS_STORAGE_FOLDER);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAwsResourceResult> deleteAwsStorageFolder(
      UUID workspaceUuid, UUID resourceId, @Valid ApiDeleteControlledAwsResourceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest,
        workspaceUuid,
        resourceId,
        SamConstants.SamControlledResourceActions.DELETE_ACTION);
    ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "deleteAwsStorageFolder workspace: {}, resourceId: {}",
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

  @Override
  public ResponseEntity<ApiDeleteControlledAwsResourceResult> getDeleteAwsStorageFolderResult(
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

  @Override
  public ResponseEntity<ApiAwsCredential> getAwsStorageFolderCredential(
      UUID workspaceUuid,
      UUID resourceId,
      ApiAwsCredentialAccessScope accessScope,
      Integer duration) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager
        .validateControlledResourceAndAction(
            userRequest, workspaceUuid, resourceId, getSamAction(accessScope))
        .castByEnum(WsmResourceType.CONTROLLED_AWS_STORAGE_FOLDER);

    Credentials awsCredentials =
        AwsUtils.getAssumeUserRoleCredentials(
            awsCloudContextService.getRequiredAuthentication(),
            awsCloudContextService.discoverEnvironment(),
            getSamUser(),
            Duration.ofSeconds(duration));

    return new ResponseEntity<>(
        new ApiAwsCredential()
            .accessKeyId(awsCredentials.accessKeyId())
            .secretAccessKey(awsCredentials.secretAccessKey())
            .sessionToken(awsCredentials.sessionToken())
            .expiration(awsCredentials.expiration().atOffset(ZoneOffset.UTC)),
        HttpStatus.OK);
  }
}
