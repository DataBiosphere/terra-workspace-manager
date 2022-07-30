package bio.terra.workspace.common.logging.model;

import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * This class holds the change details of an activity log. Currently, it contains the `who` and
 * `when` of an activity log change.
 */
public class ActivityLogChangeDetails {

  private static final Supplier<RuntimeException> MISSING_REQUIRED_FIELD =
      () -> new MissingRequiredFieldsException("Missing required field");

  private @Nullable OffsetDateTime changeDate;
  private @Nullable String actorSubjectId;
  private @Nullable String actorEmail;

  public ActivityLogChangeDetails changeDate(OffsetDateTime changedDate) {
    this.changeDate = changedDate;
    return this;
  }

  public ActivityLogChangeDetails actorSubjectId(String actorSubjectId) {
    this.actorSubjectId = actorSubjectId;
    return this;
  }

  public ActivityLogChangeDetails actorEmail(String actorEmail) {
    this.actorEmail = actorEmail;
    return this;
  }

  public OffsetDateTime getChangeDate() {
    return Optional.ofNullable(changeDate).orElseThrow(MISSING_REQUIRED_FIELD);
  }

  public String getActorEmail() {
    return Optional.ofNullable(actorEmail).orElseThrow(MISSING_REQUIRED_FIELD);
  }

  public String getActorSubjectId() {
    return Optional.ofNullable(actorSubjectId).orElseThrow(MISSING_REQUIRED_FIELD);
  }
}
