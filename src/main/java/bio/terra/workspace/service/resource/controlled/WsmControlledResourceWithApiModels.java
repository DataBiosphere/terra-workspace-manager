package bio.terra.workspace.service.resource.controlled;

import java.util.UUID;

/**
 * Intermediate controlled resource class specialized for input and output API model types. This is
 * the lowest-level non-type-specific class in the hierarchy. It was split out from
 * WsmControlledResource to allow that class's use where appropriate without specifying template
 * arguments.
 *
 * @param <T> input API model type. This class stores an instance of this type for use in flights
 * @param <U> output (response) API model type. Conversion must be specified in a subclass
 */
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

  /**
   * Create an instance of the response API model. Calling this method too early could be
   * problematic. TODO: come up with a tighter policy here; there's semantic coupling going on, as
   * this instance exists before all outupts are necessarily known.
   *
   * @return API model object
   */
  public abstract U toOutputApiModel();
}
