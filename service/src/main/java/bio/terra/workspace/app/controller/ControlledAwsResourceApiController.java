package bio.terra.workspace.app.controller;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.common.utils.MultiCloudUtils;
import bio.terra.workspace.generated.controller.ControlledAwsResourceApi;
import bio.terra.workspace.generated.model.ApiAwsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiAwsBucketResource;
import bio.terra.workspace.generated.model.ApiAwsCredentialAccessScope;
import bio.terra.workspace.generated.model.ApiControlledAwsBucketConsoleLink;
import bio.terra.workspace.generated.model.ApiControlledAwsBucketCredential;
import bio.terra.workspace.generated.model.ApiCreateControlledAwsBucketRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledAwsBucket;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
      ControlledResourceMetadataManager controlledResourceMetadataManager) {
    super(authenticatedUserRequestFactory, request, controlledResourceService, samService);
    this.features = features;
    this.workspaceService = workspaceService;
    this.controlledResourceService = controlledResourceService1;
    this.awsCloudContextService = awsCloudContextService;
    this.controlledResourceMetadataManager = controlledResourceMetadataManager;
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
            .prefix(AwsUtils.generateUniquePrefix())
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
      ApiAwsCredentialAccessScope accessScope, ControlledAwsBucketResource resource) {
    List<Tag> tags = new ArrayList<>();

    tags.add(
        new Tag()
            .withKey("ws_role")
            .withValue(
                (accessScope == ApiAwsCredentialAccessScope.WRITE_READ) ? "writer" : "reader"));

    tags.add(new Tag().withKey("s3_bucket").withValue(resource.getS3BucketName()));
    tags.add(new Tag().withKey("terra_bucket").withValue(resource.getPrefix()));
    return tags;
  }

  @Override
  public ResponseEntity<ApiControlledAwsBucketConsoleLink> getAwsBucketConsoleLink(
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
            awsCloudContext, getSamUser().getEmail(), getBucketTags(accessScope, resource));

    URL destinationUrl;

    try {
      URI uri =
          new URIBuilder()
              .setScheme("https")
              .setHost("s3.console.aws.amazon.com")
              .setPath(String.format("s3/buckets/%s", resource.getS3BucketName()))
              .setParameter("region", "us-east-1")
              .setParameter("prefix", String.format("%s/", resource.getPrefix()))
              .build();

      destinationUrl = uri.toURL();
    } catch (Exception e) {
      throw new ApiException("Failed to create destination URL.", e);
    }

    URL url = AwsUtils.createConsoleUrl(awsCredentials, duration, destinationUrl);
    return new ResponseEntity<>(
        new ApiControlledAwsBucketConsoleLink().url(url.toString()), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiControlledAwsBucketCredential> getAwsBucketCredential(
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
            getSamUser().getEmail(),
            getBucketTags(accessScope, resource),
            duration);

    return new ResponseEntity<>(
        new ApiControlledAwsBucketCredential()
            .version(1)
            .accessKeyId(awsCredentials.getAccessKeyId())
            .secretAccessKey(awsCredentials.getSecretAccessKey())
            .sessionToken(awsCredentials.getSessionToken())
            .expiration(awsCredentials.getExpiration().toInstant().atOffset(ZoneOffset.UTC)),
        HttpStatus.OK);
  }
}
