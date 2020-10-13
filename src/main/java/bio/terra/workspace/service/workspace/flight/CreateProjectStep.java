package bio.terra.workspace.service.workspace.flight;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.stairway.*;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.WorkspaceProjectConfiguration;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.cloudresourcemanager.model.ResourceId;
import com.google.api.services.serviceusage.v1.model.BatchEnableServicesRequest;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** A {@link Step} for creating a GCP Project for a workspace. */
public class CreateProjectStep implements Step {
  private static final String PROJECT_ID_KEY = "CreateProjectStep.projectId";
  private static final ImmutableList<String> ENABLED_SERVICES =
      ImmutableList.of("storage-api.googleapis.com");

  private final CloudResourceManagerCow resourceManager;
  private final ServiceUsageCow serviceUsage;
  private final WorkspaceProjectConfiguration workspaceProjectConfiguration;

  public CreateProjectStep(
      CloudResourceManagerCow resourceManager,
      ServiceUsageCow serviceUsage,
      WorkspaceProjectConfiguration workspaceProjectConfiguration) {
    this.resourceManager = resourceManager;
    this.serviceUsage = serviceUsage;
    this.workspaceProjectConfiguration = workspaceProjectConfiguration;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    // TODO, I think this needs its own step and store it in the db. Could retrieve from there or
    // working map.
    String projectId = getOrGenerateProjectId(flightContext.getWorkingMap());
    createProject(projectId);
    enableServices(projectId);
    // TODO setup billing.
    return StepResult.getStepResultSuccess();
  }

  private void createProject(String projectId) throws RetryException {
    try {
      Optional<Project> alreadyCreatedProject = retrieveProject(projectId);
      if (alreadyCreatedProject.isPresent()) {
        return;
      }
      Project project =
          new Project()
              .setProjectId(projectId)
              .setParent(
                  new ResourceId()
                      .setType("folder")
                      .setId(workspaceProjectConfiguration.getFolderId()));
      OperationCow<?> operation =
          resourceManager
              .operations()
              .operationCow(resourceManager.projects().create(project).execute());
      pollUntilSuccess(operation, Duration.ofSeconds(30), Duration.ofMinutes(5));
    } catch (IOException e) {
      throw new RetryException("Error creating project.", e);
    }
  }

  private Optional<Project> retrieveProject(String projectId) throws IOException {
    try {
      return Optional.of(resourceManager.projects().get(projectId).execute());
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 403) {
        // Google returns 403 for projects we don't have access to and projects that don't exist.
        // We assume in this case that the project does not exist, not that somebody else has created a project with the same random id.
        return Optional.empty();
      }
      throw e;
    }
  }

  private void enableServices(String projectId) throws RetryException {
    try {
      Project project = resourceManager.projects().get(projectId).execute();
      String projectName = "projects/" + projectId;
      ImmutableList<String> serviceIds =
          ENABLED_SERVICES.stream()
              .map(
                  service ->
                      String.format("projects/%d/services/%s", project.getProjectNumber(), service))
              .collect(ImmutableList.toImmutableList());

      OperationCow<?> operation =
          serviceUsage
              .operations()
              .operationCow(
                  serviceUsage
                      .services()
                      .batchEnable(
                          projectName, new BatchEnableServicesRequest().setServiceIds(serviceIds))
                      .execute());
      pollUntilSuccess(operation, Duration.ofSeconds(30), Duration.ofMinutes(5));
    } catch (IOException e) {
      throw new RetryException("Error enabling services.", e);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    String projectId = flightContext.getWorkingMap().get(PROJECT_ID_KEY, String.class);
    try {
      Optional<Project> project = retrieveProject(projectId);
      if (project.isEmpty()) {
        // The project does not exist.
        return StepResult.getStepResultSuccess();
      }
      if (project.get().getLifecycleState().equals("DELETE_REQUESTED")
              || project.get().getLifecycleState().equals("DELETE_IN_PROGRESS")) {
        // The project is already being deleted.
        return StepResult.getStepResultSuccess();
      }
      resourceManager.projects().delete(projectId).execute();
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Gets the a project id to use from the working map, or generates and stores the project id
   * there.
   *
   * <p>No matter how many times this step is restarted, we want it to always use the same project
   * id so that at most 1 project is created.
   */
  private static String getOrGenerateProjectId(FlightMap workingMap) {
    String projectId = workingMap.get(PROJECT_ID_KEY, String.class);
    if (projectId != null) {
      return projectId;
    }
    // Generate a pseudo-random project id.
    projectId = "wm-" + Long.valueOf(UUID.randomUUID().getMostSignificantBits()).toString();
    workingMap.put(PROJECT_ID_KEY, projectId);
    return projectId;
  }

  /**
   * Poll until the operation has completed. Throws any error or timeouts as a {@link
   * RetryException}.
   */
  private static void pollUntilSuccess(
      OperationCow<?> operation, Duration pollingInterval, Duration timeout) throws RetryException {
    try {
      operation =
          OperationUtils.pollUntilComplete(
              operation, Duration.ofSeconds(20), Duration.ofMinutes(5));
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
