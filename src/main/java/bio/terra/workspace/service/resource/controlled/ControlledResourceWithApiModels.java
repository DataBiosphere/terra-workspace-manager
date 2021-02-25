package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.service.datareference.model.CloningInstructions;
import java.util.Objects;
import java.util.UUID;

/**
 * Intermediate controlled resource class specialized for input and output API model types. This is
 * the lowest-level non-type-specific class in the hierarchy. It was split out from
 * ControlledResource to allow that class's use where appropriate without specifying template
 * arguments.
 *
 * @param <T> input API model type. This class stores an instance of this type for use in flights
 * @param <U> output (response) API model type. Conversion must be specified in a subclass
 */
public abstract class ControlledResourceWithApiModels<T, U> extends ControlledResource {
  private final T apiInputModel;

  public ControlledResourceWithApiModels(
      String resourceName,
      CloningInstructions cloningInstructions,
      String description,
      UUID workspaceId,
      String owner,
      T apiInputModel) {
    super(resourceName, cloningInstructions, description, workspaceId, owner);
    this.apiInputModel = apiInputModel;
  }

  public T getApiInputModel() {
    return apiInputModel;
  }

  /**
   * Create an instance of the response API model.
   *
   * @return API model object
   */
  public abstract U toOutputApiModel();

  @Override
  public void validate() {
    super.validate();
    if (getApiInputModel() == null || toOutputApiModel() == null) {
      throw new IllegalStateException(
          "Missing required field for ControlledResourceWithApiModels.");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ControlledResourceWithApiModels)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    ControlledResourceWithApiModels<?, ?> that = (ControlledResourceWithApiModels<?, ?>) o;
    return Objects.equals(getApiInputModel(), that.getApiInputModel());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), getApiInputModel());
  }
}
