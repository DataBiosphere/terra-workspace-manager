package bio.terra.workspace.common.flightGenerator;

import bio.terra.stairway.exception.StairwayException;

public class InvalidUndoAnnotationException extends StairwayException {

  public InvalidUndoAnnotationException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidUndoAnnotationException(String message) {
    super(message);
  }
}
