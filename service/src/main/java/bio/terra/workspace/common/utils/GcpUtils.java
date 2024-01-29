package bio.terra.workspace.common.utils;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.ConflictException;
import bio.terra.common.exception.ForbiddenException;
import bio.terra.common.exception.UnauthorizedException;
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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import io.grpc.Status.Code;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
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
      // Mapping details of gRPC status codes to HTTP in
      // https://chromium.googlesource.com/external/github.com/grpc/grpc/+/refs/tags/v1.21.4-pre1/doc/statuscodes.md
      int intCode = operation.getOperationAdapter().getError().getCode();
      // Do not waste time retrying on client error.
      Code code = Code.values()[intCode];
      String errorMessage = operation.getOperationAdapter().getError().getMessage();
      switch (code) {
          // 400
        case INVALID_ARGUMENT, OUT_OF_RANGE, FAILED_PRECONDITION ->
            throw new BadRequestException(errorMessage);
          // 401
        case UNAUTHENTICATED -> throw new UnauthorizedException(errorMessage);
          // 403 - This includes the "CPU quota limit exceeded" error for AI notebooks.
        case PERMISSION_DENIED -> throw new ForbiddenException(errorMessage);
          // 409
        case ALREADY_EXISTS, ABORTED -> throw new ConflictException(errorMessage);
          // 429
        case RESOURCE_EXHAUSTED -> throw new BadRequestException(errorMessage);
        default ->
            throw new RetryException(
                String.format(
                    "Error polling operation. name [%s] message [%s]",
                    operation.getOperationAdapter().getName(), errorMessage));
      }
    }
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

  /**
   * Obtains an OIDC token from Service Account that this WSM service instance is running as.
   *
   * @param audience 'aud' claim to include in the OIDC JWT
   * @return OIDC JWT representing the WSM SA
   */
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
    return location.replaceAll("(?!^)-[a-zA-Z]$", "");
  }

  // Methods for building member strings using in GCP IAM bindings
  // and pulling the email from IAM member strings
  private static final String GROUP_PREFIX = "group:";
  private static final String USER_PREFIX = "user:";
  private static final String SA_PREFIX = "serviceAccount:";

  private static String stripPrefix(String member, String prefix) {
    int prefixLength = prefix.length();
    if (member != null && member.startsWith(prefix)) {
      return member.substring(prefixLength);
    }
    return member;
  }

  public static String toGroupMember(String email) {
    return GROUP_PREFIX + email;
  }

  public static String toUserMember(String email) {
    return USER_PREFIX + email;
  }

  public static String toSaMember(String email) {
    return SA_PREFIX + email;
  }

  public static String fromUserMember(String member) {
    return stripPrefix(member, USER_PREFIX);
  }

  public static String fromSaMember(String member) {
    return stripPrefix(member, SA_PREFIX);
  }

  @VisibleForTesting public static final int MAX_INSTANCE_NAME_LENGTH = 63;

  /**
   * Generate GCE instance name that meets the requirements for a valid instance.
   *
   * <p>The resource name must be 1-63 characters long, and comply with RFC1035. Specifically, the
   * name must be 1-63 characters long and match the regular expression [a-z]([-a-z0-9]*[a-z0-9])?
   * which means the first character must be a lowercase letter, and all following characters must
   * be a dash, lowercase letter, or digit, except the last character, which cannot be a dash.
   * https://cloud.google.com/compute/docs/reference/rest/v1/instances/insert
   */
  public static String generateInstanceCloudName(
      @Nullable UUID workspaceUuid, String instanceName) {
    // GCE instance name only allows numbers, dash("-"), and lower case letters.
    String generatedName =
        CharMatcher.inRange('0', '9')
            .or(CharMatcher.inRange('a', 'z'))
            .or(CharMatcher.is('-'))
            .retainFrom(instanceName.toLowerCase());
    // The name must start with a letter.
    generatedName =
        CharMatcher.inRange('0', '9').or(CharMatcher.is('-')).trimLeadingFrom(generatedName);
    // Truncate before trimming characters to ensure the name does not end with dash("-").
    generatedName = StringUtils.truncate(generatedName, MAX_INSTANCE_NAME_LENGTH);
    // The name cannot end with dash("-").
    generatedName = CharMatcher.is('-').trimTrailingFrom(generatedName);

    if (generatedName.length() == 0) {
      throw new BadRequestException(
          String.format(
              "Cannot generate a valid GCE instance name from %s, it must contain"
                  + " alphanumerical characters.",
              instanceName));
    }
    return generatedName;
  }

  /** Service account for the instance needs to contain these scopes to interact with SAM. */
  public static final List<String> INSTANCE_SERVICE_ACCOUNT_SCOPES =
      ImmutableList.of(
          "https://www.googleapis.com/auth/cloud-platform",
          "https://www.googleapis.com/auth/userinfo.email",
          "https://www.googleapis.com/auth/userinfo.profile");

  public static String toNetworkString(String projectId, String networkName) {
    return String.format("projects/%s/global/networks/%s", projectId, networkName);
  }

  public static String toSubnetworkString(String projectId, String region, String subnetworkName) {
    return String.format(
        "projects/%s/regions/%s/subnetworks/%s", projectId, region, subnetworkName);
  }

  public static String toMachineTypeString(String zone, String machineType) {
    return String.format("zones/%s/machineTypes/%s", zone, machineType);
  }

  public static String toAcceleratorTypeString(
      String projectId, String zone, String acceleratorType) {
    return String.format(
        "projects/%s/zones/%s/acceleratorTypes/%s", projectId, zone, acceleratorType);
  }
}
