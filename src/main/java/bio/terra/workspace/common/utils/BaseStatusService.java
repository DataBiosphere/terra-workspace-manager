package bio.terra.workspace.common.utils;

import bio.terra.workspace.generated.model.ApiSystemStatus;
import bio.terra.workspace.generated.model.ApiSystemStatusSystems;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/*
 BaseStatusService is a Spring replacement for workbench-libs' HealthMonitor utilities. It checks
 status information from subsystems asynchronously at regular intervals and provides a cached
 version of the latest statuses to support a high-traffic status endpoint.
 It also tracks time since the last update and returns an unhealthy status if subsystems are not
 checked after some amount of time, which indicates that something has gone wrong.

 Specific services should extend this class with Component objects that register the appropriate
 subsystems.
*/
public class BaseStatusService {

  private final ConcurrentHashMap<String, StatusSubsystem> subsystems;
  private long lastUpdatedTimestampMillis;
  private final ApiSystemStatus currentStatus;
  private final long staleThresholdMillis;

  private static final Logger logger = LoggerFactory.getLogger(BaseStatusService.class);

  public BaseStatusService(long staleThresholdMillis) {
    subsystems = new ConcurrentHashMap<>();
    currentStatus = new ApiSystemStatus().ok(false);
    lastUpdatedTimestampMillis = 0;
    this.staleThresholdMillis = staleThresholdMillis;
  }

  protected void registerSubsystem(String name, StatusSubsystem subsystem) {
    subsystems.put(name, subsystem);
  }

  @Scheduled(cron = "${workspace.status-check.cron}")
  public void checkSubsystems() {
    // SystemStatus uses the thread-unsafe HashMap to hold ApiSystemStatusSystems objects by
    // default.
    // Instead of calling putSystemsItems from multiple threads, we safely construct a subsystem
    // status map here and then pass the complete map.
    ConcurrentHashMap<String, ApiSystemStatusSystems> tmpSubsystemStatusMap =
        new ConcurrentHashMap<>();
    AtomicBoolean systemOk = new AtomicBoolean(true);
    subsystems.forEach(
        /*parallelismThreshold=*/ 1,
        (name, subsystem) -> {
          ApiSystemStatusSystems subsystemStatus;
          try {
            subsystemStatus = subsystem.getStatusCheckFn().get();
            subsystemStatus.critical(subsystem.isCritical());
          } catch (Exception e) {
            subsystemStatus =
                new ApiSystemStatusSystems()
                    .ok(false)
                    .critical(subsystem.isCritical())
                    .addMessagesItem("Error checking status: " + e.getLocalizedMessage());
          }
          tmpSubsystemStatusMap.put(name, subsystemStatus);
          if (subsystem.isCritical() && !subsystemStatus.isOk()) {
            systemOk.set(false);
          }
        });
    lastUpdatedTimestampMillis = System.currentTimeMillis();
    Date lastUpdatedDate = new Date(lastUpdatedTimestampMillis);
    tmpSubsystemStatusMap.put(
        "Staleness",
        new ApiSystemStatusSystems()
            .ok(true)
            .addMessagesItem("Systems last checked " + lastUpdatedDate.toString()));
    currentStatus.ok(systemOk.get()).setSystems(tmpSubsystemStatusMap);
    if (!systemOk.get()) {
      logger.warn("WSM status is not ok: {}", currentStatus);
    }
  }

  public ApiSystemStatus getCurrentStatus() {
    if (System.currentTimeMillis() - lastUpdatedTimestampMillis > staleThresholdMillis) {
      Date lastCheckDate = new Date(lastUpdatedTimestampMillis);
      String timeoutMessage =
          "Subsystem status has not been checked since "
              + lastCheckDate.toString()
              + ", exceeding deadline of "
              + staleThresholdMillis
              + " ms.";
      return new ApiSystemStatus()
          .ok(false)
          .putSystemsItem(
              "Staleness", new ApiSystemStatusSystems().ok(false).addMessagesItem(timeoutMessage));
    }
    return currentStatus;
  }
}
