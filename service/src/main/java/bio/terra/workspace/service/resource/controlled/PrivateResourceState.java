package bio.terra.workspace.service.resource.controlled;

import bio.terra.common.exception.SerializationException;
import bio.terra.workspace.generated.model.ApiPrivateResourceState;
import org.apache.commons.lang3.StringUtils;

/**
 * State tracking for private resources. Private resources are assigned to individual users, and we
 * revoke resource access when users leave a workspace.
 */
public enum PrivateResourceState {
  // ABANDONED state represents a resource which no users have access to.
  ABANDONED("ABANDONED", ApiPrivateResourceState.ABANDONED),
  // ACTIVE state represents a resource which a single user has access to
  ACTIVE("ACTIVE", ApiPrivateResourceState.ACTIVE),
  // INITIALIZING state represents a resource which is being created.
  INITIALIZING("INITIALIZING", ApiPrivateResourceState.INITIALIZING),
  // NOT_APPLICABLE state represents shared resources, or private resources created in legacy
  // workspaces where WSM is not able to determine workspace membership.
  NOT_APPLICABLE("NOT_APPLICABLE", ApiPrivateResourceState.NOT_APPLICABLE);

  private final String dbString;
  private final ApiPrivateResourceState apiState;

  PrivateResourceState(String dbString, ApiPrivateResourceState apiState) {
    this.dbString = dbString;
    this.apiState = apiState;
  }

  public ApiPrivateResourceState toApiModel() {
    return apiState;
  }

  public String toSql() {
    return dbString;
  }

  public static PrivateResourceState fromSql(String dbString) {
    for (PrivateResourceState value : values()) {
      if (StringUtils.equals(value.dbString, dbString)) {
        return value;
      }
    }
    throw new SerializationException(
        "Deserialization failed: no matching private resource state for " + dbString);
  }
}
