package bio.terra.workspace.service.policy.exception;

import bio.terra.common.exception.ForbiddenException;

public class PolicyServiceAuthorizationException extends ForbiddenException {

  public PolicyServiceAuthorizationException(String message) {
    super(message);
  }

  public PolicyServiceAuthorizationException(String message, Throwable cause) {
    super(message, cause);
  }

  public PolicyServiceAuthorizationException(Throwable cause) {
    super(cause);
  }
}
