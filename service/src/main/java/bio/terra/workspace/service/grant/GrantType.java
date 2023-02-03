package bio.terra.workspace.service.grant;

import bio.terra.workspace.common.exception.InternalLogicException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;


public enum GrantType {
  RESOURCE("RESOURCE"),
  PROJECT("PROJECT"),
  ACT_AS("ACT_AS");

  private final String dbString;

  GrantType(String dbString) {
    this.dbString = dbString;
  }

  public String toDb() {
    return dbString;
  }

  public static GrantType fromDb(String dbString) {
    for (GrantType type : values()) {
      if (type.dbString.equals(dbString)) {
        return type;
      }
    }
    throw new InternalLogicException("Database value not a valid GrantType");
  }

}
