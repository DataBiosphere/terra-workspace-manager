package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.exception.SerializationException;
import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import org.apache.commons.lang3.StringUtils;

/** Controlled resources can be shared by all users in a workspace or be private to a single user */
public enum AccessScopeType {
  ACCESS_SCOPE_SHARED("ACCESS_SHARED"),
  ACCESS_SCOPE_PRIVATE("ACCESS_PRIVATE");

  private final String dbString;

  AccessScopeType(String dbString) {
    this.dbString = dbString;
  }

  public String toSql() {
    return dbString;
  }

  public static AccessScopeType fromApi(
      ApiControlledResourceCommonFields.AccessScopeEnum apiAccessScope) {
    if (apiAccessScope == null) {
      throw new MissingRequiredFieldException("Valid accessScope is required");
    }

    switch (apiAccessScope) {
      case PRIVATE_ACCESS:
        return ACCESS_SCOPE_PRIVATE;
      case SHARED_ACCESS:
        return ACCESS_SCOPE_SHARED;
      default:
        throw new InternalLogicException("Unknown API access scope");
    }
  }

  public static AccessScopeType fromSql(String dbString) {
    for (AccessScopeType value : values()) {
      if (StringUtils.equals(value.dbString, dbString)) {
        return value;
      }
    }
    throw new SerializationException(
        "Deeserialization failed: no matching access scope type for " + dbString);
  }
}
