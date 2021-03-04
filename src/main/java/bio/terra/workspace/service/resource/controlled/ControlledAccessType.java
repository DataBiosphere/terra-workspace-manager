package bio.terra.workspace.service.resource.controlled;

import static bio.terra.workspace.generated.model.ControlledResourceCommonFields.AccessScopeEnum.PRIVATE_ACCESS;
import static bio.terra.workspace.generated.model.ControlledResourceCommonFields.AccessScopeEnum.SHARED_ACCESS;
import static bio.terra.workspace.generated.model.ControlledResourceCommonFields.ManagedByEnum.APPLICATION;
import static bio.terra.workspace.generated.model.ControlledResourceCommonFields.ManagedByEnum.USER;

import bio.terra.workspace.generated.model.ControlledResourceCommonFields;
import bio.terra.workspace.generated.model.ControlledResourceCommonFields.ManagedByEnum;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.StringUtils;

public enum ControlledAccessType {
  USER_SHARED("USER_SHARED"),
  USER_PRIVATE("USER_PRIVATE"),
  APP_SHARED("APP_SHARED"),
  APP_PRIVATE("APP_PRIVATE");

  private final String dbString;

  ControlledAccessType(String dbString) {
    this.dbString = dbString;
  }

  public String toSql() {
    return dbString;
  }

  public static ControlledAccessType fromApi(
      ControlledResourceCommonFields.AccessScopeEnum accessScope, ManagedByEnum managedBy) {
    if (accessScope == SHARED_ACCESS) {
      if (managedBy == USER) {
        return USER_SHARED;
      } else if (managedBy == APPLICATION) {
        return APP_SHARED;
      }
    } else if (accessScope == PRIVATE_ACCESS) {
      if (managedBy == USER) {
        return USER_PRIVATE;
      } else if (managedBy == APPLICATION) {
        return APP_PRIVATE;
      }
    }
    throw new InternalLogicException("Unknown accessScope or managedBy");
  }

  public static ControlledAccessType fromSql(String dbString) {
    for (ControlledAccessType value : values()) {
      if (StringUtils.equals(value.dbString, dbString)) {
        return value;
      }
    }
    throw new SerializationException(
        "Deeserialization failed: no matching controlled access type for " + dbString);
  }
}
