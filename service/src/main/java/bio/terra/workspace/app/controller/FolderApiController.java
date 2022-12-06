package bio.terra.workspace.app.controller;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertApiPropertyToMap;
import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertMapToApiProperties;
import static bio.terra.workspace.common.utils.ControllerValidationUtils.validatePropertiesDeleteRequestBody;
import static bio.terra.workspace.common.utils.ControllerValidationUtils.validatePropertiesUpdateRequestBody;

import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.generated.controller.FolderApi;
import bio.terra.workspace.generated.model.ApiCreateFolderRequestBody;
import bio.terra.workspace.generated.model.ApiFolder;
import bio.terra.workspace.generated.model.ApiFolderList;
import bio.terra.workspace.generated.model.ApiJobResult;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiUpdateFolderRequestBody;
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
import java.util.List;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class FolderApiController extends ControllerBase implements FolderApi {

  private final FolderService folderService;
  private final WorkspaceService workspaceService;
  private final JobApiUtils jobApiUtils;
  private final JobService jobService;
  private final WorkspaceActivityLogService workspaceActivityLogService;

  @Autowired
  public FolderApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      FolderService folderService,
      WorkspaceService workspaceService,
      JobApiUtils jobApiUtils,
      JobService jobService,
      WorkspaceActivityLogService workspaceActivityLogService) {
    super(authenticatedUserRequestFactory, request, samService);
    this.folderService = folderService;
    this.workspaceService = workspaceService;
    this.jobApiUtils = jobApiUtils;
    this.jobService = jobService;
    this.workspaceActivityLogService = workspaceActivityLogService;
  }

  @Override
  public ResponseEntity<ApiFolder> createFolder(UUID workspaceId, ApiCreateFolderRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SamWorkspaceAction.WRITE);
    var folderId = UUID.randomUUID();
    Folder folder =
        folderService.createFolder(
            new Folder(
                folderId,
                workspaceId,
                body.getDisplayName(),
                body.getDescription(),
                body.getParentFolderId(),
                convertApiPropertyToMap(body.getProperties()),
                getSamService().getUserEmailFromSamAndRethrowOnInterrupt(userRequest),
                /*createdDate=*/ null));
    workspaceActivityLogService.writeActivity(
        userRequest,
        workspaceId,
        OperationType.CREATE,
        folderId.toString(),
        ActivityLogChangedTarget.FOLDER);
    return new ResponseEntity<>(buildFolder(folder), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiFolder> updateFolder(
      UUID workspaceId, UUID folderId, ApiUpdateFolderRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SamWorkspaceAction.WRITE);
    Folder folder =
        folderService.updateFolder(
            workspaceId,
            folderId,
            body.getDisplayName(),
            body.getDescription(),
            body.getParentFolderId(),
            body.isUpdateParent());
    workspaceActivityLogService.writeActivity(
        userRequest,
        workspaceId,
        OperationType.UPDATE,
        folderId.toString(),
        ActivityLogChangedTarget.FOLDER);
    return new ResponseEntity<>(buildFolder(folder), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiFolder> getFolder(UUID workspaceId, UUID folderId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SamWorkspaceAction.READ);

    Folder folder = folderService.getFolder(workspaceId, folderId);
    return new ResponseEntity<>(buildFolder(folder), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiFolderList> listFolders(UUID workspaceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SamWorkspaceAction.READ);

    List<Folder> folders = folderService.listFolders(workspaceId);

    var response =
        new ApiFolderList()
            .folders(folders.stream().map(FolderApiController::buildFolder).toList());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  // TODO (PF-2159): Delete this endpoint once UI has switched over to use deleteFolderAsync.
  public ResponseEntity<ApiJobResult> deleteFolder(UUID workspaceId, UUID folderId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    // If requester is writer and folder has private resources (not owned by requester), requester
    // won't have permission to delete private resources. That access control check is done in
    // folderService#deleteFolder.
    workspaceService.validateWorkspaceAndAction(userRequest, workspaceId, SamWorkspaceAction.WRITE);

    String jobId = folderService.deleteFolder(workspaceId, folderId, userRequest);
    ApiJobResult response = jobApiUtils.fetchJobResult(jobId);
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiJobResult> deleteFolderAsync(UUID workspaceId, UUID folderId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    // If requester is writer and folder has private resources (not owned by requester), requester
    // won't have permission to delete private resources. That access control check is done in
    // folderService#deleteFolder.
    workspaceService.validateWorkspaceAndAction(userRequest, workspaceId, SamWorkspaceAction.WRITE);

    String jobId = folderService.deleteFolder(workspaceId, folderId, userRequest);
    ApiJobResult response = jobApiUtils.fetchJobResult(jobId);
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiJobResult> getDeleteFolderResult(
      UUID workspaceId, UUID folderId, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceId);
    ApiJobResult response = jobApiUtils.fetchJobResult(jobId);
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

  @Override
  public ResponseEntity<Void> updateFolderProperties(
      UUID workspaceUuid, UUID folderUuid, List<ApiProperty> properties) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.WRITE);

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

  @Override
  public ResponseEntity<Void> deleteFolderProperties(
      UUID workspaceUuid, UUID folderUuid, List<String> propertyKeys) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    validatePropertiesDeleteRequestBody(propertyKeys);
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.WRITE);
    folderService.deleteFolderProperties(workspaceUuid, folderUuid, propertyKeys);
    workspaceActivityLogService.writeActivity(
        userRequest,
        workspaceUuid,
        OperationType.DELETE_PROPERTIES,
        folderUuid.toString(),
        ActivityLogChangedTarget.FOLDER);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  private static ApiFolder buildFolder(Folder folder) {
    return new ApiFolder()
        .id(folder.id())
        .displayName(folder.displayName())
        .description(folder.description())
        .parentFolderId(folder.parentFolderId())
        .properties(convertMapToApiProperties(folder.properties()))
        .createdBy(folder.createdByEmail())
        .createdDate(folder.createdDate());
  }
}
