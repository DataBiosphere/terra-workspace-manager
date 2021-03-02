package bio.terra.workspace.service.workspace.model;

import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.StringUtils;

public enum CloudPlatform {
  GCP("GCP"),
  AZURE("AZURE");

  private final String dbString;

  CloudPlatform(String dbString) {
    this.dbString = dbString;
  }

  public String toSql() {
    return dbString;
  }

  public static CloudPlatform fromSql(String dbString) {
    for (CloudPlatform value : values()) {
      if (StringUtils.equals(value.dbString, dbString)) {
        return value;
      }
    }
    throw new SerializationException(
            "Deeserialization failed: no matching cloud platform for " + dbString);
  }
}
