package bio.terra.workspace.service.resource;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.ForbiddenException;
import bio.terra.common.exception.UnauthorizedException;
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

  /**
   * Map GCP status codes to WSM exceptions. These are errors that we DO NOT want to retry in the
   * flight.
   */
  public static void handleGcpNonRetryableException(GoogleJsonResponseException e) {
    int statusCode = e.getStatusCode();
    switch (HttpStatus.valueOf(statusCode)) {
      case BAD_REQUEST, NOT_FOUND, TOO_MANY_REQUESTS -> throw new BadRequestException(
          e.getDetails().getMessage(), e);
      case UNAUTHORIZED -> throw new UnauthorizedException(e.getDetails().getMessage(), e);
      case FORBIDDEN -> throw new ForbiddenException(e.getDetails().getMessage(), e);
    }
  }
}
