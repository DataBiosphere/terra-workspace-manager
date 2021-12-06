package bio.terra.workspace.service.resource.controlled;

import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.common.exception.SerializationException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.generated.model.ApiPrivateResourceState;
import org.apache.commons.lang3.StringUtils;

public enum PrivateResourceState {
  ABANDONED("ABANDONED", ApiPrivateResourceState.ABANDONED),
  ACTIVE("ACTIVE", ApiPrivateResourceState.ACTIVE),
  INITIALIZING("INITIALIZING", ApiPrivateResourceState.INITIALIZING),
  LEGACY("LEGACY", ApiPrivateResourceState.LEGACY);

  private final String dbString;
  private final ApiPrivateResourceState apiState;

  PrivateResourceState(String dbString, ApiPrivateResourceState apiState) {
    this.dbString = dbString;
    this.apiState = apiState;
  }

  public ApiPrivateResourceState toApiModel() {
    return apiState;
  }

  public static PrivateResourceState fromApi(ApiPrivateResourceState apiState) {
    if (apiState == null) {
      throw new MissingRequiredFieldException("Valid ApiPrivateResourceState is required");
    }

    switch (apiState) {
      case ABANDONED:
        return PrivateResourceState.ABANDONED;
      case ACTIVE:
        return PrivateResourceState.ACTIVE;
      case INITIALIZING:
        return PrivateResourceState.INITIALIZING;
      case LEGACY:
        return PrivateResourceState.LEGACY;
      default:
        throw new InternalLogicException("Unknown ApiPrivateResourceState");
    }
  }

  public String toSql() {
    return dbString;
  }

  public static PrivateResourceState fromSql(String dbString) {
    for (PrivateResourceState value : values()) {
      if (StringUtils.equals(value.dbString, dbString)) {
        return value;
      }
    }
    throw new SerializationException(
        "Deserialization failed: no matching private resource state for " + dbString);
  }
}
