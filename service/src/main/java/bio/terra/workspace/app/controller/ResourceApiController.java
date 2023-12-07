package bio.terra.workspace.app.controller;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertApiPropertyToMap;
import static bio.terra.workspace.common.utils.ControllerValidationUtils.validatePropertiesDeleteRequestBody;
import static bio.terra.workspace.common.utils.ControllerValidationUtils.validatePropertiesUpdateRequestBody;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.ResourceApi;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceDescription;
import bio.terra.workspace.generated.model.ApiResourceList;
import bio.terra.workspace.generated.model.ApiResourceMetadata;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class ResourceApiController extends ControllerBase implements ResourceApi {
  private final WsmResourceService resourceService;
  private final WorkspaceService workspaceService;
  private final ReferencedResourceService referencedResourceService;

  @Autowired
  public ResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      FeatureConfiguration features,
      FeatureService featureService,
      JobService jobService,
      JobApiUtils jobApiUtils,
      WsmResourceService resourceService,
      WorkspaceService workspaceService,
      ReferencedResourceService referencedResourceService) {
    super(
        authenticatedUserRequestFactory,
        request,
        samService,
        features,
        featureService,
        jobService,
        jobApiUtils);
    this.resourceService = resourceService;
    this.workspaceService = workspaceService;
    this.referencedResourceService = referencedResourceService;
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiResourceList> enumerateResources(
      UUID workspaceUuid,
      Integer offset,
      Integer limit,
      ApiResourceType resource,
      ApiStewardshipType stewardship) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControllerValidationUtils.validatePaginationParams(offset, limit);
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.READ);

    List<WsmResource> wsmResources =
        resourceService.enumerateResources(
            workspaceUuid,
            WsmResourceFamily.fromApiOptional(resource),
            StewardshipType.fromApiOptional(stewardship),
            offset,
            limit);

    List<ApiResourceDescription> apiResourceDescriptionList =
        wsmResources.stream().map(this::makeApiResourceDescription).collect(Collectors.toList());

    var apiResourceList = new ApiResourceList().resources(apiResourceDescriptionList);
    return new ResponseEntity<>(apiResourceList, HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiResourceDescription> getResource(UUID workspaceUuid, UUID resourceUuid) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.READ);

    WsmResource wsmResource = resourceService.getResource(workspaceUuid, resourceUuid);

    ApiResourceDescription apiResourceDescription = this.makeApiResourceDescription(wsmResource);
    return new ResponseEntity<>(apiResourceDescription, HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<Boolean> checkReferenceAccess(UUID workspaceUuid, UUID resourceUuid) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.READ);
    boolean isValid =
        referencedResourceService.checkAccess(workspaceUuid, resourceUuid, userRequest);
    return new ResponseEntity<>(isValid, HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<Void> updateResourceProperties(
      UUID workspaceUuid, UUID resourceUuid, List<ApiProperty> properties) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.WRITE);
    workspaceService.validateWorkspaceState(workspace);
    validatePropertiesUpdateRequestBody(properties);
    Map<String, String> propertiesMap = convertApiPropertyToMap(properties);
    ResourceValidationUtils.validateProperties(propertiesMap);

    resourceService.updateResourceProperties(workspaceUuid, resourceUuid, propertiesMap);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @WithSpan
  @Override
  public ResponseEntity<Void> deleteResourceProperties(
      UUID workspaceUuid, UUID resourceUuid, List<String> propertyKeys) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    validatePropertiesDeleteRequestBody(propertyKeys);
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.WRITE);
    workspaceService.validateWorkspaceState(workspace);
    resourceService.deleteResourceProperties(workspaceUuid, resourceUuid, propertyKeys);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // Convert a WsmResource into the API format for enumeration
  @VisibleForTesting
  public ApiResourceDescription makeApiResourceDescription(WsmResource wsmResource) {
    ApiResourceMetadata common = wsmResource.toApiMetadata();
    ApiResourceAttributesUnion union = wsmResource.toApiAttributesUnion();
    return new ApiResourceDescription().metadata(common).resourceAttributes(union);
  }
}
