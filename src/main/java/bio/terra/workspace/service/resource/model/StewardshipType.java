package bio.terra.workspace.service.resource.model;

import bio.terra.workspace.common.exception.ValidationException;
import bio.terra.workspace.generated.model.ApiStewardshipType;
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

  public String toSql() {
    return dbString;
  }

  public ApiStewardshipType toApiModel() {
    return apiType;
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

  public static StewardshipType fromApi(ApiStewardshipType apiType) {
    for (StewardshipType value : values()) {
      if (value.toApiModel() == apiType) {
        return value;
      }
    }
    throw new ValidationException("Invalid stewardship type: " + apiType);
  }
}
