package bio.terra.workspace.service.status;

import java.util.function.Supplier;

/*
  StatusSubsystem is a POJO for representing a system check to be performed to determine
  server status.

  statusCheckFn is the function called to get the subsystem's status.
*/
public class StatusSubsystem {
  private final String name;
  private final Supplier<Boolean> statusCheckFn;

  public StatusSubsystem(String name, Supplier<Boolean> statusCheckFn) {
    this.name = name;
    this.statusCheckFn = statusCheckFn;
  }

  public String getName() {
    return name;
  }

  public Supplier<Boolean> getStatusCheckFn() {
    return statusCheckFn;
  }
}
