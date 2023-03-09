package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.common.exception.BadRequestException;
import java.util.List;

public class InvalidAdditionalPoliciesException extends BadRequestException {
  public InvalidAdditionalPoliciesException(String message) {
    super(message);
  }

  public InvalidAdditionalPoliciesException(String message, List<String> causes) {
    super(message, causes);
  }
}
