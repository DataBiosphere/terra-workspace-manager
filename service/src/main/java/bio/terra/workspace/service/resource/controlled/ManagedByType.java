package bio.terra.workspace.service.resource.controlled;

import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.common.exception.SerializationException;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import org.apache.commons.lang3.StringUtils;

/** Controlled resources can be managed by a user or by an application */
public enum ManagedByType {
  MANAGED_BY_USER("USER", ApiManagedBy.USER),
  MANAGED_BY_APPLICATION("APPLICATION", ApiManagedBy.APPLICATION);

  private final String dbString;
  private final ApiManagedBy apiManagedBy;

  ManagedByType(String dbString, ApiManagedBy apiManagedBy) {
    this.dbString = dbString;
    this.apiManagedBy = apiManagedBy;
  }

  public String toSql() {
    return dbString;
  }

  public ApiManagedBy toApiModel() {
    return apiManagedBy;
  }

  public static ManagedByType fromApi(ApiManagedBy apiManagedBy) {
    if (apiManagedBy == null) {
      throw new MissingRequiredFieldException("Valid managedBy is required");
    }

    switch (apiManagedBy) {
      case USER:
        return ManagedByType.MANAGED_BY_USER;
      case APPLICATION:
        return ManagedByType.MANAGED_BY_APPLICATION;
      default:
        throw new InternalLogicException("Unknown managedBy");
    }
  }

  public static ManagedByType fromSql(String dbString) {
    for (ManagedByType value : values()) {
      if (StringUtils.equals(value.dbString, dbString)) {
        return value;
      }
    }
    throw new SerializationException(
        "Deeserialization failed: no matching managed by type for " + dbString);
  }
}
