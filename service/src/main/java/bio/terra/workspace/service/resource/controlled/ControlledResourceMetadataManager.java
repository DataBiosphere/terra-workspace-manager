package bio.terra.workspace.service.resource.controlled;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.Rethrow;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.exception.ResourceStateConflictException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.resource.controlled.exception.ResourceIsBusyException;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.stage.StageService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.util.UUID;
import org.apache.commons.codec.binary.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Split out from ControlledResourceService so that it can be called both by services and flights.
 */
@Component
public class ControlledResourceMetadataManager {

  private final StageService stageService;
  private final ResourceDao resourceDao;
  private final SamService samService;
  private final WorkspaceService workspaceService;
  private final ApplicationDao applicationDao;

  public ControlledResourceMetadataManager(
      StageService stageService,
      ResourceDao resourceDao,
      SamService samService,
      WorkspaceService workspaceService,
      ApplicationDao applicationDao) {
    this.stageService = stageService;
    this.resourceDao = resourceDao;
    this.samService = samService;
    this.workspaceService = workspaceService;
    this.applicationDao = applicationDao;
  }

  /**
   * Convenience function that checks existence of a controlled resource within a workspace,
   * followed by a read authorization check against the workspace (if the user does not have read
   * access against the workspace, a read access check against the resource will be done).
   *
   * <p>This method differs from `validateControlledResourceAndAction` in that this method will
   * allow read access to a resource if the user has access to the workspace, even if the user does
   * not have read access to the resource itself.
   *
   * <p>Throws ResourceNotFound from getResource if the resource does not exist in the specified
   * workspace, regardless of the user's permission.
   *
   * <p>Throws InvalidControlledResourceException if the given resource is not controlled.
   *
   * <p>Throws ForbiddenException if the user does not have read access on the workspace nor the
   * resource.
   *
   * @param userRequest the user's authenticated request
   * @param workspaceUuid id of the workspace this resource exists in
   * @param resourceId id of the resource in question
   * @return validated resource
   */
  public ControlledResource validateWorkspaceOrControlledResourceReadAccess(
      AuthenticatedUserRequest userRequest, UUID workspaceUuid, UUID resourceId) {
    String readAction = SamControlledResourceActions.READ_ACTION;
    stageService.assertMcWorkspace(workspaceUuid, readAction);
    WsmResource resource = resourceDao.getResource(workspaceUuid, resourceId);

    // Everyone who is a reader (or above) on the workspace can see all the resources in a
    // workspace. Thus we return the resource if the user has read access on the workspace,
    // and only check permissions on the resource itself if the user does not have read
    // access on the workspace (which should not happen given that it is a controlled resource).
    try {
      checkResourceAuthz(
          userRequest, SamConstants.SamResource.WORKSPACE, workspaceUuid, readAction);
    } catch (ForbiddenException exception) {
      ControlledResource controlledResource = resource.castToControlledResource();
      String samName = controlledResource.getCategory().getSamResourceName();
      checkResourceAuthz(userRequest, samName, resourceId, readAction);
    }

    return resource.castToControlledResource();
  }

