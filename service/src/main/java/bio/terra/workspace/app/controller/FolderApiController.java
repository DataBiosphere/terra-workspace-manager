package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.FolderApi;
import bio.terra.workspace.generated.model.ApiCreateFolderRequestBody;
import bio.terra.workspace.generated.model.ApiFolder;
import bio.terra.workspace.generated.model.ApiFoldersList;
import bio.terra.workspace.generated.model.ApiUpdateFolderRequestBody;
import bio.terra.workspace.service.folder.FolderService;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.workspace.WorkspaceService;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class FolderApiController extends ControllerBase implements FolderApi {

  private final FolderService folderService;
  private final WorkspaceService workspaceService;

  @Autowired
  public FolderApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      FolderService folderService,
      WorkspaceService workspaceService) {
    super(authenticatedUserRequestFactory, request, samService);
    this.folderService = folderService;
    this.workspaceService = workspaceService;
  }

  @Override
  public ResponseEntity<ApiFolder> createFolder(UUID workspaceId, ApiCreateFolderRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SamWorkspaceAction.WRITE);

    Folder folder =
        folderService.createFolder(
            new Folder(
                UUID.randomUUID(),
                workspaceId,
                body.getDisplayName(),
                body.getDescription(),
                body.getParentFolderId()));
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
  public ResponseEntity<ApiFoldersList> listFolders(UUID workspaceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SamWorkspaceAction.READ);

    List<Folder> folders = folderService.listFolders(workspaceId);

    var response =
        new ApiFoldersList()
            .folders(
                folders.stream().map(folder -> buildFolder(folder)).collect(Collectors.toList()));
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteFolder(UUID workspaceId, UUID folderId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, workspaceId, SamWorkspaceAction.WRITE);
    folderService.deleteFolder(workspaceId, folderId);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  private static ApiFolder buildFolder(Folder folder) {
    return new ApiFolder()
        .id(folder.id())
        .displayName(folder.displayName())
        .description(folder.description())
        .parentFolderId(folder.parentFolderId());
  }
}
