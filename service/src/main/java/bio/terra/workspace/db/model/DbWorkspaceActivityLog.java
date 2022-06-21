package bio.terra.workspace.db.model;

import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.Optional;
import java.util.function.Supplier;

public class DbWorkspaceActivityLog {

  private OperationType operationType;

  private static final Supplier<RuntimeException> MISSING_REQUIRED_FIELD =
      () -> new MissingRequiredFieldsException("Missing required field");

  public DbWorkspaceActivityLog operationType(OperationType operationType) {
    this.operationType = operationType;
    return this;
  }

  public OperationType getOperationType() {
    return Optional.ofNullable(operationType).orElseThrow(MISSING_REQUIRED_FIELD);
  }
}
