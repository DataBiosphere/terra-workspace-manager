package bio.terra.workspace.db.model;

import bio.terra.workspace.service.workspace.model.OperationType;

public record DbWorkspaceActivityLog(String userEmail, String subjectId, OperationType operationType) {

  public static DbWorkspaceActivityLog getDbWorkspaceActivityLog(
      OperationType operationType, String userEmail, String subjectId) {
    return new DbWorkspaceActivityLog(userEmail, subjectId, operationType);
  }
}
