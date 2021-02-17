package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.service.resource.StewardshipType;
import bio.terra.workspace.service.resource.WsmResource;
import java.util.UUID;

/**
 * Class for all controlled resource fields that are not common to all resource stewardship types
 * and are not specific to any particular resource type.
 */
public abstract class WsmControlledResource extends WsmResource {

  public WsmControlledResource(
      String resourceName,
      String description,
      UUID resourceId,
      UUID workspaceId,
      boolean isVisible,
      String associatedApp,
      String owner) {
    super(resourceName, description, resourceId, workspaceId, isVisible, associatedApp, owner);
  }

  @Override
  public StewardshipType getStewardshipType() {
    return StewardshipType.CONTROLLED_RESOURCE;
  }

  public abstract CloudPlatform getCloudPlatform();

  public abstract WsmResourceType getResourceType();

  /**
   * Generate a model suitable for serialization into the workspace_resource table, via the
   * ControlledResourceDao.
   *
   * @return
   */
  public ControlledResourceDbModel toDbModel() {
    return ControlledResourceDbModel.builder()
        .setResourceId(
            getResourceId()
                .orElseThrow(
                    () ->
                        new IllegalStateException("Required field Resource ID has not been set.")))
        .setWorkspaceId(getWorkspaceId())
        .setAssociatedApp(getAssociatedApp())
        .setIsVisible(isVisible())
        .setOwner(getOwner())
        .setAttributes(getJsonAttributes())
        .build();
  }

  public abstract String getJsonAttributes();
}
