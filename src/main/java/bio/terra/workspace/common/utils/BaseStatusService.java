package bio.terra.workspace.common.utils;

import bio.terra.workspace.generated.model.SystemStatus;
import bio.terra.workspace.generated.model.SystemStatusSystems;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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

  private ConcurrentHashMap<String, StatusSubsystem> subsystems;
  private long lastUpdatedTimestampMillis;
  private SystemStatus currentStatus;
  private long staleThresholdMillis;

  public BaseStatusService(long staleThresholdMillis) {
    subsystems = new ConcurrentHashMap<>();
    currentStatus = new SystemStatus().ok(false);
    lastUpdatedTimestampMillis = 0;
    this.staleThresholdMillis = staleThresholdMillis;
  }

  protected void registerSubsystem(String name, StatusSubsystem subsystem) {
    subsystems.put(name, subsystem);
  }

  @Scheduled(fixedDelayString = "${status-check-frequency.in.milliseconds}")
  public void checkSubsystems() {
    // SystemStatus uses the thread-unsafe HashMap to hold SystemStatusSystems objects by default.
    // Instead of calling putSystemsItems from multiple threads, we safely construct a subsystem
    // status map here and then pass the complete map.
    ConcurrentHashMap<String, SystemStatusSystems> tmpSubsystemStatusMap =
        new ConcurrentHashMap<>();
    AtomicBoolean systemOk = new AtomicBoolean(true);
    subsystems.forEach(
        /*parallelismThreshold=*/ 1,
        (name, subsystem) -> {
          SystemStatusSystems subsystemStatus = null;
          try {
            subsystemStatus = subsystem.getStatusCheckFn().get();
          } catch (Exception e) {
            subsystemStatus =
                new SystemStatusSystems()
                    .ok(false)
                    .addMessagesItem("Error checking status: " + e.getLocalizedMessage());
          }
          tmpSubsystemStatusMap.put(name, subsystemStatus);
          if (subsystem.isCritical() && !subsystemStatus.getOk()) {
            systemOk.set(false);
          }
        });
    lastUpdatedTimestampMillis = System.currentTimeMillis();
    Date lastUpdatedDate = new Date(lastUpdatedTimestampMillis);
    tmpSubsystemStatusMap.put(
        "Staleness",
        new SystemStatusSystems()
            .ok(true)
            .addMessagesItem("Systems last checked " + lastUpdatedDate.toString()));
    currentStatus.ok(systemOk.get()).setSystems(tmpSubsystemStatusMap);
  }

  public SystemStatus getCurrentStatus() {
    if (System.currentTimeMillis() - lastUpdatedTimestampMillis > staleThresholdMillis) {
      Date lastCheckDate = new Date(lastUpdatedTimestampMillis);
      String timeoutMessage =
          "Subsystem status has not been checked since "
              + lastCheckDate.toString()
              + ", exceeding deadline of "
              + staleThresholdMillis
              + " ms.";
      return new SystemStatus()
          .ok(false)
          .putSystemsItem(
              "Staleness", new SystemStatusSystems().ok(false).addMessagesItem(timeoutMessage));
    }
    return currentStatus;
  }
}
