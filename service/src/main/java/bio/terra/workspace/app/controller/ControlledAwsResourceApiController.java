package bio.terra.workspace.app.controller;

import bio.terra.aws.resource.discovery.LandingZone;
import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.ControlledAwsResourceApi;
import bio.terra.workspace.generated.model.ApiAwsConsoleLink;
import bio.terra.workspace.generated.model.ApiAwsCredential;
import bio.terra.workspace.generated.model.ApiAwsCredentialAccessScope;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderCreationParameters;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderResource;
import bio.terra.workspace.generated.model.ApiCreateControlledAwsS3StorageFolderRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledAwsS3StorageFolder;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder.ControlledAwsS3StorageFolderResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import software.amazon.awssdk.regions.Region;

@Controller
public class ControlledAwsResourceApiController extends ControlledResourceControllerBase
    implements ControlledAwsResourceApi {

  private final Logger logger = LoggerFactory.getLogger(ControlledAwsResourceApiController.class);

  // TODO(TERRA-304): Move up to base class
  private final FeatureConfiguration features;
  private final WorkspaceService workspaceService;
  private final ControlledResourceService controlledResourceService;
  private final ControlledResourceMetadataManager controlledResourceMetadataManager;

  private final AwsCloudContextService awsCloudContextService;

  @Autowired
  public ControlledAwsResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      FeatureConfiguration features,
      WorkspaceService workspaceService,
      ControlledResourceService controlledResourceService,
      ControlledResourceMetadataManager controlledResourceMetadataManager,
      AwsCloudContextService awsCloudContextService) {
    super(authenticatedUserRequestFactory, request, controlledResourceService, samService);
    this.features = features;
    this.workspaceService = workspaceService;
    this.controlledResourceService = controlledResourceService;
    this.controlledResourceMetadataManager = controlledResourceMetadataManager;

    this.awsCloudContextService = awsCloudContextService;
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAwsS3StorageFolder> createAwsS3StorageFolder(
      UUID workspaceUuid, @Valid ApiCreateControlledAwsS3StorageFolderRequestBody body) {
    features.awsEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            body.getAwsS3StorageFolder().getRegion(),
            userRequest,
            WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER);
    workspaceService.validateMcWorkspaceAndAction(
        userRequest, workspaceUuid, ControllerValidationUtils.samCreateAction(commonFields));

    AwsCloudContext awsCloudContext =
        awsCloudContextService.getRequiredAwsCloudContext(workspaceUuid);

    ApiAwsS3StorageFolderCreationParameters creationParameters = body.getAwsS3StorageFolder();

    Region requestedRegion;
    LandingZone landingZone;
    try {
      String prefixName = commonFields.getName();
      if (prefixName.isEmpty() || prefixName.length() > 1024) {
        throw new BadRequestException("Resource length name must be between 1 and 1024 chars");
      }

      requestedRegion = Region.of(creationParameters.getRegion());
      landingZone =
          awsCloudContextService
              .getLandingZone(awsCloudContext, requestedRegion)
              .orElseThrow(
                  () -> {
                    throw new BadRequestException(
                        String.format(
                            "Unsupported AWS region: '%s'.", creationParameters.getRegion()));
                  });

    } catch (IllegalArgumentException e) {
      throw new BadRequestException(
          String.format("Invalid AWS region: '%s'.", creationParameters.getRegion()));
    }

    ControlledAwsS3StorageFolderResource resource =
        ControlledAwsS3StorageFolderResource.builder()
            .common(commonFields)
            .s3BucketName(landingZone.getStorageBucket().name())
            .prefix(commonFields.getName())
            .build();

    final ControlledAwsS3StorageFolderResource createdBucket =
        controlledResourceService
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, body.getAwsS3StorageFolder())
            .castByEnum(WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER);

    var response =
        new ApiCreatedControlledAwsS3StorageFolder()
            .resourceId(createdBucket.getResourceId())
            .awsS3StorageFolder(createdBucket.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAwsS3StorageFolderResource> getAwsS3StorageFolder(
      UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
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

  @Override
  public ResponseEntity<Void> deleteAwsS3StorageFolder(UUID workspaceUuid, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest,
        workspaceUuid,
        resourceId,
        SamConstants.SamControlledResourceActions.DELETE_ACTION);
    logger.info(
        "deleteAwsS3Bucket workspace {} resource {}",
        workspaceUuid.toString(),
        resourceId.toString());

    controlledResourceService.deleteControlledResourceSync(workspaceUuid, resourceId, userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<ApiAwsConsoleLink> getAwsS3StorageFolderConsoleLink(
      UUID workspaceUuid,
      UUID resourceId,
      ApiAwsCredentialAccessScope accessScope,
      Integer duration) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public ResponseEntity<ApiAwsCredential> getAwsS3StorageFolderCredential(
      UUID workspaceUuid,
      UUID resourceId,
      ApiAwsCredentialAccessScope accessScope,
      Integer duration) {
    throw new NotImplementedException("not implemented");
  }
}
