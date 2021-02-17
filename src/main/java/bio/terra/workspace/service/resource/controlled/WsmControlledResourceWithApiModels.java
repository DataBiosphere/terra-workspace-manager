package bio.terra.workspace.service.resource.controlled;

import java.util.UUID;

public abstract class WsmControlledResourceWithApiModels<T, U> extends WsmControlledResource {
  private final T apiInputModel;

  public WsmControlledResourceWithApiModels(
      String resourceName,
      String description,
      UUID resourceId,
      UUID workspaceId,
      boolean isVisible,
      String associatedApp,
      String owner,
      T apiInputModel) {
    super(resourceName, description, resourceId, workspaceId, isVisible, associatedApp, owner);
    this.apiInputModel = apiInputModel;
  }

  public T getApiInputModel() {
    return apiInputModel;
  }

  public abstract U toOutputApiModel();
}
