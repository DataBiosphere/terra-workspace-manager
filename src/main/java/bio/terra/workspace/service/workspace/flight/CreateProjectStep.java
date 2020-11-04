package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.stairway.*;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.GoogleWorkspaceConfiguration;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.cloudresourcemanager.model.ResourceId;
import com.google.api.services.serviceusage.v1.model.BatchEnableServicesRequest;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * A {@link Step} for creating a Google Project for a workspace.
 *
 * <p>TODO(PF-156): Use RBS for project creation instead.
 */
public class CreateProjectStep implements Step {
  @VisibleForTesting
  static final ImmutableList<String> ENABLED_SERVICES =
      ImmutableList.of("storage-api.googleapis.com");

  private final CloudResourceManagerCow resourceManager;
  private final ServiceUsageCow serviceUsage;
  private final GoogleWorkspaceConfiguration googleWorkspaceConfiguration;

  public CreateProjectStep(
      CloudResourceManagerCow resourceManager,
      ServiceUsageCow serviceUsage,
      GoogleWorkspaceConfiguration googleWorkspaceConfiguration) {
    this.resourceManager = resourceManager;
    this.serviceUsage = serviceUsage;
    this.googleWorkspaceConfiguration = googleWorkspaceConfiguration;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    createProject(projectId);
    enableServices(projectId);
    // TODO(PF-186): setup billing.
    return StepResult.getStepResultSuccess();
  }

  private void createProject(String projectId) throws RetryException {
    try {
      Optional<Project> alreadyCreatedProject =
          GoogleUtils.retrieveProject(projectId, resourceManager);
      if (alreadyCreatedProject.isPresent()) {
        return;
      }
      Project project =
          new Project()
              .setProjectId(projectId)
              .setParent(
                  new ResourceId()
                      .setType("folder")
                      .setId(googleWorkspaceConfiguration.getFolderId()));
      OperationCow<?> operation =
          resourceManager
              .operations()
              .operationCow(resourceManager.projects().create(project).execute());
      pollUntilSuccess(operation, Duration.ofSeconds(30), Duration.ofMinutes(5));
    } catch (IOException e) {
      throw new RetryException("Error creating project.", e);
    }
  }

  private void enableServices(String projectId) throws RetryException {
    try {
      Project project = resourceManager.projects().get(projectId).execute();
      String projectName = "projects/" + projectId;
      OperationCow<?> operation =
          serviceUsage
              .operations()
              .operationCow(
                  serviceUsage
                      .services()
                      .batchEnable(
                          projectName,
                          new BatchEnableServicesRequest().setServiceIds(ENABLED_SERVICES))
                      .execute());
      pollUntilSuccess(operation, Duration.ofSeconds(30), Duration.ofMinutes(5));
    } catch (IOException e) {
      throw new RetryException("Error enabling services.", e);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    try {
      GoogleUtils.deleteProject(projectId, resourceManager);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Poll until the operation has completed. Throws any error or timeouts as a {@link
   * RetryException}.
   */
  private static void pollUntilSuccess(
      OperationCow<?> operation, Duration pollingInterval, Duration timeout) throws RetryException {
    try {
      operation = OperationUtils.pollUntilComplete(operation, pollingInterval, timeout);
      if (operation.getOperationAdapter().getError() != null) {
        throw new RetryException(
            String.format(
                "Error polling operation. name [%s] message [%s]",
                operation.getOperationAdapter().getName(),
                operation.getOperationAdapter().getError().getMessage()));
      }
    } catch (IOException | InterruptedException e) {
      throw new RetryException("Error polling.", e);
    }
  }
}
