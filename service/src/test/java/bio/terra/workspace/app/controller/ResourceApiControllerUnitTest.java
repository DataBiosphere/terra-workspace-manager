package bio.terra.workspace.app.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.annotations.Unit;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceMetadata;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;

@Unit
@ExtendWith(MockitoExtension.class)
public class ResourceApiControllerUnitTest {

  @Mock AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  @Mock HttpServletRequest request;
  @Mock SamService samService;
  @Mock FeatureConfiguration features;
  @Mock FeatureService featureService;
  @Mock JobService jobService;
  @Mock JobApiUtils jobApiUtils;
  @Mock WsmResourceService resourceService;
  @Mock WorkspaceService workspaceService;
  @Mock ReferencedResourceService referencedResourceService;
  @Mock ControlledResourceMetadataManager controlledResourceMetadataManager;
  @Mock AuthenticatedUserRequest userRequest;

  @BeforeEach
  void setup() {
    when(authenticatedUserRequestFactory.from(request)).thenReturn(userRequest);
  }

  ResourceApiController getApiController() {
    return new ResourceApiController(
        authenticatedUserRequestFactory,
        request,
        samService,
        features,
        featureService,
        jobService,
        jobApiUtils,
        resourceService,
        workspaceService,
        referencedResourceService,
        controlledResourceMetadataManager);
  }

  @Test
  void getResourceByName_returnsResourceIfUserCanAccessEntireWorkspace() {
    var workspaceId = UUID.randomUUID();
    Workspace workspace = mock();
    WsmResource resource = mock();
    when(workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceId, SamConstants.SamControlledResourceActions.READ_ACTION))
        .thenReturn(workspace);
    var resourceName = "test-resource-name";
    when(resourceService.getResourceByName(workspaceId, resourceName)).thenReturn(resource);
    ApiResourceMetadata resourceMetadata = mock();
    when(resource.toApiMetadata()).thenReturn(resourceMetadata);
    ApiResourceAttributesUnion resourceAttributes = mock();
    when(resource.toApiAttributesUnion()).thenReturn(resourceAttributes);

    // methods to check access on resources aren't set up here,
    // so any calls to them should just throw an incomplete stubbing exception
    var api = getApiController();
    var apiResponse = api.getResourceByName(workspaceId, resourceName);

