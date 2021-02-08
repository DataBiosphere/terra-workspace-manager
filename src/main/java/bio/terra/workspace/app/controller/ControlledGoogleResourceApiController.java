package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.ControlledGoogleResourceApi;
import bio.terra.workspace.generated.model.CreateControlledGoogleBucketRequestBody;
import bio.terra.workspace.generated.model.CreatedControlledGoogleBucket;
import bio.terra.workspace.service.controlledresource.ControlledGoogleResourceService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class ControlledGoogleResourceApiController implements ControlledGoogleResourceApi {
  private final Logger logger =
      LoggerFactory.getLogger(ControlledGoogleResourceApiController.class);

  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final ControlledGoogleResourceService controlledResourceService;
  private final HttpServletRequest request;

  @Autowired
  public ControlledGoogleResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      ControlledGoogleResourceService controlledResourceService,
      HttpServletRequest request) {
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.controlledResourceService = controlledResourceService;
    this.request = request;
  }

  @Override
  public ResponseEntity<CreatedControlledGoogleBucket> createBucket(
      @Valid CreateControlledGoogleBucketRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final String jobId = controlledResourceService.createBucket(body, userRequest);
    return null;
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }
}
