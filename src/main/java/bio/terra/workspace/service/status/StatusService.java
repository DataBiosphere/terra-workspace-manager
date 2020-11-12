package bio.terra.workspace.service.status;

import bio.terra.workspace.generated.model.SystemStatus;

/**
 * An interface for a service providing status information about this server.
 *
 * <p>This has two implementations in Workspace Manager. The first, ScheduledStatusService, is used
 * for all deployments of the service. It checks all dependencies on a schedule and reports cached
 * statuses via endpoints.
 *
 * <p>The second, FakeStatusService, is only used for tests and always returns an OK status with no
 * subsystems. This is useful for preventing unexpected interactions between scheduled methods and
 * tests.
 */
public interface StatusService {

  /** Retrieves the current status of the system. */
  public SystemStatus getCurrentStatus();
}
