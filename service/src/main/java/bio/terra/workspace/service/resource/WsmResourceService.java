package bio.terra.workspace.service.resource;

import bio.terra.workspace.db.DbRetryUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.resource.controlled.ControlledResourceCategory;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Support for cross-resource methods */
@Component
public class WsmResourceService {

  private final WorkspaceService workspaceService;
  private final ResourceDao resourceDao;
  private final SamService samService;

  @Autowired
  public WsmResourceService(
      WorkspaceService workspaceService, ResourceDao resourceDao, SamService samService) {
    this.workspaceService = workspaceService;
    this.resourceDao = resourceDao;
    this.samService = samService;
  }

  public List<WsmResource> enumerateResources(
      UUID workspaceId,
      @Nullable WsmResourceType resourceType,
      @Nullable StewardshipType stewardshipType,
      int offset,
      int limit,
      AuthenticatedUserRequest userRequest) {

    // First, we check if the caller has read action on the workspace. If not, we are done. They see
    // nothing!
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);

    // We need to gather the list of visible controlled resources. That entails four calls to Sam:
    // one for each resource category (controlled_user_shared, controlled_user_private,
    // controlled_application_shared, controlled_application_private). Then we can make a single
    // query that will return all referenced resources and only controlled resources filtered by Sam
    // visibility.
    List<String> controlledResourceIds = new ArrayList<>();
    for (ControlledResourceCategory category : ControlledResourceCategory.values()) {
      controlledResourceIds.addAll(
          SamService.rethrowIfSamInterrupted(
              () ->
                  samService.listControlledResourceIds(userRequest, category.getSamResourceName()),
              "listControlledResourceIds"));
    }
    return DbRetryUtils.throwIfInterrupted(
        () ->
            resourceDao.enumerateResources(
                workspaceId, controlledResourceIds, resourceType, stewardshipType, offset, limit));
  }
}
