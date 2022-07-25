package bio.terra.workspace.common.logging.model;

import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * This class hold the change details of an activity log. Currently, it contains the who and when of
 * an activity log change.
 */
public class ActivityLogChangeDetails {

  private static final Supplier<RuntimeException> MISSING_REQUIRED_FIELD =
      () -> new MissingRequiredFieldsException("Missing required field");

  private @Nullable OffsetDateTime changedDate;
  private @Nullable String userSubjectId;
  private @Nullable String userEmail;

  public ActivityLogChangeDetails dateTime(OffsetDateTime changedDate) {
    this.changedDate = changedDate;
    return this;
  }

  public ActivityLogChangeDetails userSubjectId(String subjectId) {
    this.userSubjectId = subjectId;
    return this;
  }

  public ActivityLogChangeDetails userEmail(String userEmail) {
    this.userEmail = userEmail;
    return this;
  }

  public OffsetDateTime getChangedDate() {
    return Optional.ofNullable(changedDate).orElseThrow(MISSING_REQUIRED_FIELD);
  }

  public String getUserEmail() {
    return Optional.ofNullable(userEmail).orElseThrow(MISSING_REQUIRED_FIELD);
  }

  public @Nullable String getUserSubjectId() {
    return userSubjectId;
  }
}