    assertEquals(HttpStatusCode.valueOf(200), apiResponse.getStatusCode());
    var resourceDesciption = apiResponse.getBody();
    assertEquals(resourceAttributes, resourceDesciption.getResourceAttributes());
    assertEquals(resourceMetadata, resourceDesciption.getMetadata());
  }

  @Test
  void getResourceByName_returnsControlledResourceForNoWorkspaceAccessButHasResourceAccess() {
    var workspaceId = UUID.randomUUID();
    ControlledResource resource = mock();
    var wsAccessException = new ForbiddenException("no workspace access");
    doThrow(wsAccessException)
        .when(workspaceService)
        .validateWorkspaceAndAction(
            userRequest, workspaceId, SamConstants.SamControlledResourceActions.READ_ACTION);

    var resourceName = "test-resource-name";
    when(resourceService.getResourceByName(workspaceId, resourceName)).thenReturn(resource);
    var resourceId = UUID.randomUUID();
    when(resource.getResourceId()).thenReturn(resourceId);
    when(resource.getStewardshipType()).thenReturn(StewardshipType.CONTROLLED);
    when(controlledResourceMetadataManager.validateControlledResourceAndAction(
            userRequest,
            workspaceId,
            resourceId,
            SamConstants.SamControlledResourceActions.READ_ACTION))
        .thenReturn(resource);

    ApiResourceMetadata resourceMetadata = mock();
    when(resource.toApiMetadata()).thenReturn(resourceMetadata);
    ApiResourceAttributesUnion resourceAttributes = mock();
    when(resource.toApiAttributesUnion()).thenReturn(resourceAttributes);

    var api = getApiController();
    var apiResponse = api.getResourceByName(workspaceId, resourceName);

    assertEquals(HttpStatusCode.valueOf(200), apiResponse.getStatusCode());
    var resourceDesciption = apiResponse.getBody();
    assertEquals(resourceAttributes, resourceDesciption.getResourceAttributes());
    assertEquals(resourceMetadata, resourceDesciption.getMetadata());
    verify(controlledResourceMetadataManager)
        .validateControlledResourceAndAction(
            userRequest,
            workspaceId,
            resourceId,
            SamConstants.SamControlledResourceActions.READ_ACTION);
  }

  @Test
  void getResourceByName_throwsOriginalWorkspaceExceptionForNoAccessToControlledResource() {
    var workspaceId = UUID.randomUUID();
    ControlledResource resource = mock();
    var wsAccessException = new ForbiddenException("no workspace access");
    doThrow(wsAccessException)
        .when(workspaceService)
        .validateWorkspaceAndAction(
            userRequest, workspaceId, SamConstants.SamControlledResourceActions.READ_ACTION);
    var resourceName = "test-resource-name";
    when(resourceService.getResourceByName(workspaceId, resourceName)).thenReturn(resource);
    var resourceId = UUID.randomUUID();
    when(resource.getResourceId()).thenReturn(resourceId);
    when(resource.getStewardshipType()).thenReturn(StewardshipType.CONTROLLED);
    var resourceException = new ForbiddenException("no resource access");
    doThrow(resourceException)
        .when(controlledResourceMetadataManager)
        .validateControlledResourceAndAction(
            userRequest,
            workspaceId,
            resourceId,
            SamConstants.SamControlledResourceActions.READ_ACTION);

    var api = getApiController();
    var thrownException =
        assertThrows(
            ForbiddenException.class, () -> api.getResourceByName(workspaceId, resourceName));

    assertEquals(wsAccessException, thrownException);
    verify(controlledResourceMetadataManager)
        .validateControlledResourceAndAction(
            userRequest,
            workspaceId,
            resourceId,
            SamConstants.SamControlledResourceActions.READ_ACTION);
  }

  @Test
  void getResourceByName_returnsReferencedResourceForNoWorkspaceAccessButHasResourceAccess() {
    var workspaceId = UUID.randomUUID();
    WsmResource resource = mock();
    var wsAccessException = new ForbiddenException("no workspace access");
    doThrow(wsAccessException)
        .when(workspaceService)
        .validateWorkspaceAndAction(
            userRequest, workspaceId, SamConstants.SamControlledResourceActions.READ_ACTION);
    var resourceName = "test-resource-name";
    when(resourceService.getResourceByName(workspaceId, resourceName)).thenReturn(resource);
    var resourceId = UUID.randomUUID();
    when(resource.getResourceId()).thenReturn(resourceId);
    when(resource.getStewardshipType()).thenReturn(StewardshipType.REFERENCED);
    when(referencedResourceService.checkAccess(workspaceId, resourceId, userRequest))
        .thenReturn(true);
    ApiResourceMetadata resourceMetadata = mock();
    when(resource.toApiMetadata()).thenReturn(resourceMetadata);
    ApiResourceAttributesUnion resourceAttributes = mock();
    when(resource.toApiAttributesUnion()).thenReturn(resourceAttributes);

    var api = getApiController();
    var apiResponse = api.getResourceByName(workspaceId, resourceName);

    assertEquals(HttpStatusCode.valueOf(200), apiResponse.getStatusCode());
    var resourceDesciption = apiResponse.getBody();
    assertEquals(resourceAttributes, resourceDesciption.getResourceAttributes());
    assertEquals(resourceMetadata, resourceDesciption.getMetadata());
    verify(referencedResourceService).checkAccess(workspaceId, resourceId, userRequest);
  }

  @Test
  void getResourceByName_throwsOriginalWorkspaceExceptionForNoAccessToReferencedResource() {
    var workspaceId = UUID.randomUUID();
    WsmResource resource = mock();
    var wsAccessException = new ForbiddenException("no workspace access");
    doThrow(wsAccessException)
        .when(workspaceService)
        .validateWorkspaceAndAction(
            userRequest, workspaceId, SamConstants.SamControlledResourceActions.READ_ACTION);
    var resourceName = "test-resource-name";
    when(resourceService.getResourceByName(workspaceId, resourceName)).thenReturn(resource);
    var resourceId = UUID.randomUUID();
    when(resource.getResourceId()).thenReturn(resourceId);
    when(resource.getStewardshipType()).thenReturn(StewardshipType.REFERENCED);
    when(referencedResourceService.checkAccess(workspaceId, resourceId, userRequest))
        .thenReturn(false);

    var api = getApiController();
    var thrownException =
        assertThrows(
            ForbiddenException.class, () -> api.getResourceByName(workspaceId, resourceName));

    assertEquals(wsAccessException, thrownException);
    verify(referencedResourceService).checkAccess(workspaceId, resourceId, userRequest);
  }
}
