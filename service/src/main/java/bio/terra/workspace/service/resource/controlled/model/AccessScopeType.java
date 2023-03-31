package bio.terra.workspace.service.resource.controlled.model;

import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.common.exception.SerializationException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.generated.model.ApiAccessScope;
import org.apache.commons.lang3.StringUtils;

/** Controlled resources can be shared by all users in a workspace or be private to a single user */
public enum AccessScopeType {
  ACCESS_SCOPE_SHARED("ACCESS_SHARED", ApiAccessScope.SHARED_ACCESS),
  ACCESS_SCOPE_PRIVATE("ACCESS_PRIVATE", ApiAccessScope.PRIVATE_ACCESS);

  private final String dbString;
  private final ApiAccessScope apiAccessScope;

  AccessScopeType(String dbString, ApiAccessScope apiAccessScope) {
    this.dbString = dbString;
    this.apiAccessScope = apiAccessScope;
  }

  public static AccessScopeType fromApi(ApiAccessScope apiAccessScope) {
    if (apiAccessScope == null) {
      throw new MissingRequiredFieldException("Valid accessScope is required");
    }

    return switch (apiAccessScope) {
      case PRIVATE_ACCESS -> ACCESS_SCOPE_PRIVATE;
      case SHARED_ACCESS -> ACCESS_SCOPE_SHARED;
      default -> throw new InternalLogicException("Unknown API access scope");
    };
  }

  public static AccessScopeType fromSql(String dbString) {
    for (AccessScopeType value : values()) {
      if (StringUtils.equals(value.dbString, dbString)) {
        return value;
      }
    }
    throw new SerializationException(
        "Deserialization failed: no matching access scope type for " + dbString);
  }

  public String toSql() {
    return dbString;
  }

  public ApiAccessScope toApiModel() {
    return apiAccessScope;
  }
}
