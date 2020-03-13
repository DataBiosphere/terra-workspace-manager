package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.WorkspaceApi;
import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import bio.terra.workspace.service.create.CreateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;


@Controller
public class WorkspaceApiController implements WorkspaceApi {
  private CreateService createService;

  @Autowired
  public WorkspaceApiController(CreateService createService) {
      this.createService = createService;
  }

  @Override
  public ResponseEntity<CreatedWorkspace> create(@RequestBody CreateWorkspaceRequestBody body) {
      CreatedWorkspace result = createService.createWorkspace(body);
      return new ResponseEntity<>(result, HttpStatus.OK);
  }

}
