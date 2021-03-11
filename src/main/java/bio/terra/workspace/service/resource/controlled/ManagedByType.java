package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.generated.model.ControlledResourceCommonFields;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.StringUtils;

/** Controlled resources can be managed by a user or by an application */
public enum ManagedByType {
  MANAGED_BY_USER("USER"),
  MANAGED_BY_APPLICATION("APPLICATION");

  private final String dbString;

  ManagedByType(String dbString) {
    this.dbString = dbString;
  }

  public String toSql() {
    return dbString;
  }

  public static ManagedByType fromApi(ControlledResourceCommonFields.ManagedByEnum apiManagedBy) {
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
