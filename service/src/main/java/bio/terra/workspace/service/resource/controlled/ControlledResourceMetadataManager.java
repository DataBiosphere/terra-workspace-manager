package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.stage.StageService;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.stereotype.Component;

/**
 * Split out from ControlledResourceService so that it can be called both by services and flights.
 */
@Component
public class ControlledResourceMetadataManager {

  private final StageService stageService;
  private final ResourceDao resourceDao;
  private final SamService samService;

  public ControlledResourceMetadataManager(
      StageService stageService, ResourceDao resourceDao, SamService samService) {
    this.stageService = stageService;
    this.resourceDao = resourceDao;
    this.samService = samService;
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
      @Nullable CloningInstructions cloningInstructions,
      AuthenticatedUserRequest userRequest) {
    stageService.assertMcWorkspace(workspaceId, "updateControlledResource");
    validateControlledResourceAndAction(
        userRequest, workspaceId, resourceId, SamControlledResourceActions.EDIT_ACTION);
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
   * <p>Throws ResourceNotFound from getResource if the workspace does not exist in the specified
   * workspace, regardless of the user's permission.
   *
   * <p>Throws InvalidControlledResourceException if the given resource is not controlled.
   *
   * <p>Throws ForbiddenException if the user is not permitted to perform the specified action on
   * the resource in question.
   *
   * @param userRequest the user's authenticated request
   * @param workspaceUuid if of the workspace this resource exists in
   * @param resourceId id of the resource in question
   * @param action the action to authorize against the resource
   * @return validated resource
   */
  @Traced
  public WsmResource validateControlledResourceAndAction(
      AuthenticatedUserRequest userRequest, UUID workspaceUuid, UUID resourceId, String action) {
    WsmResource resource = resourceDao.getResource(workspaceUuid, resourceId);
    ControlledResource controlledResource = resource.castToControlledResource();
    SamRethrow.onInterrupted(
        () ->
            samService.checkAuthz(
                userRequest,
                controlledResource.getCategory().getSamResourceName(),
                resourceId.toString(),
                action),
        "checkAuthz");
    return resource;
  }
}
