package bio.terra.workspace.common.exception;

public class SamUnauthorizedException extends ForbiddenException {

  public SamUnauthorizedException(String message) {
    super(message);
  }
}
