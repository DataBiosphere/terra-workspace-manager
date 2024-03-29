package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.generated.model.ApiApplicationState;
import bio.terra.workspace.service.workspace.exceptions.InvalidApplicationConfigException;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

public enum WsmApplicationState {
  OPERATING(ApiApplicationState.OPERATING),
  DEPRECATED(ApiApplicationState.DEPRECATED),
  DECOMMISSIONED(ApiApplicationState.DECOMMISSIONED);

  private final ApiApplicationState apiState;

  WsmApplicationState(ApiApplicationState apiState) {
    this.apiState = apiState;
  }

  public static WsmApplicationState fromString(@Nullable String state) {
    for (var stateEnum : WsmApplicationState.values()) {
      if (StringUtils.equalsIgnoreCase(state, stateEnum.name())) {
        return stateEnum;
      }
    }
    throw new InvalidApplicationConfigException(
        "Invalid application state: " + Optional.ofNullable(state).orElse("<null>"));
  }

  // Convert from an enum value to the database form
  public String toDb() {
    return StringUtils.lowerCase(name());
  }

  // Convert from database to enum value
  public static WsmApplicationState fromDb(String dbState) {
    return fromString(dbState);
  }

  // Convert to API enum
  public ApiApplicationState toApi() {
    return apiState;
  }
}
