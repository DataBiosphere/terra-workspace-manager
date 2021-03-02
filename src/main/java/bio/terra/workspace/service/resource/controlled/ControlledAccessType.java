package bio.terra.workspace.service.resource.controlled;

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
