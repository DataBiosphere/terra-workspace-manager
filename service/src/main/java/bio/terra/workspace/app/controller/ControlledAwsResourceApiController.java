package bio.terra.workspace.app.controller;

import bio.terra.aws.resource.discovery.LandingZone;
import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.ControlledAwsResourceApi;
import bio.terra.workspace.generated.model.ApiAwsCredential;
import bio.terra.workspace.generated.model.ApiAwsCredentialAccessScope;
import bio.terra.workspace.generated.model.ApiAwsStorageFolderCreationParameters;
import bio.terra.workspace.generated.model.ApiAwsStorageFolderResource;
import bio.terra.workspace.generated.model.ApiCreateControlledAwsStorageFolderRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledAwsStorageFolder;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.aws.storageFolder.ControlledAwsStorageFolderResource;
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

  @Override
  public ResponseEntity<ApiCreatedControlledAwsStorageFolder> createAwsStorageFolder(
      UUID workspaceUuid, @Valid ApiCreateControlledAwsStorageFolderRequestBody body) {
    features.awsEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
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

    final ControlledAwsStorageFolderResource createdBucket =
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
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
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
  public ResponseEntity<Void> deleteAwsStorageFolder(UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest,
        workspaceUuid,
        resourceId,
        SamConstants.SamControlledResourceActions.DELETE_ACTION);
    logger.info(
        "deleteAwsStorageFolder workspace: {}, resourceId: {}",
        workspaceUuid.toString(),
        resourceId.toString());

    controlledResourceService.deleteControlledResourceSync(workspaceUuid, resourceId, userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<ApiAwsCredential> getAwsStorageFolderCredential(
      UUID workspaceUuid,
      UUID resourceId,
      ApiAwsCredentialAccessScope accessScope,
      Integer duration) {
    throw new NotImplementedException("not implemented");
  }
}
