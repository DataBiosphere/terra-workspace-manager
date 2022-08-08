package bio.terra.workspace.db.model;

import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.Optional;
import java.util.function.Supplier;

public class DbWorkspaceActivityLog {

  private OperationType operationType;

  private String actorEmail;

  private String actorSubjectId;

  private static final Supplier<RuntimeException> MISSING_REQUIRED_FIELD =
      () -> new MissingRequiredFieldsException("Missing required field");

  private DbWorkspaceActivityLog operationType(OperationType operationType) {
    this.operationType = operationType;
    return this;
  }

  private DbWorkspaceActivityLog actorEmail(String actorEmail) {
    this.actorEmail = actorEmail;
    return this;
  }

  private DbWorkspaceActivityLog actorSubjectId(String actorSubjectId) {
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
