package bio.terra.workspace.service.resource.model;

import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.StringUtils;

public enum StewardshipType {
  REFERENCED("REFERENCED"),
  CONTROLLED("CONTROLLED"),
  MONITORED("MONITORED");

  private final String dbString;

  StewardshipType(String dbString) {
    this.dbString = dbString;
  }

  public String toSql() {
    return dbString;
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
}
