package bio.terra.workspace.service.privateresource;

import bio.terra.common.sam.exception.SamForbiddenException;
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
import bio.terra.workspace.service.resource.controlled.PrivateResourceState;
import bio.terra.workspace.service.workspace.flight.RemoveUserFromWorkspaceFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
      scheduler.scheduleAtFixedRate(
          this::cleanupResources,
          configuration.getStartupWaitSeconds(),
          TimeUnit.SECONDS.convert(configuration.getPollingIntervalMinutes(), TimeUnit.MINUTES),
          TimeUnit.SECONDS);
    }
  }

  public void cleanupResources() {
    if (!configuration.isEnabled()) {
      return;
    }
    logger.info("Beginning resource cleanup cronjob");
    // Use a one-second shorter duration here to ensure we don't skip a run due to run time.
    Duration claimTime =
        Duration.ofMinutes(configuration.getPollingIntervalMinutes()).minus(Duration.ofSeconds(1));
    // Attempt to claim the latest run of this job to ensure only one pod runs the cleanup job.
    if (!cronjobDao.claimJob(PRIVATE_RESOURCE_CLEANUP_JOB_NAME, claimTime)) {
      logger.info("Another pod has executed this job more recently. Ending resource cleanup.");
      return;
    }

    // First, read all unique (workspace, private user pairs) from WSM's database
    List<WorkspaceUserPair> resourcesToValidate = workspaceDao.getPrivateResourceUsers();
    logger.info("Resources to validate: {}", resourcesToValidate);
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
          launchCleanupFlight(workspaceUserPair);
        }
      } catch (SamForbiddenException forbiddenEx) {
        // Older workspaces do not have the "manager" role, so WSM cannot read permissions from
        // them and will never be able to. Mark this as a LEGACY resource so we don't keep polling.
        logger.info("Found LEGACY workspace {}", workspaceUserPair.getWorkspaceId());
        resourceDao.setPrivateResourcesStateForWorkspaceUser(
            workspaceUserPair.getWorkspaceId(),
            workspaceUserPair.getUserEmail(),
            PrivateResourceState.LEGACY);
      }
    }
  }

  private void launchCleanupFlight(WorkspaceUserPair workspaceUserPair) {
    String description =
        "Clean up after user "
            + workspaceUserPair.getUserEmail()
            + " in workspace "
            + workspaceUserPair.getWorkspaceId().toString();
    String wsmSaToken = samService.getWsmServiceAccountToken();
    AuthenticatedUserRequest wsmSaRequest =
        new AuthenticatedUserRequest().token(Optional.of(wsmSaToken));
    JobBuilder userCleanupJob =
        jobService
            .newJob(
                description,
                UUID.randomUUID().toString(),
                RemoveUserFromWorkspaceFlight.class,
                null,
                wsmSaRequest)
            .addParameter(
                WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUserPair.getWorkspaceId().toString())
            .addParameter(WorkspaceFlightMapKeys.USER_TO_REMOVE, workspaceUserPair.getUserEmail())
            // Explicitly indicate there is no role to remove, as the user is already out of the
            // workspace.
            .addParameter(WorkspaceFlightMapKeys.ROLE_TO_REMOVE, null);
    try {
      userCleanupJob.submitAndWait(null);
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
