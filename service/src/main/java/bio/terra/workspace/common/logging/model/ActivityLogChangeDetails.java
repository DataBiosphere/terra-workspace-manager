package bio.terra.workspace.common.logging.model;

import bio.terra.workspace.service.workspace.model.OperationType;
import com.google.common.annotations.VisibleForTesting;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * This class holds the change details of an activity log. Currently, it contains the `who` and
 * `when` of an activity log change.
 */
public record ActivityLogChangeDetails(
    UUID workspaceId,
    OffsetDateTime changeDate,
    String actorEmail,
    String actorSubjectId,
    OperationType operationType,
    String changeSubjectId,
    ActivityLogChangedTarget changeSubjectType) {

  @VisibleForTesting
  public ActivityLogChangeDetails withChangeDate(@Nullable OffsetDateTime changeDate) {
    return new ActivityLogChangeDetails(
        workspaceId(),
        changeDate,
        actorEmail(),
        actorSubjectId(),
        operationType(),
        changeSubjectId(),
        changeSubjectType());
  }
}