  /**
   * Convenience function that checks existence of a controlled resource within a workspace,
   * followed by an authorization check against that resource. This method verifies that the user
   * has access on the specific resource (even for read access, so it is stricter in the case of
   * read access than `validateWorkspaceOrControlledResourceReadAccess`).
   *
   * <p>Throws ResourceNotFound from getResource if the resource does not exist in the specified
   * workspace, regardless of the user's permission.
   *
   * <p>Throws InvalidControlledResourceException if the given resource is not controlled.
   *
   * <p>Throws ForbiddenException if the user is not permitted to perform the specified action on
   * the resource in question.
   *
   * <p>Throws ResourceIsBusyException if the user attempts an update or delete operation, while the
   * resource is being created, updated, or deleted.
   *
   * <p>Throws ResourceStateConflictException if the resource is broken and the user attempts an
   * update operation. The only valid operations are read and delete.
   *
   * @param userRequest the user's authenticated request
   * @param workspaceUuid id of the workspace this resource exists in
   * @param resourceId id of the resource in question
   * @param action the action to authorize against the resource
   * @return validated resource
   */
  @WithSpan
  public ControlledResource validateControlledResourceAndAction(
      AuthenticatedUserRequest userRequest, UUID workspaceUuid, UUID resourceId, String action) {
    stageService.assertMcWorkspace(workspaceUuid, action);
    WsmResource resource = resourceDao.getResource(workspaceUuid, resourceId);
    ControlledResource controlledResource = resource.castToControlledResource();
    String samName = controlledResource.getCategory().getSamResourceName();

    if (StringUtils.equals(action, SamControlledResourceActions.READ_ACTION)) {
      checkResourceAuthz(userRequest, samName, resourceId, action);
      return controlledResource;
    }

    // For non-read cases, test the resource state
    switch (controlledResource.getState()) {
      case READY:
        // Primary success case: check auth and return the resource
        checkResourceAuthz(userRequest, samName, resourceId, action);
        return controlledResource;

      case CREATING, DELETING, UPDATING:
        // Check forbidden before throwing the busy resource error
        checkResourceAuthz(userRequest, samName, resourceId, action);
        throw new ResourceIsBusyException(
            "Another operation is running on the resource; try again later");

      case BROKEN:
        if (!SamControlledResourceActions.DELETE_ACTION.equals(action)) {
          throw new ResourceStateConflictException(
              "Delete is the only operation allowed on a resource in the broken state");
        }
        // For a resource in the BROKEN state, then there may be no Sam resource. We do our
        // best with what we know. We handle two cases:
        //  1. If the resource is an application resource, and the application is making
        //     the request, we go ahead and perform the operation.
        //  2. If the resource is a user resource, we test that the user has delete action
        //     on the workspace.
        if (controlledResource.getManagedBy() == ManagedByType.MANAGED_BY_APPLICATION) {
          WsmApplication application =
              applicationDao.getApplication(controlledResource.getApplicationId());
          String callerEmail = samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest);
          if (!StringUtils.equals(application.getServiceAccount(), callerEmail)) {
            throw new ForbiddenException(
                "Only the associated application "
                    + application.getDisplayName()
                    + "can delete a broken application resource");
          }
        } else {
          // Broken user resource. The Sam resource for this resource may not exist.
          // Either it failed to get created or we undid it on the way out of the
          // flight. So we base the authz check on the user's permission on the workspace.
          Rethrow.onInterrupted(
              () ->
                  samService.checkAuthz(
                      userRequest,
                      SamConstants.SamResource.WORKSPACE,
                      workspaceUuid.toString(),
                      SamConstants.SamWorkspaceAction.DELETE),
              "checkAuthz");
        }
        return controlledResource;

      default:
        throw new InternalLogicException("Unexpected case: " + controlledResource.getState());
    }
  }

  private void checkResourceAuthz(
      AuthenticatedUserRequest userRequest, String samName, UUID resourceId, String action) {
    Rethrow.onInterrupted(
        () -> samService.checkAuthz(userRequest, samName, resourceId.toString(), action),
        "checkAuthz");
  }

  /**
   * A special case of {@code validateControlledResourceAndAction} for cloning operations.
   *
   * <p>Unlike most operations, in order to clone we need to validate both that the user has read
   * permission on the source resource and create-controlled-resource permission on the destination
   * workspace.
   *
   * @param userRequest the user's authenticated request
   * @param sourceWorkspaceUuid id of the workspace this resource exists in
   * @param destinationWorkspaceUuid id of the workspace this resource is being cloned to
   * @param resourceId id of the resource in question
   * @return validated resource
   */
  @WithSpan
  public ControlledResource validateCloneAction(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceUuid,
      UUID destinationWorkspaceUuid,
      UUID resourceId) {
    ControlledResource validatedResource =
        validateControlledResourceAndAction(
            userRequest, sourceWorkspaceUuid, resourceId, SamControlledResourceActions.READ_ACTION);
    // validateControlledResourceAndAction validates this is an MC_WORKSPACE stage workspace, so
    // we don't need to duplicate that here.
    workspaceService.validateWorkspaceAndAction(
        userRequest,
        destinationWorkspaceUuid,
        validatedResource.getCategory().getSamCreateResourceAction());
    return validatedResource;
  }
}
