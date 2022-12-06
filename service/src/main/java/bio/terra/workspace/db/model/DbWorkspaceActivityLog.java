package bio.terra.workspace.db.model;

import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.service.workspace.model.OperationType;

public record DbWorkspaceActivityLog(
    String actorEmail,
    String actorSubjectId,
    OperationType operationType,
    String changeSubjectId,
    ActivityLogChangedTarget changeSubjectType) {}
