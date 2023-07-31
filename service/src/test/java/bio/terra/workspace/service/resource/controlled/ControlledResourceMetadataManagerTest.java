package bio.terra.workspace.service.resource.controlled;

import static bio.terra.workspace.service.resource.model.WsmResourceState.NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.stage.StageService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class ControlledResourceMetadataManagerTest extends BaseUnitTest {

  @MockBean AuthenticatedUserRequest userRequest;

  @MockBean StageService stageService;
  @MockBean ResourceDao resourceDao;
  @MockBean WorkspaceService workspaceService;
  @MockBean ApplicationDao applicationDao;
  @Autowired ControlledResourceMetadataManager controlledResourceMetadataManager;

  private UUID workspaceId;
  private UUID resourceId;
  private ControlledResource controlledResource;

  private final String readAction = SamConstants.SamControlledResourceActions.READ_ACTION;

  @BeforeEach
  void setupMocks() throws InterruptedException {
    workspaceId = UUID.randomUUID();
    resourceId = UUID.randomUUID();

    controlledResource =
        new ControlledAzureStorageContainerResource.Builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(workspaceId)
                    .resourceId(resourceId)
                    .build())
            .storageContainerName("container")
            .build();

    when(resourceDao.getResource(workspaceId, resourceId)).thenReturn(controlledResource);
    doCallRealMethod().when(mockSamService()).checkAuthz(any(), any(), any(), any());
  }

  @Test
  public void testValidateControlledResourceReadAccess_Workspace() throws InterruptedException {
    // User has read permissions on the workspace, but NOT the resource itself.
    // They are still able to see the resource.
    doReturn(true)
        .when(mockSamService())
        .isAuthorized(
            userRequest, SamConstants.SamResource.WORKSPACE, workspaceId.toString(), readAction);
    doReturn(false)
        .when(mockSamService())
        .isAuthorized(
            userRequest,
            controlledResource.getCategory().getSamResourceName(),
            resourceId.toString(),
            readAction);

    ControlledResource resource =
        controlledResourceMetadataManager.validateControlledResourceReadAccess(
            userRequest, workspaceId, resourceId);
    Assertions.assertEquals(controlledResource, resource);
  }

  @Test
  public void testValidateControlledResourceReadAccess_Resource() throws InterruptedException {
    // User does not have read access on the workspace, but does on the resource (which
    // should not happen in practice). Go ahead and return the resource.
    doReturn(false)
        .when(mockSamService())
        .isAuthorized(
            userRequest, SamConstants.SamResource.WORKSPACE, workspaceId.toString(), readAction);
    doReturn(true)
        .when(mockSamService())
        .isAuthorized(
            userRequest,
            controlledResource.getCategory().getSamResourceName(),
            resourceId.toString(),
            readAction);

    ControlledResource resource =
        controlledResourceMetadataManager.validateControlledResourceReadAccess(
            userRequest, workspaceId, resourceId);
    Assertions.assertEquals(controlledResource, resource);
  }

  @Test
  public void testValidateControlledResourceReadAccess_NoAccess() throws InterruptedException {
    // User does not have read access on the workspace, nor the resource.
    doReturn(false)
        .when(mockSamService())
        .isAuthorized(
            userRequest, SamConstants.SamResource.WORKSPACE, workspaceId.toString(), readAction);
    doReturn(false)
        .when(mockSamService())
        .isAuthorized(
            userRequest, SamConstants.SamResource.WORKSPACE, workspaceId.toString(), readAction);
    assertThrows(
        ForbiddenException.class,
        () ->
            controlledResourceMetadataManager.validateControlledResourceReadAccess(
                userRequest, workspaceId, resourceId));
  }

  @Test
  public void testValidateControlledResourceAndAction_Read_HasWorkspaceAccessOnly()
      throws InterruptedException {
    // User has read permissions on the workspace, but NOT the resource itself.
    // This method will throw an error, as opposed to validateControlledResourceReadAccess.
    doReturn(true)
        .when(mockSamService())
        .isAuthorized(
            userRequest, SamConstants.SamResource.WORKSPACE, workspaceId.toString(), readAction);
    doReturn(false)
        .when(mockSamService())
        .isAuthorized(
            userRequest,
            controlledResource.getCategory().getSamResourceName(),
            resourceId.toString(),
            readAction);

    assertThrows(
        ForbiddenException.class,
        () ->
            controlledResourceMetadataManager.validateControlledResourceAndAction(
                userRequest, workspaceId, resourceId, readAction));
  }

  @Test
  public void testValidateControlledResourceAndAction_Read_HasResourceAccess()
      throws InterruptedException {
    // User has read permissions on the resource, method short-circuits and returns the resource.
    doReturn(true)
        .when(mockSamService())
        .isAuthorized(
            userRequest,
            controlledResource.getCategory().getSamResourceName(),
            resourceId.toString(),
            readAction);

    ControlledResource resource =
        controlledResourceMetadataManager.validateControlledResourceAndAction(
            userRequest, workspaceId, resourceId, readAction);
    Assertions.assertEquals(controlledResource, resource);
  }

  @Test
  public void testValidateControlledResourceAndAction_Read_NoResourceAccess()
      throws InterruptedException {
    // User does not have read access on the resource.
    doReturn(false)
        .when(mockSamService())
        .isAuthorized(
            userRequest,
            controlledResource.getCategory().getSamResourceName(),
            resourceId.toString(),
            readAction);

    assertThrows(
        ForbiddenException.class,
        () ->
            controlledResourceMetadataManager.validateControlledResourceAndAction(
                userRequest, workspaceId, resourceId, readAction));
  }

  @Test
  public void testValidateControlledResourceAndAction_Write() {
    String writeAction = SamConstants.SamControlledResourceActions.WRITE_ACTION;

    // On write permissions, resource access is done based on the resource state.
    // Since this controlled resource was created directly without proper state,
    // its `getState` method returns NOT_EXISTS and `validateControlledResourceAndAction` throws
    // an exception.
    Assertions.assertEquals(NOT_EXISTS, controlledResource.getState());
    assertThrows(
        InternalLogicException.class,
        () ->
            controlledResourceMetadataManager.validateControlledResourceAndAction(
                userRequest, workspaceId, resourceId, writeAction));
  }
}
