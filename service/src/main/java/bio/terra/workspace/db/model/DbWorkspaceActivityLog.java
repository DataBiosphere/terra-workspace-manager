package bio.terra.workspace.db.model;

import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public class DbWorkspaceActivityLog {

  private @Nullable OperationType operationType;

  private @Nullable String userEmail;

  private @Nullable String userSubjectId;

  private static final Supplier<RuntimeException> MISSING_REQUIRED_FIELD =
      () -> new MissingRequiredFieldsException("Missing required field");

  public DbWorkspaceActivityLog operationType(OperationType operationType) {
    this.operationType = operationType;
    return this;
  }

  public DbWorkspaceActivityLog userEmail(String userEmail) {
    this.userEmail = userEmail;
    return this;
  }

  public DbWorkspaceActivityLog userSubjectId(String userSubjectId) {
    this.userSubjectId = userSubjectId;
    return this;
  }

  public OperationType getOperationType() {
    return Optional.ofNullable(operationType).orElseThrow(MISSING_REQUIRED_FIELD);
  }

  public String getUserEmail() {
    return Optional.ofNullable(userEmail).orElseThrow(MISSING_REQUIRED_FIELD);
  }

  public @Nullable String getUserSubjectId() {
    return userSubjectId;
  }

  public static DbWorkspaceActivityLog getDbWorkspaceActivityLog(
      OperationType operationType, String userEmail, String subjectId) {
    return new DbWorkspaceActivityLog()
        .operationType(operationType)
        .userEmail(userEmail)
        .userSubjectId(subjectId);
  }
}
