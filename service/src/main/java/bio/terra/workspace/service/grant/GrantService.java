package bio.terra.workspace.service.grant;

import bio.terra.common.logging.LoggingUtils;
import bio.terra.workspace.app.configuration.external.TemporaryGrantRevokeConfiguration;
import bio.terra.workspace.db.CronjobDao;
import bio.terra.workspace.db.GrantDao;
import bio.terra.workspace.service.grant.flight.RevokeTemporaryGrantFlight;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
   * exception. Suppressing these exceptions ensures we do not stop revoking temporary grants if a
   * single run fails.
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

  /**
   * Only allows users from a specific domain to have temporary grants. Pets are always give
   * temporary grants.
   *
   * @param userEmail email to check
   * @return true if the user can have a temporary grant
   */
  public boolean isUserGrantAllowed(String userEmail) {
    if (StringUtils.isBlank(configuration.getRestrictUserDomain())) {
      logger.info("User grant allowed: {}", userEmail);
      return true;
    }
    boolean endsWithDomain = userEmail.endsWith(configuration.getRestrictUserDomain());
    logger.info("User grant {}allowed: {}", endsWithDomain ? "" : "not ", userEmail);
    return endsWithDomain;
  }

  public void recordProjectGrant(
      UUID workspaceId, @Nullable String userMember, String petMember, String role) {
    GrantData grantData =
        makeGrantData(
            workspaceId,
            userMember,
            petMember,
            GrantType.PROJECT,
            null, // resourceId
            role);
    grantDao.insertGrant(grantData);
  }

  public void recordResourceGrant(
      UUID workspaceId,
      @Nullable String userMember,
      String petMember,
      String role,
      UUID resourceId) {
    GrantData grantData =
        makeGrantData(workspaceId, userMember, petMember, GrantType.RESOURCE, resourceId, role);
    grantDao.insertGrant(grantData);
  }

  public void recordActAsGrant(UUID workspaceId, @Nullable String userMember, String petMember) {
    GrantData grantData =
        makeGrantData(workspaceId, userMember, petMember, GrantType.ACT_AS, null, null);
    grantDao.insertGrant(grantData);
  }

  private GrantData makeGrantData(
      UUID workspaceId,
      @Nullable String userMember,
      String petMember,
      GrantType grantType,
      @Nullable UUID resourceId,
      @Nullable String role) {
    OffsetDateTime createTime = OffsetDateTime.now(ZoneId.of("UTC"));
    OffsetDateTime expireTime = createTime.plus(configuration.getGrantHoldTime());
    UUID grantId = UUID.randomUUID();
    return new GrantData(
        grantId,
        workspaceId,
        userMember,
        petMember,
        grantType,
        resourceId,
        role,
        createTime,
        expireTime);
  }

  private void revokeGrants() {
    if (!configuration.isRevokeEnabled()) {
      return;
    }
    logger.info("Beginning revoke grants cronjob");
    // Use shorter duration here to ensure we don't skip a run by moving slightly too
    // quickly.
    Duration claimTime = configuration.getPollingInterval().minus(Duration.ofSeconds(10));
    // Attempt to claim the latest run of this job to ensure only one pod runs the cleanup job.
    if (!cronjobDao.claimJob(REVOKE_GRANTS_JOB_NAME, claimTime)) {
      logger.info("Another pod has executed this job more recently. Ending resource cleanup.");
      return;
    }

    // Get the list of grants to revoke and spin up a flight for each of them
    List<GrantDao.ExpiredGrant> revokeList = grantDao.getExpiredGrants();
    logger.info("Found {} temporary grants to revoke", revokeList.size());
    for (GrantDao.ExpiredGrant expiredGrant : revokeList) {
      runRevokeFlight(expiredGrant);
    }
  }

  private void runRevokeFlight(GrantDao.ExpiredGrant expiredGrant) {
    String description =
        String.format(
            "revoke temporary grant %s in workspace %s",
            expiredGrant.grantId(), expiredGrant.workspaceId());

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
            .workspaceId(expiredGrant.workspaceId().toString())
            .addParameter(RevokeTemporaryGrantFlight.GRANT_ID_KEY, expiredGrant.grantId());

    try {
      String flightId = revokeJob.submit();
      logger.info("Launched flight {} for {}", flightId, description);
    } catch (RuntimeException e) {
      // Log the error, but don't kill this thread as it still needs to clean up other users.
      logger.error("Flight revoking grant {} failed: ", expiredGrant.grantId(), e);
    }
  }
}
