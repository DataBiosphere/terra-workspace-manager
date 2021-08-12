package bio.terra.workspace.common.utils;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.Step;
import bio.terra.stairway.exception.RetryException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import com.google.cloud.ServiceOptions;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpStatus;

/** Utilities for interacting with Google Cloud APIs within {@link Step}s. */
public class GcpUtils {
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
    pollUntilSuccess(
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
   * Poll until the Google Service API operation has completed. Throws any error or timeouts as a
   * {@link RetryException}.
   */
  public static void pollUntilSuccess(
      OperationCow<?> operation, Duration pollingInterval, Duration timeout)
      throws RetryException, IOException, InterruptedException {
    operation = OperationUtils.pollUntilComplete(operation, pollingInterval, timeout);
    if (operation.getOperationAdapter().getError() != null) {
      throw new RetryException(
          String.format(
              "Error polling operation. name [%s] message [%s]",
              operation.getOperationAdapter().getName(),
              operation.getOperationAdapter().getError().getMessage()));
    }
  }

  public static String getControlPlaneProjectId() {
    return Optional.ofNullable(ServiceOptions.getDefaultProjectId())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Could not determine default GCP control plane project ID."));
  }
}
