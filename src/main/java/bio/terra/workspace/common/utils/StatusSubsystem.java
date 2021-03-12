package bio.terra.workspace.common.utils;

import bio.terra.workspace.generated.model.ApiSystemStatusSystems;
import java.util.function.Supplier;

/*
  StatusSubsystem is a POJO for representing a dependency of the current system used in checking
  server status.

  isCritical indicates whether this is a critical subsystem or not. If a subsystem is critical,
  a non-ok subsystem status leads to a non-ok status for the system as a whole. If a subsystem is
  not critical, the system may have an ok status despite the issue with the subsystem.

  statusCheckFn is the function called to get the subsystem's status.

*/
public class StatusSubsystem {

  private Supplier<ApiSystemStatusSystems> statusCheckFn;
  private boolean isCritical;

  public StatusSubsystem(Supplier<ApiSystemStatusSystems> statusCheckFn, boolean isCritical) {
    this.statusCheckFn = statusCheckFn;
    this.isCritical = isCritical;
  }

  public Supplier<ApiSystemStatusSystems> getStatusCheckFn() {
    return statusCheckFn;
  }

  public void setStatusCheckFn(Supplier<ApiSystemStatusSystems> statusCheckFn) {
    this.statusCheckFn = statusCheckFn;
  }

  public boolean isCritical() {
    return isCritical;
  }

  public void setCritical(boolean critical) {
    isCritical = critical;
  }
}
