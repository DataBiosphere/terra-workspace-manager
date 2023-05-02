package bio.terra.workspace.service.resource.exception;

import bio.terra.common.exception.ConflictException;
import bio.terra.policy.model.TpsPaoConflict;
import java.util.List;

public class PolicyConflictException extends ConflictException {

  public PolicyConflictException(String message) {
    super(message);
  }

  public PolicyConflictException(String message, Throwable cause) {
    super(message, cause);
  }

  public PolicyConflictException(List<String> causes) {
    super("Policy violations exist", causes);
  }

  public PolicyConflictException(String message, List<TpsPaoConflict> causes) {
    super(message, causes.stream().map(c -> c.getNamespace() + ':' + c.getName()).toList());
  }
}
