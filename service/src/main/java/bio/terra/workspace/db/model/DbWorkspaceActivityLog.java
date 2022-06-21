package bio.terra.workspace.db.model;

import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.UUID;

public class DbWorkspaceActivityLog {

  private OperationType operationType;
  private UUID resourceId;
  private UUID cloudContextId;

  public DbWorkspaceActivityLog operationType(OperationType operationType) {
    this.operationType = operationType;
    return this;
  }

  public OperationType getOperationType() {
    return operationType;
  }

  public DbWorkspaceActivityLog resourceId(UUID resourceId) {
    this.resourceId = resourceId;
    return this;
  }

  public UUID getResourceId() {
    return resourceId;
  }

  public DbWorkspaceActivityLog cloudContextId(UUID cloudContextId) {
    this.cloudContextId = cloudContextId;
    return this;
  }

  public UUID getCloudContextId() {
    return cloudContextId;
  }
}
