package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.service.resource.StewardshipType;
import bio.terra.workspace.service.resource.WsmResource;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.UUID;

public abstract class WsmControlledResource<API_MODEL_T> extends WsmResource {

  public WsmControlledResource(
      String resourceName,
      String description,
      UUID resourceId,
      UUID workspaceId,
      boolean isVisible,
      String associatedApp) {
    super(resourceName, description, resourceId, workspaceId, isVisible, associatedApp);
  }

  @Override
  public StewardshipType getStewardshipType() {
    return StewardshipType.CONTROLLED_RESOURCE;
  }

  public abstract CloudPlatform getCloudPlatform();

  public abstract WsmResourceType getResourceType();

  public ControlledResourceDbModel getDbModel() {
    return ControlledResourceDbModel.builder()
        .setResourceId(getResourceId())
        .setWorkspaceId(getWorkspaceId())
        .setAssociatedApp(getAssociatedApp())
        .setIsVisible(isVisible())
        .setAttributes(getJsonAttributes())
        .build();
  }

  public abstract String getJsonAttributes();

  public abstract API_MODEL_T toOutputApiModel();
}
