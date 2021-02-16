package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.service.resource.StewardshipType;
import bio.terra.workspace.service.resource.WsmResource;
import java.util.UUID;

public abstract class WsmControlledResource<API_IN_T, API_OUT_T> extends WsmResource {

  private final API_IN_T apiInputModel;

  public WsmControlledResource(
      String resourceName,
      String description,
      UUID resourceId,
      UUID workspaceId,
      boolean isVisible,
      String associatedApp,
      API_IN_T apiInputModel,
      String owner) {
    super(resourceName, description, resourceId, workspaceId, isVisible, associatedApp, owner);
    this.apiInputModel = apiInputModel;
  }

  @Override
  public StewardshipType getStewardshipType() {
    return StewardshipType.CONTROLLED_RESOURCE;
  }

  public abstract CloudPlatform getCloudPlatform();

  public abstract WsmResourceType getResourceType();

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

  public API_IN_T getApiInputModel() {
    return apiInputModel;
  }

  public abstract API_OUT_T toOutputApiModel();
}
