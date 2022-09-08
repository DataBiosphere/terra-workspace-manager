package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.FolderApi;
import bio.terra.workspace.generated.model.ApiCreateFolderRequestBody;
import bio.terra.workspace.generated.model.ApiFolderDescription;
import bio.terra.workspace.generated.model.ApiUpdateFolderRequestBody;
import bio.terra.workspace.service.folder.FolderService;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.workspace.WorkspaceService;
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
  public ResponseEntity<ApiFolderDescription> createFolder(
      UUID workspaceId, ApiCreateFolderRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SamWorkspaceAction.WRITE);

    Folder folder =
        folderService.createFolder(
            new Folder.Builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .parentFolderId(body.getParentFolderId())
                .displayName(body.getDisplayName())
                .description(body.getDescription())
                .build());
    return new ResponseEntity<>(buildFolderDescription(folder), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiFolderDescription> updateFolder(
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
            body.getParentFolderId());
    return new ResponseEntity<>(buildFolderDescription(folder), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiFolderDescription> getFolder(UUID workspaceId, UUID folderId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SamWorkspaceAction.READ);

    Folder folder = folderService.getFolder(workspaceId, folderId);
    return new ResponseEntity<>(buildFolderDescription(folder), HttpStatus.OK);
  }

  private static ApiFolderDescription buildFolderDescription(Folder folder) {
    return new ApiFolderDescription()
        .id(folder.getId())
        .displayName(folder.getDisplayName())
        .description(folder.getDescription().orElse(null));
  }
}
