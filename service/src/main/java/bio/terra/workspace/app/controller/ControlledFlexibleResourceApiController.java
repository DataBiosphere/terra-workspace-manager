package bio.terra.workspace.app.controller;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.ControlledFlexibleResourceApi;
import bio.terra.workspace.generated.model.ApiCloneControlledFlexibleResourceRequest;
import bio.terra.workspace.generated.model.ApiCloneControlledFlexibleResourceResult;
import bio.terra.workspace.generated.model.ApiCreateControlledFlexibleResourceRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledFlexibleResource;
import bio.terra.workspace.generated.model.ApiFlexibleResource;
import bio.terra.workspace.generated.model.ApiFlexibleResourceUpdateParameters;
import bio.terra.workspace.generated.model.ApiUpdateControlledFlexibleResourceRequestBody;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.FlexResourceCreationParameters;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.CommonUpdateParameters;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
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
  private final WsmResourceService wsmResourceService;

  @Autowired
  public ControlledFlexibleResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      FeatureConfiguration features,
      FeatureService featureService,
      JobService jobService,
      JobApiUtils jobApiUtils,
      ControlledResourceService controlledResourceService,
      ControlledResourceMetadataManager controlledResourceMetadataManager,
      WorkspaceService workspaceService,
      WsmResourceService wsmResourceService) {
    super(
        authenticatedUserRequestFactory,
        request,
        samService,
        features,
        featureService,
        jobService,
        jobApiUtils,
        controlledResourceService,
        controlledResourceMetadataManager,
        workspaceService);
    this.wsmResourceService = wsmResourceService;
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCreatedControlledFlexibleResource> createFlexibleResource(
      UUID workspaceUuid, @Valid ApiCreateControlledFlexibleResourceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest, workspaceUuid, ControllerValidationUtils.getSamAction(body.getCommon()));
    workspaceService.validateWorkspaceState(workspace);

    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            null,
            userRequest,
            WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE);

    byte[] encodedJSON = body.getFlexibleResource().getData();
    String decodedJSON = ControlledFlexibleResource.getDecodedJSONFromByteArray(encodedJSON);

    ControlledFlexibleResource resource =
        ControlledFlexibleResource.builder()
            .common(commonFields)
            .typeNamespace(body.getFlexibleResource().getTypeNamespace())
            .type(body.getFlexibleResource().getType())
            .data(decodedJSON)
            .build();

    FlexResourceCreationParameters creationParameters =
        FlexResourceCreationParameters.fromApiCreationParameters(body.getFlexibleResource());

    ControlledFlexibleResource createdFlexibleResource =
        controlledResourceService
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE);

    var response =
        new ApiCreatedControlledFlexibleResource()
            .resourceId(createdFlexibleResource.getResourceId())
            .flexibleResource(createdFlexibleResource.toApiResource());

    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiFlexibleResource> updateFlexibleResource(
      UUID workspaceUuid,
      UUID resourceUuid,
      @Valid ApiUpdateControlledFlexibleResourceRequestBody body) {
    logger.info(
        "Updating flexible resource; resourceId {} workspaceId {}", resourceUuid, workspaceUuid);
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledFlexibleResource flexibleResource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest,
                workspaceUuid,
                resourceUuid,
                SamConstants.SamControlledResourceActions.EDIT_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE);
    workspaceService.validateWorkspaceState(workspaceUuid);

    // The update parameter for flexible resource is the decoded string form
    // of the base64 byte input.
    ApiFlexibleResourceUpdateParameters updateParameters = body.getUpdateParameters();
    CommonUpdateParameters commonUpdateParameters =
        new CommonUpdateParameters()
            .setName(body.getName())
            .setDescription(body.getDescription())
            .setCloningInstructions(
                StewardshipType.CONTROLLED,
                updateParameters == null ? null : updateParameters.getCloningInstructions());
    byte[] encodedJSON = updateParameters != null ? updateParameters.getData() : null;
    String decodedData = ControlledFlexibleResource.getDecodedJSONFromByteArray(encodedJSON);
    ResourceValidationUtils.validateFlexResourceDataSize(decodedData);

    wsmResourceService.updateResource(
        userRequest, flexibleResource, commonUpdateParameters, decodedData);
    ControlledFlexibleResource updatedResource =
        controlledResourceService
            .getControlledResource(workspaceUuid, resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<Void> deleteFlexibleResource(UUID workspaceUuid, UUID resourceUuid) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest,
        workspaceUuid,
        resourceUuid,
        SamConstants.SamControlledResourceActions.DELETE_ACTION);
    workspaceService.validateWorkspaceState(workspaceUuid);
    logger.info(
        "Deleting controlled flexible resource {} in workspace {}",
        resourceUuid.toString(),
        workspaceUuid.toString());
    controlledResourceService.deleteControlledResourceSync(
        workspaceUuid, resourceUuid, /* forceDelete= */ false, userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiFlexibleResource> getFlexibleResource(
      UUID workspaceUuid, UUID resourceUuid) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledFlexibleResource resource =
        controlledResourceMetadataManager
            .validateWorkspaceOrControlledResourceReadAccess(
                userRequest, workspaceUuid, resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCloneControlledFlexibleResourceResult> cloneFlexibleResource(
      UUID workspaceUuid,
      UUID resourceUuid,
      @Valid ApiCloneControlledFlexibleResourceRequest body) {
    logger.info(
        "Cloning flex resource resourceId {} workspaceUuid {}", resourceUuid, workspaceUuid);
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledFlexibleResource resource =
        controlledResourceMetadataManager
            .validateCloneAction(
                userRequest, workspaceUuid, body.getDestinationWorkspaceId(), resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE);
    CloningInstructions cloningInstructions =
        resource.computeCloningInstructions(body.getCloningInstructions());
    ResourceValidationUtils.validateCloningInstructions(
        StewardshipType.CONTROLLED, cloningInstructions);
    workspaceService.validateWorkspaceState(workspaceUuid);
    workspaceService.validateCloneWorkspaceAndContextState(
        body.getDestinationWorkspaceId(), CloudPlatform.ANY, cloningInstructions);

    // If COPY_NOTHING return an empty result (no-op).
    if (cloningInstructions == CloningInstructions.COPY_NOTHING) {
      ApiCloneControlledFlexibleResourceResult emptyResult =
          new ApiCloneControlledFlexibleResourceResult()
              .effectiveCloningInstructions(CloningInstructions.COPY_NOTHING.toApiModel())
              .sourceResourceId(resourceUuid)
              .sourceWorkspaceId(workspaceUuid)
              .resource(null);
      return new ResponseEntity<>(emptyResult, HttpStatus.OK);
    }

    // Otherwise start a flight to clone the flex resource.
    ControlledFlexibleResource clonedFlexResource =
        controlledResourceService.cloneFlexResource(
            resource,
            body.getDestinationWorkspaceId(),
            UUID.randomUUID(),
            userRequest,
            body.getName(),
            body.getDescription(),
            cloningInstructions);

    ApiCloneControlledFlexibleResourceResult result =
        new ApiCloneControlledFlexibleResourceResult()
            .resource(clonedFlexResource.toApiResource())
            .effectiveCloningInstructions(cloningInstructions.toApiModel())
            .sourceWorkspaceId(workspaceUuid)
            .sourceResourceId(resourceUuid);

    return new ResponseEntity<>(result, HttpStatus.OK);
  }
}
