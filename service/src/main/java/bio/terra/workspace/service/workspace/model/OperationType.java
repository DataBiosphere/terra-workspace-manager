package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.generated.model.ApiOperationType;
import javax.annotation.Nullable;

/**
 * Operation type - corollary to the ApiOperationType.
 */
public enum OperationType {
  CLONE(ApiOperationType.CLONE),
  CREATE(ApiOperationType.CREATE),
  DELETE(ApiOperationType.DELETE),
  UPDATE(ApiOperationType.UPDATE),
  UNKNOWN(ApiOperationType.UNKNOWN);

  private final ApiOperationType apiOperationType;

  OperationType(ApiOperationType apiOperationType) {
    this.apiOperationType = apiOperationType;
  }

  /**
   * Convert from an optional api type to OperationType. This method handles the case where the
   * API input is optional/can be null. If the input is null we return null and leave it to
   * caller to raise any error.
   *
   * @param apiOperationType incoming operation type or null
   * @return valid operation type; null if input is null
   */
  public static @Nullable OperationType fromApiOptional(
      @Nullable ApiOperationType apiOperationType) {
    if (apiOperationType == null) {
      return null;
    }
    for (OperationType value : values()) {
      if (value.toApiModel() == apiOperationType) {
        return value;
      }
    }
    throw new ValidationException("Invalid operation type " + apiOperationType);
  }

  /**
   * Translate an operation type to the API veraion
   * @return an API operation type
   */
  public ApiOperationType toApiModel() {
    return apiOperationType;
  }

}
