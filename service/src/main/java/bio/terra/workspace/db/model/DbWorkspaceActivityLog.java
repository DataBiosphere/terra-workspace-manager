package bio.terra.workspace.db.model;

import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public class DbWorkspaceActivityLog {

  private @Nullable OperationType operationType;

  private @Nullable String changeAgentEmail;

  private @Nullable String changeAgentSubjectId;

  private static final Supplier<RuntimeException> MISSING_REQUIRED_FIELD =
      () -> new MissingRequiredFieldsException("Missing required field");

  public DbWorkspaceActivityLog operationType(OperationType operationType) {
    this.operationType = operationType;
    return this;
  }

  public DbWorkspaceActivityLog changeAgentEmail(String changeAgentEmail) {
    this.changeAgentEmail = changeAgentEmail;
    return this;
  }

  public DbWorkspaceActivityLog changeAgentSubjectId(String changeAgentSubjectId) {
    this.changeAgentSubjectId = changeAgentSubjectId;
    return this;
  }

  public OperationType getOperationType() {
    return Optional.ofNullable(operationType).orElseThrow(MISSING_REQUIRED_FIELD);
  }

  public String getChangeAgentEmail() {
    return Optional.ofNullable(changeAgentEmail).orElseThrow(MISSING_REQUIRED_FIELD);
  }

  public Optional<String> getChangeAgentSubjectId() {
    return Optional.ofNullable(changeAgentSubjectId);
  }
}
