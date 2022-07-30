package bio.terra.workspace.db.model;

import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public class DbWorkspaceActivityLog {

  private @Nullable OperationType operationType;

  private @Nullable String actorEmail;

  private @Nullable String actorSubjectId;

  private static final Supplier<RuntimeException> MISSING_REQUIRED_FIELD =
      () -> new MissingRequiredFieldsException("Missing required field");

  public DbWorkspaceActivityLog operationType(OperationType operationType) {
    this.operationType = operationType;
    return this;
  }

  public DbWorkspaceActivityLog actorEmail(String actorEmail) {
    this.actorEmail = actorEmail;
    return this;
  }

  public DbWorkspaceActivityLog actorSubjectId(String actorSubjectId) {
    this.actorSubjectId = actorSubjectId;
    return this;
  }

  public OperationType getOperationType() {
    return Optional.ofNullable(operationType).orElseThrow(MISSING_REQUIRED_FIELD);
  }

  public String getActorEmail() {
    return Optional.ofNullable(actorEmail).orElseThrow(MISSING_REQUIRED_FIELD);
  }

  public String getActorSubjectId() {
    return Optional.ofNullable(actorSubjectId).orElseThrow(MISSING_REQUIRED_FIELD);
  }

  public static DbWorkspaceActivityLog getDbWorkspaceActivityLog(
      OperationType operationType, String userEmail, String subjectId) {
    return new DbWorkspaceActivityLog()
        .operationType(operationType)
        .actorEmail(userEmail)
        .actorSubjectId(subjectId);
  }
}
