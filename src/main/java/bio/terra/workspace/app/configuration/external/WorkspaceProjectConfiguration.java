package bio.terra.workspace.app.configuration.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuration for managing how to create GCP projects for workspaces. */
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.project")
public class WorkspaceProjectConfiguration {
  /** What folder id to create workspace projects within, e.g. "866104354540". */
  private String folderId;

  public String getFolderId() {
    return folderId;
  }

  public void setFolderId(String folderId) {
    this.folderId = folderId;
  }
}
