package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.service.resource.ResourceInput;
import bio.terra.workspace.service.resource.StewardshipType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Class for all controlled resource fields that are not common to all resource stewardship types
 * and are not specific to any particular resource type.
 */
public abstract class ControlledResource extends ResourceInput {
  public ControlledResource(
      String resourceName,
      String description,
      UUID workspaceId,
      boolean isVisible,
      String owner) {
    super(resourceName, description, workspaceId, isVisible, owner);
  }

  @Override
  public StewardshipType getStewardshipType() {
    return StewardshipType.CONTROLLED_RESOURCE;
  }

  public abstract CloudPlatform getCloudPlatform();

  public abstract WsmResourceType getResourceType();

  /**
   * Generate a model suitable for serialization into the workspace_resource table, via the
   * ControlledResourceDao. Note that this method should not be called before the resource ID has
   * been created and set, as it is the primary key for this table.
   *
   * @return model to be saved in the database.
   */
  public ControlledResourceDbModel toDbModel(UUID resourceId) {
    return ControlledResourceDbModel.builder()
        .setResourceId(resourceId)
        .setWorkspaceId(getWorkspaceId())
        .setAssociatedApp(getAssociatedApp())
        .setIsVisible(isVisible())
        .setOwner(getOwner())
        .setAttributes(getJsonAttributes())
        .build();
  }

  /**
   * Attributes string, serialized as JSON. Includes only those attributes of the cloud resource
   * that are necessary for identification.
   *
   * @return json string
   */
  public abstract String getJsonAttributes();
}
