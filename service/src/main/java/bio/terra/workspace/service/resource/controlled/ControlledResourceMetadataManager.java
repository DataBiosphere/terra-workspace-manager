package bio.terra.workspace.service.resource.controlled;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.exception.ResourceIsBusyException;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.stage.StageService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.UUID;
import javax.annotation.Nullable;
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
   * Update the name and description metadata fields of a controlled resource. These are only stored
   * inside WSM, so this does not require any calls to clouds.
   *
   * @param workspaceUuid workspace of interest
   * @param resourceId resource to update
   * @param name name to change - may be null, in which case resource name will not be changed.
   * @param description description to change - may be null, in which case resource description will
   *     not be changed.
   */
  public void updateControlledResourceMetadata(
      UUID workspaceUuid,
      UUID resourceId,
      @Nullable String name,
      @Nullable String description,
      @Nullable CloningInstructions cloningInstructions) {
    // Name may be null if the user is not updating it in this request.
    if (name != null) {
      ResourceValidationUtils.validateResourceName(name);
    }
    // Description may also be null, but this validator accepts null descriptions.
    ResourceValidationUtils.validateResourceDescriptionName(description);
    resourceDao.updateResource(
        workspaceUuid, resourceId, name, description, null, cloningInstructions);
  }

  /**
   * Convenience function that checks existence of a controlled resource within a workspace,
   * followed by an authorization check against that resource.
   *
   * <p>Throws ResourceNotFound from getResource if the resource does not exist in the specified
   * workspace, regardless of the user's permission.
   *
   * <p>Throws InvalidControlledResourceException if the given resource is not controlled.
   *
   * <p>Throws ForbiddenException if the user is not permitted to perform the specified action on
   * the resource in question.
   *
   * @param userRequest the user's authenticated request
   * @param workspaceUuid id of the workspace this resource exists in
   * @param resourceId id of the resource in question
   * @param action the action to authorize against the resource
   * @return validated resource
   */
  @Traced
  public ControlledResource validateControlledResourceAndAction(
      AuthenticatedUserRequest userRequest, UUID workspaceUuid, UUID resourceId, String action) {
    stageService.assertMcWorkspace(workspaceUuid, action);
    WsmResource resource = resourceDao.getResource(workspaceUuid, resourceId);
    ControlledResource controlledResource = resource.castToControlledResource();
    String samName = controlledResource.getCategory().getSamResourceName();

    // Every case exits the validation
    switch (controlledResource.getState()) {
      case READY:
        // Primary success case: check auth and return the resource
        SamRethrow.onInterrupted(
            () -> samService.checkAuthz(userRequest, samName, resourceId.toString(), action),
            "checkAuthz");
        return controlledResource;

      case CREATING, DELETING, UPDATING:
        // Check forbidden before throwing the busy resource error
        SamRethrow.onInterrupted(
            () -> samService.checkAuthz(userRequest, samName, resourceId.toString(), action),
            "checkAuthz");
        throw new ResourceIsBusyException(
            "Another operation is running on the resource; wait and try again");

      case BROKEN:
        // If the resource is in the BROKEN state, then there may be no Sam resource. We do our
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
          // Broken user resource. The Sam resource for this resource will not exist.
          // Either it failed to get created or we undid it on the way out of the
          // flight. So we base the authz check on the user's permission on the workspace.
          SamRethrow.onInterrupted(
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
  @Traced
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
