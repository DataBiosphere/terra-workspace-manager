package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.FolderApi;
import bio.terra.workspace.generated.model.ApiCreateFolderRequestBody;
import bio.terra.workspace.generated.model.ApiFolderDescription;
import bio.terra.workspace.generated.model.ApiFolderDescriptionsList;
import bio.terra.workspace.generated.model.ApiUpdateFolderRequestBody;
import bio.terra.workspace.service.folder.FolderService;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.workspace.WorkspaceService;
import java.util.List;
import java.util.Optional;
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
                .parentFolderId(Optional.ofNullable(body.getParentFolderId()))
                .displayName(body.getDisplayName())
                .description(Optional.ofNullable(body.getDescription()))
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
            body.getParentFolderId(),
            body.isMoveToTopLevel());
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

  @Override
  public ResponseEntity<ApiFolderDescriptionsList> listFolders(UUID workspaceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SamWorkspaceAction.READ);

    List<Folder> folders = folderService.listFolders(workspaceId);

    var response =
        new ApiFolderDescriptionsList()
            .folders(
                folders.stream()
                    .map(folder -> buildFolderDescription(folder))
                    .collect(Collectors.toList()));
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  private static ApiFolderDescription buildFolderDescription(Folder folder) {
    return new ApiFolderDescription()
        .id(folder.getId())
        .displayName(folder.getDisplayName())
        .description(folder.getDescription().orElse(null))
        .parentFolderId(folder.getParentFolderId().orElse(null));
  }
}
