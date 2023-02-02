package bio.terra.workspace.service.grant;

import bio.terra.common.logging.LoggingUtils;
import bio.terra.common.sam.exception.SamNotFoundException;
import bio.terra.workspace.app.configuration.external.PrivateResourceCleanupConfiguration;
import bio.terra.workspace.app.configuration.external.TemporaryGrantRevokeConfiguration;
import bio.terra.workspace.db.CronjobDao;
import bio.terra.workspace.db.GrantDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.WorkspaceDao.WorkspaceUserPair;
import bio.terra.workspace.service.grant.flight.RevokeTemporaryGrantFlight;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants.SamResource;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.workspace.flight.RemoveUserFromWorkspaceFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.OperationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class GrantService {

  private static final Logger logger = LoggerFactory.getLogger(GrantService.class);
  private static final String REVOKE_GRANTS_JOB_NAME = "revoke_grants_job";

  private final TemporaryGrantRevokeConfiguration configuration;
  private final GrantDao grantDao;
  private final CronjobDao cronjobDao;
  private final SamService samService;
  private final JobService jobService;
  private final ScheduledExecutorService scheduler;

  @Autowired
  public GrantService(
      TemporaryGrantRevokeConfiguration configuration,
      GrantDao grantDao,
      CronjobDao cronjobDao,
      SamService samService,
      JobService jobService) {
    this.configuration = configuration;
    this.grantDao = grantDao;
    this.cronjobDao = cronjobDao;
    this.samService = samService;
    this.jobService = jobService;
    this.scheduler = Executors.newScheduledThreadPool(1);
  }

  @PostConstruct
  public void startStatusChecking() {
    if (configuration.isRevokeEnabled()) {
      // Per scheduleAtFixedRate documentation, if a single execution runs longer than the polling
      // interval, subsequent executions may start late but will not concurrently execute.
      scheduler.scheduleAtFixedRate(
          this::revokeGrantsSuppressExceptions,
          configuration.getStartupWait().toSeconds(),
          configuration.getPollingInterval().toSeconds(),
          TimeUnit.SECONDS);
    }
  }

  /**
   * Run {@code revokeGrants}, suppressing all thrown exceptions. This is helpful as {@code
   * ScheduledExecutorService.scheduleAtFixedRate} will stop running if any execution throws an
   * exception. Suppressing these exceptions ensures we do not stop revoking temporary
   * grants if a single run fails.
   */
  public void revokeGrantsSuppressExceptions() {
    try {
      revokeGrants();
    } catch (Exception e) {
      LoggingUtils.logAlert(
          logger, "Unexpected error during revokeGrants execution, see stacktrace below");
      logger.error("revokeGrants stacktrace: ", e);
    }
  }

  private void revokeGrants() {
    if (!configuration.isRevokeEnabled()) {
      return;
    }
    logger.info("Beginning revoke grants cronjob");
    // Use a one-second shorter duration here to ensure we don't skip a run by moving slightly too
    // quickly.
    Duration claimTime = configuration.getPollingInterval().minus(Duration.ofSeconds(1));
    // Attempt to claim the latest run of this job to ensure only one pod runs the cleanup job.
    if (!cronjobDao.claimJob(REVOKE_GRANTS_JOB_NAME, claimTime)) {
      logger.info("Another pod has executed this job more recently. Ending resource cleanup.");
      return;
    }

    // Get the list of grants to revoke and spin up a flight for each of them
    List<UUID> revokeList = grantDao.getExpiredGrants();
    for (UUID grantId : revokeList) {
      runRevokeFlight(grantId);
    }
  }

  private void runRevokeFlight(UUID grantId) {
    String description = "Revoke temporary grant " + grantId;

    String wsmSaToken = samService.getWsmServiceAccountToken();
    AuthenticatedUserRequest wsmSaRequest =
        new AuthenticatedUserRequest().token(Optional.of(wsmSaToken));

    JobBuilder revokeJob =
        jobService
            .newJob()
            .description(description)
            .flightClass(RevokeTemporaryGrantFlight.class)
            .userRequest(wsmSaRequest)
            .operationType(OperationType.SYSTEM_CLEANUP)
            .addParameter(RevokeTemporaryGrantFlight.GRANT_ID_KEY, grantId);

    try {
      revokeJob.submit();
    } catch (RuntimeException e) {
      // Log the error, but don't kill this thread as it still needs to clean up other users.
      logger.error("Flight revoking grant {} failed: ", grantId, e);
    }
  }
}
