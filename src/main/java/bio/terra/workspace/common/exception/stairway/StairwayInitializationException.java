// TODO: this should be moved to the Stairway library
package bio.terra.workspace.common.exception.stairway;

import bio.terra.stairway.exception.StairwayRuntimeException;

public class StairwayInitializationException  extends StairwayRuntimeException {

  public StairwayInitializationException(String message) {
    super(message);
  }

  public StairwayInitializationException(String message, Throwable cause) {
    super(message, cause);
  }

  public StairwayInitializationException(Throwable cause) {
    super(cause);
  }
}
