package bio.terra.workspace.db.model;

import bio.terra.workspace.service.workspace.model.OperationType;

public record DbWorkspaceActivityLog(
    String actorEmail, String actorSubjectId, OperationType operationType) {

  public static DbWorkspaceActivityLog getDbWorkspaceActivityLog(
      OperationType operationType, String actorEmail, String actorSubjectId) {
    return new DbWorkspaceActivityLog(actorEmail, actorSubjectId, operationType);
  }
}
