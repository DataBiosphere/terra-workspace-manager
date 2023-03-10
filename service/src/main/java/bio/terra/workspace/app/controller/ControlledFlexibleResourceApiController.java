package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.ControlledFlexibleResourceApi;
import bio.terra.workspace.generated.model.ApiCreateControlledFlexibleResourceRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledFlexibleResource;
import bio.terra.workspace.generated.model.ApiFlexibleResource;
import bio.terra.workspace.generated.model.ApiUpdateControlledFlexibleResourceRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.FlexResourceCreationParameters;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

/**
 * Super class for controllers containing common code. The code in here requires the @Autowired
 * beans from the @Controller classes, so it is better as a superclass rather than static methods.
 */
@Controller
public class ControlledFlexibleResourceApiController extends ControlledResourceControllerBase
    implements ControlledFlexibleResourceApi {

  private final Logger logger =
      LoggerFactory.getLogger(ControlledFlexibleResourceApiController.class);
  private final ControlledResourceService controlledResourceService;
  private final WorkspaceService workspaceService;

  private final ControlledResourceMetadataManager controlledResourceMetadataManager;

  @Autowired
  public ControlledFlexibleResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      ControlledResourceService controlledResourceService,
      SamService samService,
      WorkspaceService workspaceService,
      ControlledResourceMetadataManager controlledResourceMetadataManager) {
    super(authenticatedUserRequestFactory, request, controlledResourceService, samService);
    this.workspaceService = workspaceService;
    this.controlledResourceService = controlledResourceService;
    this.controlledResourceMetadataManager = controlledResourceMetadataManager;
  }

  @Traced
  @Override
  public ResponseEntity<ApiCreatedControlledFlexibleResource> createFlexibleResource(
      UUID workspaceUuid, @Valid ApiCreateControlledFlexibleResourceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            null,
            userRequest,
            WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE);

    byte[] encodedJSON = body.getFlexibleResource().getData();
    String decodedJSON = ControlledFlexibleResource.getDecodedJSONFromByteArray(encodedJSON);

    String typeNamespace = body.getFlexibleResource().getTypeNamespace();
    String type = body.getFlexibleResource().getType();

    ControlledFlexibleResource resource =
        ControlledFlexibleResource.builder()
            .common(commonFields)
            .typeNamespace(typeNamespace)
            .type(type)
            .data(decodedJSON)
            .build();

    FlexResourceCreationParameters creationParameters =
        new FlexResourceCreationParameters()
            .type(type)
            .typeNamespace(typeNamespace)
            .data(encodedJSON);

    ControlledFlexibleResource createdFlexibleResource =
        getControlledResourceService()
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE);

    var response =
        new ApiCreatedControlledFlexibleResource()
            .resourceId(createdFlexibleResource.getResourceId())
            .flexibleResource(createdFlexibleResource.toApiResource());

    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiFlexibleResource> updateFlexibleResource(
      UUID workspaceUuid,
      UUID resourceId,
      @Valid ApiUpdateControlledFlexibleResourceRequestBody body) {
    logger.info(
        "Updating flexible resource; resourceId {} workspaceId {}", resourceId, workspaceUuid);
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledFlexibleResource flexibleResource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest,
                workspaceUuid,
                resourceId,
                SamConstants.SamControlledResourceActions.EDIT_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE);

    getControlledResourceService()
        .updateFlexResource(
            flexibleResource,
            body.getUpdateParameters(),
            body.getName(),
            body.getDescription(),
            userRequest);

    // Retrieve and cast response to ApiFlexibleResource
    ControlledFlexibleResource updatedResource =
        getControlledResourceService()
            .getControlledResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<Void> deleteFlexibleResource(UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest,
        workspaceUuid,
        resourceId,
        SamConstants.SamControlledResourceActions.DELETE_ACTION);
    logger.info(
        "Deleting controlled flexible resource {} in workspace {}",
        resourceId.toString(),
        workspaceUuid.toString());
    getControlledResourceService()
        .deleteControlledResourceSync(workspaceUuid, resourceId, userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Traced
  @Override
  public ResponseEntity<ApiFlexibleResource> getFlexibleResource(
      UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledFlexibleResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest,
                workspaceUuid,
                resourceId,
                SamConstants.SamControlledResourceActions.READ_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }
}
