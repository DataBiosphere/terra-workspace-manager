package bio.terra.workspace.service.status;

import bio.terra.common.logging.LoggingUtils;
import bio.terra.workspace.app.configuration.external.StatusCheckConfiguration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 This class is factored out with the idea that it could be moved to Terra Common Library.

 BaseStatusService is a Spring replacement for workbench-libs' HealthMonitor utilities. It checks
 status information from subsystems asynchronously at regular intervals and provides a cached
 version of the latest statuses to support a high-traffic status endpoint.
 It also tracks time since the last update and returns an unhealthy status if subsystems are not
 checked after some amount of time, which indicates that something has gone wrong.

 Specific services should extend this class with Component objects that register the appropriate
 subsystems.
*/
public class BaseStatusService {
  private static final Logger logger = LoggerFactory.getLogger(BaseStatusService.class);
  private static final int PARALLELISM_THRESHOLD = 1;

  /** cached status */
  private final AtomicBoolean statusOk;

  /** configuration parameters */
  private final StatusCheckConfiguration configuration;

  /** set of status methods to check */
  private final ConcurrentHashMap<String, Supplier<Boolean>> statusCheckMap;

  /** scheduler */
  private final ScheduledExecutorService scheduler;

  /** last time cache was updated */
  private Instant lastStatusUpdate;

  public BaseStatusService(StatusCheckConfiguration configuration) {
    this.configuration = configuration;
    this.statusCheckMap = new ConcurrentHashMap<>();

    this.statusOk = new AtomicBoolean(false);
    this.lastStatusUpdate = Instant.now();
    this.scheduler = Executors.newScheduledThreadPool(1);
  }

  @PostConstruct
  public void startStatusChecking() {
    if (configuration.isEnabled()) {
      scheduler.scheduleAtFixedRate(
          this::checkStatus,
          configuration.getStartupWaitSeconds(),
          configuration.getPollingIntervalSeconds(),
          TimeUnit.SECONDS);
    }
  }

  public void registerStatusCheck(String name, Supplier<Boolean> checkFn) {
    statusCheckMap.put(name, checkFn);
  }

  public void checkStatus() {
    if (configuration.isEnabled()) {
      AtomicBoolean summaryOk = new AtomicBoolean(true);
      statusCheckMap.forEach(
          PARALLELISM_THRESHOLD,
          (name, fn) -> {
            boolean isOk;
            try {
              isOk = fn.get();
            } catch (Exception e) {
              logger.warn("Status check exception for " + name, e);
              isOk = false;
            }
            // If not OK, set to summary to false. We only ever go from true -> false
            // so there are no concurrency issues here.
            if (!isOk) {
              summaryOk.set(false);
            }
          });
      statusOk.set(summaryOk.get());
      lastStatusUpdate = Instant.now();
    }
  }

  public boolean getCurrentStatus() {
    if (configuration.isEnabled()) {
      // If staleness time (last update + stale threshold) is before the current time, then
      // we are officially not OK.
      if (lastStatusUpdate
          .plusSeconds(configuration.getStalenessThresholdSeconds())
          .isBefore(Instant.now())) {
        LoggingUtils.logAlert(
            logger,
            String.format(
                "Status has not been updated since %s. This might mean that the status cronjob has failed, or that requests to downstream services are timing out.",
                lastStatusUpdate));
        statusOk.set(false);
      }
      return statusOk.get();
    }
    return true;
  }
}
