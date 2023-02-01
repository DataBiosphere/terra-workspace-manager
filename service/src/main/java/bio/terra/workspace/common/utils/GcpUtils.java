package bio.terra.workspace.common.utils;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.Step;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.workspace.exceptions.SaCredentialsMissingException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import com.google.cloud.ServiceOptions;
import io.grpc.Status.Code;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/** Utilities for interacting with Google Cloud APIs within {@link Step}s. */
public class GcpUtils {
  private static final Logger logger = LoggerFactory.getLogger(GcpUtils.class);

  private GcpUtils() {}

  /** Try to delete the Project associated with {@code projectId}. */
  public static void deleteProject(String projectId, CloudResourceManagerCow resourceManager)
      throws IOException, InterruptedException, RetryException {
    Optional<Project> project = retrieveProject(projectId, resourceManager);
    if (project.isEmpty()) {
      // The project does not exist.
      return;
    }
    if (project.get().getState().equals("DELETE_REQUESTED")
        || project.get().getState().equals("DELETE_IN_PROGRESS")) {
      // The project is already being deleted.
      return;
    }
    pollAndRetry(
        resourceManager
            .operations()
            .operationCow(resourceManager.projects().delete(projectId).execute()),
        Duration.ofSeconds(5),
        Duration.ofMinutes(5));
  }

  /**
   * Returns a {@link Project} corresponding to the {@code projectId}, if one exists. Handles 403
   * errors as no project existing.
   */
  public static Optional<Project> retrieveProject(
      String projectId, CloudResourceManagerCow resourceManager) throws IOException {
    try {
      return Optional.of(resourceManager.projects().get(projectId).execute());
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == HttpStatus.FORBIDDEN.value()) {
        // Google returns 403 for projects we don't have access to and projects that don't exist.
        // We assume in this case that the project does not exist, not that somebody else has
        // created a project with the same id.
        return Optional.empty();
      }
      throw e;
    }
  }

  /**
   * Poll until the Google Service API operation has completed. If an error is retryable, throws a
   * {@link RetryException}.
   */
  public static void pollAndRetry(
      OperationCow<?> operation, Duration pollingInterval, Duration timeout)
      throws RetryException, IOException, InterruptedException {
    operation = OperationUtils.pollUntilComplete(operation, pollingInterval, timeout);
    if (operation.getOperationAdapter().getError() != null) {
      int code = operation.getOperationAdapter().getError().getCode();
      if (is4xxClientError(code)) {
        // do not waste time retrying on client error.
        throw new BadRequestException(
            String.format("Gcp calls failed with client error code %s. Do not retry", code));
      } else {
        throw new RetryException(
            String.format(
                "Error polling operation. name [%s] message [%s]",
                operation.getOperationAdapter().getName(),
                operation.getOperationAdapter().getError().getMessage()));
      }
    }
  }

  /**
   * Check whether the grpc status code is a client error.
   *
   * <p>Details of mapping of gRPC status code to http in
   * https://chromium.googlesource.com/external/github.com/grpc/grpc/+/refs/tags/v1.21.4-pre1/doc/statuscodes.md
   *
   * @param code gRPC status code.
   */
  private static boolean is4xxClientError(int code) {
    return Code.INVALID_ARGUMENT.value() == code // 400
        || Code.OUT_OF_RANGE.value() == code // 400
        || Code.FAILED_PRECONDITION.value() == code // 400
        || Code.ALREADY_EXISTS.value() == code // 409
        || Code.ABORTED.value() == code // 409
        || Code.UNAUTHENTICATED.value() == code // 401
        || Code.RESOURCE_EXHAUSTED.value() == code; // 429
  }

  public static String getControlPlaneProjectId() {
    return Optional.ofNullable(ServiceOptions.getDefaultProjectId())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Could not determine default GCP control plane project ID."));
  }

  /**
   * Returns the email of the application default credentials, which should represent a service
   * account in all WSM deployments.
   */
  public static String getWsmSaEmail(GoogleCredentials wsmCredentials) {
    // WSM always runs as a service account, but credentials for that SA may come from different
    // sources depending on whether it is running in GCP or locally.
    if (wsmCredentials instanceof ServiceAccountSigner) {
      return ((ServiceAccountSigner) wsmCredentials).getAccount();
    } else {
      throw new SaCredentialsMissingException(
          "Unable to find WSM service account credentials. Ensure WSM is actually running as a service account");
    }
  }

  public static String getWsmSaEmail() {
    try {
      return getWsmSaEmail(GoogleCredentials.getApplicationDefault());
    } catch (IOException e) {
      throw new SaCredentialsMissingException(
          "Unable to find WSM service account credentials. Ensure WSM is actually running as a service account");
    }
  }

  public static GoogleCredentials getGoogleCredentialsFromUserRequest(
      AuthenticatedUserRequest userRequest) {
    // The expirationTime argument is only used for refresh tokens, not access tokens.
    AccessToken accessToken = new AccessToken(userRequest.getRequiredToken(), null);
    return GoogleCredentials.create(accessToken);
  }

  /**
   * Extract the region part from the given location string. If the string is a region, return that.
   * If the string looks like a zone, return just the region part. Basically, remove any trailing
   * "-[a-z]".
   */
  public static String parseRegion(String location) {
    return location.replaceAll("(?!^)-[a-z]$", "");
  }

  public static String getWsmSaJwt(String audience) {
    try {
      GoogleCredentials googleCredentials = GoogleCredentials.getApplicationDefault();

      IdTokenCredentials idTokenCredentials =
          IdTokenCredentials.newBuilder()
              .setIdTokenProvider((IdTokenProvider) googleCredentials)
              .setTargetAudience(audience)
              .setOptions(
                  Arrays.asList(
                      IdTokenProvider.Option.FORMAT_FULL, IdTokenProvider.Option.LICENSES_TRUE))
              .build();

      return idTokenCredentials.refreshAccessToken().getTokenValue();
    } catch (IOException e) {
      throw new SaCredentialsMissingException(
          "Unable to get WSM service account JWT. Ensure WSM is actually running as a service account");
    }
  }
}
