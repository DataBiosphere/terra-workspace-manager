package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.ControlledAwsResourceApi;
import bio.terra.workspace.generated.model.ApiAwsBucketResource;
import bio.terra.workspace.generated.model.ApiAwsConsoleLink;
import bio.terra.workspace.generated.model.ApiAwsCredential;
import bio.terra.workspace.generated.model.ApiAwsCredentialAccessScope;
import bio.terra.workspace.generated.model.ApiAwsSageMakerNotebookResource;
import bio.terra.workspace.generated.model.ApiAwsSageMakerProxyUrlView;
import bio.terra.workspace.generated.model.ApiCreateControlledAwsBucketRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAwsSageMakerNotebookRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledAwsBucket;
import bio.terra.workspace.generated.model.ApiCreatedControlledAwsSageMakerNotebookResult;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.workspace.exceptions.CloudPlatformNotImplementedException;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class ControlledAwsResourceApiController extends ControlledResourceControllerBase
    implements ControlledAwsResourceApi {

  public ControlledAwsResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      ControlledResourceService controlledResourceService,
      SamService samService) {
    super(authenticatedUserRequestFactory, request, controlledResourceService, samService);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAwsBucket> createAwsBucket(
      UUID workspaceUuid, @Valid ApiCreateControlledAwsBucketRequestBody body) {
    throw new CloudPlatformNotImplementedException("Platform support for AWS not implemented");
  }

  @Override
  public ResponseEntity<ApiAwsBucketResource> getAwsBucket(UUID workspaceUuid, UUID resourceId) {
    throw new CloudPlatformNotImplementedException("Platform support for AWS not implemented");
  }

  @Override
  public ResponseEntity<ApiAwsCredential> getAwsBucketCredential(
      UUID workspaceUuid,
      UUID resourceId,
      ApiAwsCredentialAccessScope accessScope,
      Integer duration) {
    throw new CloudPlatformNotImplementedException("Platform support for AWS not implemented");
  }

  @Override
  public ResponseEntity<ApiAwsConsoleLink> getAwsBucketConsoleLink(
      UUID workspaceUuid,
      UUID resourceId,
      ApiAwsCredentialAccessScope accessScope,
      Integer duration) {
    throw new CloudPlatformNotImplementedException("Platform support for AWS not implemented");
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAwsSageMakerNotebookResult> createAwsSageMakerNotebook(
      UUID workspaceUuid, ApiCreateControlledAwsSageMakerNotebookRequestBody body) {
    throw new CloudPlatformNotImplementedException("Platform support for AWS not implemented");
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAwsSageMakerNotebookResult>
      getCreateAwsSageMakerNotebookResult(UUID workspaceUuid, String jobId) {
    throw new CloudPlatformNotImplementedException("Platform support for AWS not implemented");
  }

  @Override
  public ResponseEntity<ApiAwsSageMakerNotebookResource> getAwsSageMakerNotebook(
      UUID workspaceUuid, UUID resourceId) {
    throw new CloudPlatformNotImplementedException("Platform support for AWS not implemented");
  }

  @Override
  public ResponseEntity<ApiAwsCredential> getAwsSageMakerNotebookCredential(
      UUID workspaceUuid,
      UUID resourceId,
      ApiAwsCredentialAccessScope accessScope,
      Integer duration) {
    throw new CloudPlatformNotImplementedException("Platform support for AWS not implemented");
  }

  @Override
  public ResponseEntity<ApiAwsConsoleLink> getAwsSageMakerNotebookConsoleLink(
      UUID workspaceUuid,
      UUID resourceId,
      ApiAwsCredentialAccessScope accessScope,
      Integer duration) {
    throw new CloudPlatformNotImplementedException("Platform support for AWS not implemented");
  }

  @Override
  public ResponseEntity<ApiAwsConsoleLink> getAwsSageMakerNotebookProxyUrl(
      UUID workspaceUuid, UUID resourceId, ApiAwsSageMakerProxyUrlView view, Integer duration) {
    throw new CloudPlatformNotImplementedException("Platform support for AWS not implemented");
  }
}
