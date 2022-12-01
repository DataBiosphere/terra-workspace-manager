package bio.terra.workspace.common.logging.model;

import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.WsmObjectType;
import java.time.OffsetDateTime;
import org.apache.commons.lang3.builder.EqualsBuilder;

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
    WsmObjectType changeSubjectType) {

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ActivityLogChangeDetails that = (ActivityLogChangeDetails) o;
    return new EqualsBuilder()
        .append(actorEmail, that.actorEmail)
        .append(actorSubjectId, that.actorSubjectId)
        .append(operationType, that.operationType)
        .append(changeSubjectId, that.changeSubjectId)
        .append(changeSubjectType, that.changeSubjectType)
        .isEquals();
  }
}
