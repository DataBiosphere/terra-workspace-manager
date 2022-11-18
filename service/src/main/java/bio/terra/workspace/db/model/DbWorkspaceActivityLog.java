package bio.terra.workspace.db.model;

import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.WsmObjectType;

public record DbWorkspaceActivityLog(
    String actorEmail, String actorSubjectId, OperationType operationType,
    String changeSubjectId, WsmObjectType changeSubjectType) {
}
