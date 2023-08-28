package bio.terra.workspace.service.resource;

import bio.terra.common.exception.BadRequestException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import org.springframework.http.HttpStatus;

/** Utility methods for handling GCP exceptions during flights */
public class GcpFlightExceptionUtils {

  /**
   * On gcp calls with malformed parameters, throw a {@link BadRequestException} and surface the GCP
   * error message
   */
  public static void handleGcpBadRequestException(GoogleJsonResponseException e)
      throws BadRequestException {
    if (HttpStatus.BAD_REQUEST.value() == e.getStatusCode()
        || HttpStatus.NOT_FOUND.value() == e.getStatusCode()) {
      // Don't retry bad requests, which won't change. Instead, fail faster.
      throw new BadRequestException(e.getDetails().getMessage());
    }
  }
}
