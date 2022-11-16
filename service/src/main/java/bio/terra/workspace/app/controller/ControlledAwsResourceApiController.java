package bio.terra.workspace.app.controller;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.ControlledAwsResourceApi;
import bio.terra.workspace.generated.model.ApiAwsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiCreateControlledAwsBucketRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledAwsBucket;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.aws.storagebucket.ControlledAwsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import com.amazonaws.regions.Regions;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
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

  @Autowired
  public ControlledAwsResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      ControlledResourceService controlledResourceService,
      SamService samService,
      FeatureConfiguration features,
      WorkspaceService workspaceService,
      ControlledResourceService controlledResourceService1,
      AwsCloudContextService awsCloudContextService) {
    super(authenticatedUserRequestFactory, request, controlledResourceService, samService);
    this.features = features;
    this.workspaceService = workspaceService;
    this.controlledResourceService = controlledResourceService1;
    this.awsCloudContextService = awsCloudContextService;
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
}
