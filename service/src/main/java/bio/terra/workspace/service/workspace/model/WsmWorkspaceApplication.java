package bio.terra.workspace.service.workspace.model;

import java.util.UUID;

/** An instance of this class represents the link if any between applications and workspaces. */
public class WsmWorkspaceApplication {
  WsmApplication application;
  // workspace and whether the application is enabled
  private UUID workspaceId;
  private boolean enabled;

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public WsmWorkspaceApplication workspaceId(UUID workspaceId) {
    this.workspaceId = workspaceId;
    return this;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public WsmWorkspaceApplication enabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public WsmApplication getApplication() {
    return application;
  }

  public WsmWorkspaceApplication application(WsmApplication application) {
    this.application = application;
    return this;
  }
}
