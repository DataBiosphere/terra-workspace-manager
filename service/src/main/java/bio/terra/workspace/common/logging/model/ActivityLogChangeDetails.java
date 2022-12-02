package bio.terra.workspace.common.logging.model;

import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.WsmObjectType;
import java.time.OffsetDateTime;

/**
 * This class holds the change details of an activity log. Currently, it contains the `who` and
 * `when` of an activity log change.
 */
public record ActivityLogChangeDetails(
    OffsetDateTime changeDate,
    String actorEmail,
    String actorSubjectId,
    OperationType operationType,
    String changeSubjectId,
    WsmObjectType changeSubjectType) {}
