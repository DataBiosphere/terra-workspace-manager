package bio.terra.workspace.service.privateresource;

import bio.terra.common.logging.LoggingUtils;
import bio.terra.common.sam.exception.SamNotFoundException;
import bio.terra.workspace.app.configuration.external.PrivateResourceCleanupConfiguration;
import bio.terra.workspace.db.CronjobDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.WorkspaceDao.WorkspaceUserPair;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants.SamResource;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.RemoveUserFromWorkspaceFlight;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PrivateResourceCleanupService {

  private static final Logger logger = LoggerFactory.getLogger(PrivateResourceCleanupService.class);
  private static final String PRIVATE_RESOURCE_CLEANUP_JOB_NAME = "private_resource_cleanup_job";

  private final PrivateResourceCleanupConfiguration configuration;
  private final WorkspaceDao workspaceDao;
  private final ResourceDao resourceDao;
  private final CronjobDao cronjobDao;
  private final SamService samService;
  private final JobService jobService;
  private final ScheduledExecutorService scheduler;

  @Autowired
  public PrivateResourceCleanupService(
      PrivateResourceCleanupConfiguration configuration,
      WorkspaceDao workspaceDao,
      ResourceDao resourceDao,
      CronjobDao cronjobDao,
      SamService samService,
      JobService jobService) {
    this.configuration = configuration;
    this.workspaceDao = workspaceDao;
    this.resourceDao = resourceDao;
    this.cronjobDao = cronjobDao;
    this.samService = samService;
    this.jobService = jobService;
    this.scheduler = Executors.newScheduledThreadPool(1);
  }

  @PostConstruct
  public void startStatusChecking() {
    if (configuration.isEnabled()) {
      // Per scheduleAtFixedRate documentation, if a single execution runs longer than the polling
      // interval, subsequent executions may start late but will not concurrently execute.
      scheduler.scheduleAtFixedRate(
          this::cleanupResourcesSuppressExceptions,
          configuration.getStartupWait().toSeconds(),
          configuration.getPollingInterval().toSeconds(),
          TimeUnit.SECONDS);
    }
  }

  /**
   * Run {@code cleanupResources}, suppressing all thrown exceptions. This is helpful as {@code
   * ScheduledExecutorService.scheduleAtFixedRate} will stop running if any execution throws an
   * exception. Suppressing these exceptions ensures we do not stop cleaning up resources if a
   * single run fails.
   */
  public void cleanupResourcesSuppressExceptions() {
    try {
      cleanupResources();
    } catch (Exception e) {
      LoggingUtils.logAlert(
          logger, "Unexpected error during privateResourceCleanup execution, see stacktrace below");
      logger.error("privateResourceCleanup stacktrace: ", e);
    }
  }

  private void cleanupResources() {
    if (!configuration.isEnabled()) {
      return;
    }
    logger.info("Beginning resource cleanup cronjob");
    // Use a one-second shorter duration here to ensure we don't skip a run by moving slightly too
    // quickly.
    Duration claimTime = configuration.getPollingInterval().minus(Duration.ofSeconds(1));
    // Attempt to claim the latest run of this job to ensure only one pod runs the cleanup job.
    if (!cronjobDao.claimJob(PRIVATE_RESOURCE_CLEANUP_JOB_NAME, claimTime)) {
      logger.info("Another pod has executed this job more recently. Ending resource cleanup.");
      return;
    }

    // First, read all unique (workspace, private user pairs) from WSM's database
    List<WorkspaceUserPair> resourcesToValidate = workspaceDao.getPrivateResourceUsers();
    // For each pair, validate that the user is still in the workspace (i.e. can read the workspace)
    for (WorkspaceUserPair workspaceUserPair : resourcesToValidate) {
      try {
        boolean userHasPermission =
            SamRethrow.onInterrupted(
                () ->
                    samService.checkAuthAsWsmSa(
                        SamResource.WORKSPACE,
                        workspaceUserPair.getWorkspaceId().toString(),
                        SamWorkspaceAction.READ,
                        workspaceUserPair.getUserEmail()),
                "cleanupResources");
        if (!userHasPermission) {
          logger.info(
              "Cleaning up resources for user {} from workspace {}",
              workspaceUserPair.getUserEmail(),
              workspaceUserPair.getWorkspaceId());
          runCleanupFlight(workspaceUserPair);
        }
      } catch (SamNotFoundException notFoundEx) {
        // Older workspaces do not have the "manager" role, so WSM cannot read permissions from
        // them and will never be able to. Sam responds to these requests with 404 rather than 403
        // to avoid leaking workspace existence information.
        // Mark these resources as NOT_APPLICABLE so we don't keep polling.
        logger.warn("Found legacy workspace {}", workspaceUserPair.getWorkspaceId());
        resourceDao.setPrivateResourcesStateForWorkspaceUser(
            workspaceUserPair.getWorkspaceId(),
            workspaceUserPair.getUserEmail(),
            PrivateResourceState.NOT_APPLICABLE);
      }
    }
  }

  private void runCleanupFlight(WorkspaceUserPair workspaceUserPair) {
    String description =
        "Clean up after user "
            + workspaceUserPair.getUserEmail()
            + " in workspace "
            + workspaceUserPair.getWorkspaceId().toString();
    String wsmSaToken = samService.getWsmServiceAccountToken();
    final AuthenticatedUserRequest wsmSaRequest =
        new AuthenticatedUserRequest().token(Optional.of(wsmSaToken));
    JobBuilder userCleanupJob =
        jobService
            .newJob()
            .description(description)
            .flightClass(RemoveUserFromWorkspaceFlight.class)
            .userRequest(wsmSaRequest)
            .workspaceId(workspaceUserPair.getWorkspaceId().toString())
            .operationType(OperationType.SYSTEM_CLEANUP)
            .addParameter(
                WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUserPair.getWorkspaceId().toString())
            .addParameter(WorkspaceFlightMapKeys.USER_TO_REMOVE, workspaceUserPair.getUserEmail())
            // Explicitly indicate there is no role to remove, as the user is already out of the
            // workspace.
            .addParameter(WorkspaceFlightMapKeys.ROLE_TO_REMOVE, null);
    try {
      userCleanupJob.submitAndWait();
    } catch (RuntimeException e) {
      // Log the error, but don't kill this thread as it still needs to clean up other users.
      logger.error(
          "Flight cleaning up user {} in workspace {} failed: ",
          workspaceUserPair.getUserEmail(),
          workspaceUserPair.getWorkspaceId(),
          e);
    }
  }
}
