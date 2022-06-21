package bio.terra.workspace.db.model;

import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.UUID;

public class DbActivityLog {

  private OperationType operationType;
  private UUID resourceId;
  private UUID cloudContextId;

  public DbActivityLog operationType(OperationType operationType) {
    this.operationType = operationType;
    return this;
  }

  public OperationType getOperationType() {
    return operationType;
  }

  public DbActivityLog resourceId(UUID resourceId) {
    this.resourceId = resourceId;
    return this;
  }

  public UUID getResourceId() {
    return resourceId;
  }

  public DbActivityLog cloudContextId(UUID cloudContextId) {
    this.cloudContextId = cloudContextId;
    return this;
  }

  public UUID getCloudContextId() {
    return cloudContextId;
  }
}
