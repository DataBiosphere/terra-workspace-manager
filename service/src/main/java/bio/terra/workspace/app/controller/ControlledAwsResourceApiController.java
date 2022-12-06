package bio.terra.workspace.app.controller;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.common.utils.MultiCloudUtils;
import bio.terra.workspace.generated.controller.ControlledAwsResourceApi;
import bio.terra.workspace.generated.model.ApiAwsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiAwsBucketResource;
import bio.terra.workspace.generated.model.ApiAwsConsoleLink;
import bio.terra.workspace.generated.model.ApiAwsCredential;
import bio.terra.workspace.generated.model.ApiAwsCredentialAccessScope;
import bio.terra.workspace.generated.model.ApiAwsSageMakerNotebookResource;
import bio.terra.workspace.generated.model.ApiAwsSageMakerProxyUrlView;
import bio.terra.workspace.generated.model.ApiAwsSagemakerNotebookDefaultBucket;
import bio.terra.workspace.generated.model.ApiCreateControlledAwsBucketRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAwsSageMakerNotebookRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledAwsBucket;
import bio.terra.workspace.generated.model.ApiCreatedControlledAwsSageMakerNotebookResult;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakernotebook.ControlledAwsSageMakerNotebookResource;
import bio.terra.workspace.service.resource.controlled.cloud.aws.storagebucket.ControlledAwsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.securitytoken.model.Tag;
import java.net.URI;
import java.net.URL;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class ControlledAwsResourceApiController extends ControlledResourceControllerBase
    implements ControlledAwsResourceApi {
  private final Logger logger = LoggerFactory.getLogger(ControlledAwsResourceApiController.class);

  private final FeatureConfiguration features;
  private final WorkspaceService workspaceService;
  private final ControlledResourceService controlledResourceService;
  private final AwsCloudContextService awsCloudContextService;
  private final ControlledResourceMetadataManager controlledResourceMetadataManager;
  private final JobApiUtils jobApiUtils;
  private final JobService jobService;

  @Autowired
  public ControlledAwsResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      ControlledResourceService controlledResourceService,
      SamService samService,
      FeatureConfiguration features,
      WorkspaceService workspaceService,
      ControlledResourceService controlledResourceService1,
      AwsCloudContextService awsCloudContextService,
      ControlledResourceMetadataManager controlledResourceMetadataManager,
      JobApiUtils jobApiUtils,
      JobService jobService) {
    super(authenticatedUserRequestFactory, request, controlledResourceService, samService);
    this.features = features;
    this.workspaceService = workspaceService;
    this.controlledResourceService = controlledResourceService1;
    this.awsCloudContextService = awsCloudContextService;
    this.controlledResourceMetadataManager = controlledResourceMetadataManager;
    this.jobApiUtils = jobApiUtils;
    this.jobService = jobService;
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAwsBucket> createAwsBucket(
      UUID workspaceUuid, @Valid ApiCreateControlledAwsBucketRequestBody body) {
    features.awsEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ControlledResourceFields commonFields =
        toCommonFields(workspaceUuid, body.getCommon(), userRequest);
    workspaceService.validateMcWorkspaceAndAction(
        userRequest, workspaceUuid, ControllerValidationUtils.samCreateAction(commonFields));

    AwsCloudContext awsCloudContext =
        awsCloudContextService.getRequiredAwsCloudContext(workspaceUuid);

    final ApiAwsBucketCreationParameters creationParameters = body.getAwsBucket();
    Regions requestedRegion;

    try {
      requestedRegion = Regions.fromName(creationParameters.getLocation());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(
          String.format(
              "Region '%s' is not a valid AWS region.", creationParameters.getLocation()));
    }

    String s3BucketName = awsCloudContext.getBucketNameForRegion(requestedRegion);

    if (s3BucketName == null) {
      throw new NotFoundException(
          String.format(
              "Configured AWS Landing zone does not support S3 buckets in region '%s'.",
              requestedRegion));
    }

    ControlledAwsBucketResource resource =
        ControlledAwsBucketResource.builder()
            .common(commonFields)
            .s3BucketName(s3BucketName)
            .prefix(commonFields.getName())
            .region(creationParameters.getLocation())
            .build();

    final ControlledAwsBucketResource createdAwsBucket =
        controlledResourceService
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, body.getAwsBucket())
            .castByEnum(WsmResourceType.CONTROLLED_AWS_BUCKET);
    var response =
        new ApiCreatedControlledAwsBucket()
            .resourceId(createdAwsBucket.getResourceId())
            .awsBucket(createdAwsBucket.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAwsBucketResource> getAwsBucket(UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAwsBucketResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest,
                workspaceUuid,
                resourceId,
                SamConstants.SamControlledResourceActions.READ_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_AWS_BUCKET);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  private String getSamAction(ApiAwsCredentialAccessScope accessScope) {
    return (accessScope == ApiAwsCredentialAccessScope.WRITE_READ)
        ? SamConstants.SamControlledResourceActions.WRITE_ACTION
        : SamConstants.SamControlledResourceActions.READ_ACTION;
  }

  private Collection<Tag> getBucketTags(
      UUID workspaceId,
      ApiAwsCredentialAccessScope accessScope,
      ControlledAwsBucketResource resource) {
    Set<Tag> tags = new HashSet<>();

    AwsUtils.addWorkspaceTags(tags, workspaceId);
    AwsUtils.addBucketTags(
        tags,
        (accessScope == ApiAwsCredentialAccessScope.WRITE_READ)
            ? AwsUtils.RoleTag.WRITER
            : AwsUtils.RoleTag.READER,
        resource.getS3BucketName(),
        resource.getPrefix());

    return tags;
  }

  @Override
  public ResponseEntity<ApiAwsConsoleLink> getAwsBucketConsoleLink(
      UUID workspaceUuid,
      UUID resourceId,
      ApiAwsCredentialAccessScope accessScope,
      Integer duration) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAwsBucketResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceId, getSamAction(accessScope))
            .castByEnum(WsmResourceType.CONTROLLED_AWS_BUCKET);

    AwsCloudContext awsCloudContext =
        awsCloudContextService.getRequiredAwsCloudContext(workspaceUuid);

    Credentials awsCredentials =
        MultiCloudUtils.assumeAwsUserRoleFromGcp(
            awsCloudContext, getSamUser(), getBucketTags(workspaceUuid, accessScope, resource));

    URL destinationUrl;

    try {
      URI uri =
          new URIBuilder()
              .setScheme("https")
              .setHost("s3.console.aws.amazon.com")
              .setPath(String.format("s3/buckets/%s", resource.getS3BucketName()))
              .setParameter("region", resource.getRegion())
              .setParameter("prefix", String.format("%s/", resource.getPrefix()))
              .build();

      destinationUrl = uri.toURL();
    } catch (Exception e) {
      throw new ApiException("Failed to create destination URL.", e);
    }

    URL url = AwsUtils.createConsoleUrl(awsCredentials, duration, destinationUrl);
    return new ResponseEntity<>(new ApiAwsConsoleLink().url(url.toString()), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAwsCredential> getAwsBucketCredential(
      UUID workspaceUuid,
      UUID resourceId,
      ApiAwsCredentialAccessScope accessScope,
      Integer duration) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAwsBucketResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceId, getSamAction(accessScope))
            .castByEnum(WsmResourceType.CONTROLLED_AWS_BUCKET);

    AwsCloudContext awsCloudContext =
        awsCloudContextService.getRequiredAwsCloudContext(workspaceUuid);

    Credentials awsCredentials =
        MultiCloudUtils.assumeAwsUserRoleFromGcp(
            awsCloudContext,
            getSamUser(),
            getBucketTags(workspaceUuid, accessScope, resource),
            duration);

    return new ResponseEntity<>(
        new ApiAwsCredential()
            .version(1)
            .accessKeyId(awsCredentials.getAccessKeyId())
            .secretAccessKey(awsCredentials.getSecretAccessKey())
            .sessionToken(awsCredentials.getSessionToken())
            .expiration(awsCredentials.getExpiration().toInstant().atOffset(ZoneOffset.UTC)),
        HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAwsSageMakerNotebookResult> createAwsSageMakerNotebook(
      UUID workspaceUuid, ApiCreateControlledAwsSageMakerNotebookRequestBody body) {
    features.awsEnabledCheck();

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResourceFields commonFields =
        toCommonFields(workspaceUuid, body.getCommon(), userRequest);

    // Check authz before reading the cloud context.
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, ControllerValidationUtils.samCreateAction(commonFields));

    ControlledAwsBucketResource defaultBucketResource = null;

    ApiAwsSagemakerNotebookDefaultBucket defaultBucket =
        body.getAwsSageMakerNotebook().getDefaultBucket();

    if (defaultBucket != null) {
      defaultBucketResource =
          controlledResourceMetadataManager
              .validateControlledResourceAndAction(
                  userRequest,
                  workspaceUuid,
                  defaultBucket.getBucketId(),
                  getSamAction(defaultBucket.getAccessScope()))
              .castByEnum(WsmResourceType.CONTROLLED_AWS_BUCKET);
    }

    ControlledAwsSageMakerNotebookResource resource =
        ControlledAwsSageMakerNotebookResource.builder()
            .common(commonFields)
            .instanceId(body.getAwsSageMakerNotebook().getInstanceId())
            .region(body.getAwsSageMakerNotebook().getLocation())
            .instanceType(body.getAwsSageMakerNotebook().getInstanceType())
            .defaultBucket(body.getAwsSageMakerNotebook().getDefaultBucket())
            .build();

    String jobId =
        controlledResourceService.createAwsSageMakerNotebook(
            resource,
            body.getAwsSageMakerNotebook(),
            commonFields.getIamRole(),
            body.getJobControl(),
            getAsyncResultEndpoint(body.getJobControl().getId(), "create-result"),
            userRequest,
            getSamUser(),
            defaultBucketResource);

    ApiCreatedControlledAwsSageMakerNotebookResult result =
        fetchNotebookInstanceCreateResult(jobId);

    result
        .getAiNotebookInstance()
        .getAttributes()
        .setAwsAccountNumber(
            awsCloudContextService.getRequiredAwsCloudContext(workspaceUuid).getAccountNumber());

    return new ResponseEntity<>(result, getAsyncResponseCode((result.getJobReport())));
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAwsSageMakerNotebookResult>
      getCreateAwsSageMakerNotebookResult(UUID workspaceUuid, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiCreatedControlledAwsSageMakerNotebookResult result =
        fetchNotebookInstanceCreateResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  private ApiCreatedControlledAwsSageMakerNotebookResult fetchNotebookInstanceCreateResult(
      String jobId) {
    JobApiUtils.AsyncJobResult<ControlledAwsSageMakerNotebookResource> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, ControlledAwsSageMakerNotebookResource.class);

    ApiAwsSageMakerNotebookResource apiResource = null;
    if (jobResult.getJobReport().getStatus().equals(ApiJobReport.StatusEnum.SUCCEEDED)) {
      ControlledAwsSageMakerNotebookResource resource = jobResult.getResult();
      apiResource = resource.toApiResource();
    }
    return new ApiCreatedControlledAwsSageMakerNotebookResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .aiNotebookInstance(apiResource);
  }

  @Override
  public ResponseEntity<ApiAwsSageMakerNotebookResource> getAwsSageMakerNotebook(
      UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAwsSageMakerNotebookResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest,
                workspaceUuid,
                resourceId,
                SamConstants.SamControlledResourceActions.READ_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_AWS_SAGEMAKER_NOTEBOOK);

    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAwsConsoleLink> getAwsSageMakerNotebookConsoleLink(
      UUID workspaceUuid,
      UUID resourceId,
      ApiAwsCredentialAccessScope accessScope,
      Integer duration) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAwsSageMakerNotebookResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceId, getSamAction(accessScope))
            .castByEnum(WsmResourceType.CONTROLLED_AWS_SAGEMAKER_NOTEBOOK);

    AwsCloudContext awsCloudContext =
        awsCloudContextService.getRequiredAwsCloudContext(workspaceUuid);

    Collection<Tag> tags = new HashSet<>();
    AwsUtils.addUserTags(tags, getSamUser());
    AwsUtils.addWorkspaceTags(tags, workspaceUuid);

    Credentials awsCredentials =
        MultiCloudUtils.assumeAwsUserRoleFromGcp(awsCloudContext, getSamUser(), tags);

    URL destinationUrl;

    try {
      URI uri =
          new URIBuilder()
              .setScheme("https")
              .setHost(String.format("%s.console.aws.amazon.com", resource.getRegion()))
              .setPath("sagemaker/home")
              .setParameter("region", resource.getRegion())
              .setFragment(String.format("/notebook-instances/%s", resource.getInstanceId()))
              .build();

      destinationUrl = uri.toURL();
    } catch (Exception e) {
      throw new ApiException("Failed to create destination URL.", e);
    }

    URL url = AwsUtils.createConsoleUrl(awsCredentials, duration, destinationUrl);
    return new ResponseEntity<>(new ApiAwsConsoleLink().url(url.toString()), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAwsCredential> getAwsSageMakerNotebookCredential(
      UUID workspaceUuid,
      UUID resourceId,
      ApiAwsCredentialAccessScope accessScope,
      Integer duration) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAwsSageMakerNotebookResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceId, getSamAction(accessScope))
            .castByEnum(WsmResourceType.CONTROLLED_AWS_SAGEMAKER_NOTEBOOK);

    AwsCloudContext awsCloudContext =
        awsCloudContextService.getRequiredAwsCloudContext(workspaceUuid);

    Collection<Tag> tags = new HashSet<>();
    AwsUtils.addUserTags(tags, getSamUser());
    AwsUtils.addWorkspaceTags(tags, workspaceUuid);

    Credentials awsCredentials =
        MultiCloudUtils.assumeAwsUserRoleFromGcp(awsCloudContext, getSamUser(), tags);

    return new ResponseEntity<>(
        new ApiAwsCredential()
            .version(1)
            .accessKeyId(awsCredentials.getAccessKeyId())
            .secretAccessKey(awsCredentials.getSecretAccessKey())
            .sessionToken(awsCredentials.getSessionToken())
            .expiration(awsCredentials.getExpiration().toInstant().atOffset(ZoneOffset.UTC)),
        HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAwsConsoleLink> getAwsSageMakerNotebookProxyUrl(
      UUID workspaceUuid, UUID resourceId, ApiAwsSageMakerProxyUrlView view, Integer duration) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAwsSageMakerNotebookResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest,
                workspaceUuid,
                resourceId,
                SamConstants.SamControlledResourceActions.READ_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_AWS_SAGEMAKER_NOTEBOOK);

    AwsCloudContext awsCloudContext =
        awsCloudContextService.getRequiredAwsCloudContext(workspaceUuid);

    Collection<Tag> tags = new HashSet<>();
    AwsUtils.addUserTags(tags, getSamUser());
    AwsUtils.addWorkspaceTags(tags, workspaceUuid);

    Credentials awsCredentials =
        MultiCloudUtils.assumeAwsUserRoleFromGcp(awsCloudContext, getSamUser(), tags);

    URL url =
        AwsUtils.getSageMakerNotebookProxyUrl(
            awsCredentials,
            Regions.fromName(resource.getRegion()),
            resource.getInstanceId(),
            duration,
            view.equals(ApiAwsSageMakerProxyUrlView.JUPYTERLAB) ? "lab" : "classic");
    return new ResponseEntity<>(new ApiAwsConsoleLink().url(url.toString()), HttpStatus.OK);
  }
}
