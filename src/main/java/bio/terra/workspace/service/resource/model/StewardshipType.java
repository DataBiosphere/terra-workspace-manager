package bio.terra.workspace.service.resource.model;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import javax.annotation.Nullable;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.StringUtils;

public enum StewardshipType {
  REFERENCED("REFERENCED", ApiStewardshipType.REFERENCED),
  CONTROLLED("CONTROLLED", ApiStewardshipType.CONTROLLED);

  private final String dbString;
  private final ApiStewardshipType apiType;

  StewardshipType(String dbString, ApiStewardshipType apiType) {
    this.dbString = dbString;
    this.apiType = apiType;
  }

  public static StewardshipType fromSql(String dbString) {
    for (StewardshipType value : values()) {
      if (StringUtils.equals(value.dbString, dbString)) {
        return value;
      }
    }
    throw new SerializationException(
        "Deeserialization failed: no matching stewardship type for " + dbString);
  }

  /**
   * Convert from an optional api type to StewardshipType. This method handles the case where API
   * input is optional/can be null. If the input is null we return null and leave it to the caller
   * to raise any error.
   *
   * @param apiType incoming stewardship type or null
   * @return valid stewardship type; null if input is null
   */
  public static @Nullable StewardshipType fromApiOptional(@Nullable ApiStewardshipType apiType) {
    if (apiType == null) {
      return null;
    }
    for (StewardshipType value : values()) {
      if (value.toApiModel() == apiType) {
        return value;
      }
    }
    throw new ValidationException("Invalid stewardship type: " + apiType);
  }

  public String toSql() {
    return dbString;
  }

  public ApiStewardshipType toApiModel() {
    return apiType;
  }
}
