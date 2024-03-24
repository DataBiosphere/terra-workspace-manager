package bio.terra.workspace.common.flightGenerator;

import bio.terra.stairway.exception.StairwayException;

public class InvalidRetryAnnotationException extends StairwayException {
  public InvalidRetryAnnotationException(String message) {
    super(message);
  }
}
