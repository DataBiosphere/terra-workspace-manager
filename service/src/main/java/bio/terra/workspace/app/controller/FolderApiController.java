package bio.terra.workspace.app.controller;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertApiPropertyToMap;
import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertMapToApiProperties;
import static bio.terra.workspace.common.utils.ControllerValidationUtils.validatePropertiesDeleteRequestBody;
import static bio.terra.workspace.common.utils.ControllerValidationUtils.validatePropertiesUpdateRequestBody;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.generated.controller.FolderApi;
import bio.terra.workspace.generated.model.ApiCreateFolderRequestBody;
import bio.terra.workspace.generated.model.ApiFolder;
import bio.terra.workspace.generated.model.ApiFolderList;
import bio.terra.workspace.generated.model.ApiJobResult;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiUpdateFolderRequestBody;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.folder.FolderService;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.Workspace;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class FolderApiController extends ControllerBase implements FolderApi {
  private final WorkspaceService workspaceService;
  private final WorkspaceActivityLogService workspaceActivityLogService;
  private final FolderService folderService;

  @Autowired
  public FolderApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      FeatureConfiguration features,
      FeatureService featureService,
      JobService jobService,
      JobApiUtils jobApiUtils,
      WorkspaceService workspaceService,
      WorkspaceActivityLogService workspaceActivityLogService,
      FolderService folderService) {
    super(
        authenticatedUserRequestFactory,
        request,
        samService,
        features,
        featureService,
        jobService,
        jobApiUtils);
    this.workspaceService = workspaceService;
    this.workspaceActivityLogService = workspaceActivityLogService;
    this.folderService = folderService;
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiFolder> createFolder(
      UUID workspaceUuid, ApiCreateFolderRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.WRITE);
    workspaceService.validateWorkspaceState(workspace);
    var folderId = UUID.randomUUID();
    Folder folder =
        folderService.createFolder(
            new Folder(
                folderId,
                workspaceUuid,
                body.getDisplayName(),
                body.getDescription(),
                body.getParentFolderId(),
                convertApiPropertyToMap(body.getProperties()),
                samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest),
                /*createdDate=*/ null));
    workspaceActivityLogService.writeActivity(
        userRequest,
        workspaceUuid,
        OperationType.CREATE,
        folderId.toString(),
        ActivityLogChangedTarget.FOLDER);
    return new ResponseEntity<>(
        buildFolder(folderService.getFolder(workspaceUuid, folderId), workspaceUuid),
        HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiFolder> updateFolder(
      UUID workspaceUuid, UUID folderId, ApiUpdateFolderRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.WRITE);
    workspaceService.validateWorkspaceState(workspace);

    Folder folder =
        folderService.updateFolder(
            workspaceUuid,
            folderId,
            body.getDisplayName(),
            body.getDescription(),
            body.getParentFolderId(),
            body.isUpdateParent());
    workspaceActivityLogService.writeActivity(
        userRequest,
        workspaceUuid,
        OperationType.UPDATE,
        folderId.toString(),
        ActivityLogChangedTarget.FOLDER);
    return new ResponseEntity<>(buildFolder(folder, workspaceUuid), HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiFolder> getFolder(UUID workspaceUuid, UUID folderId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.READ);

    Folder folder = folderService.getFolder(workspaceUuid, folderId);
    return new ResponseEntity<>(buildFolder(folder, workspaceUuid), HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiFolderList> listFolders(UUID workspaceUuid) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.READ);

    List<Folder> folders = folderService.listFolders(workspaceUuid);

    var response =
        new ApiFolderList()
            .folders(folders.stream().map(f -> buildFolder(f, workspaceUuid)).toList());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiJobResult> deleteFolderAsync(UUID workspaceUuid, UUID folderId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    // If requester is writer and folder has private resources (not owned by requester), requester
    // won't have permission to delete private resources. That access control check is done in
    // folderService#deleteFolder.
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceUuid, SamWorkspaceAction.WRITE);
    workspaceService.validateWorkspaceState(workspace);

    String jobId = folderService.deleteFolder(workspaceUuid, folderId, userRequest);
    ApiJobResult response = jobApiUtils.fetchJobResult(jobId);
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiJobResult> getDeleteFolderResult(
      UUID workspaceUuid, UUID folderId, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiJobResult response = jobApiUtils.fetchJobResult(jobId);
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

  @WithSpan
  @Override
  public ResponseEntity<Void> updateFolderProperties(
      UUID workspaceUuid, UUID folderUuid, List<ApiProperty> properties) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.WRITE);
    workspaceService.validateWorkspaceState(workspace);

    validatePropertiesUpdateRequestBody(properties);

    folderService.updateFolderProperties(
        workspaceUuid, folderUuid, convertApiPropertyToMap(properties));
    workspaceActivityLogService.writeActivity(
        userRequest,
        workspaceUuid,
        OperationType.UPDATE_PROPERTIES,
        folderUuid.toString(),
        ActivityLogChangedTarget.FOLDER);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @WithSpan
  @Override
  public ResponseEntity<Void> deleteFolderProperties(
      UUID workspaceUuid, UUID folderUuid, List<String> propertyKeys) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    validatePropertiesDeleteRequestBody(propertyKeys);
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.WRITE);
    workspaceService.validateWorkspaceState(workspace);

    folderService.deleteFolderProperties(workspaceUuid, folderUuid, propertyKeys);
    workspaceActivityLogService.writeActivity(
        userRequest,
        workspaceUuid,
        OperationType.DELETE_PROPERTIES,
        folderUuid.toString(),
        ActivityLogChangedTarget.FOLDER);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  private ApiFolder buildFolder(Folder folder, UUID workspaceUuid) {
    Optional<ActivityLogChangeDetails> lastUpdatedDetail =
        workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid, folder.id().toString());
    return new ApiFolder()
        .id(folder.id())
        .displayName(folder.displayName())
        .description(folder.description())
        .parentFolderId(folder.parentFolderId())
        .properties(convertMapToApiProperties(folder.properties()))
        .createdBy(folder.createdByEmail())
        .createdDate(folder.createdDate())
        .lastUpdatedBy(
            lastUpdatedDetail
                .map(ActivityLogChangeDetails::actorEmail)
                .orElse(folder.createdByEmail()))
        .lastUpdatedDate(
            lastUpdatedDetail
                .map(ActivityLogChangeDetails::changeDate)
                .orElse(folder.createdDate()));
  }
}
