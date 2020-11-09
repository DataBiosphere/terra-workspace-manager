package bio.terra.workspace.app.configuration.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuration for managing how to set up Google cloud context for workspaces. */
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.google")
public class GoogleWorkspaceConfiguration {
  /** The id of the folder to create workspace projects within, e.g. "866104354540". */
  private String folderId;

  public String getFolderId() {
    return folderId;
  }

  public void setFolderId(String folderId) {
    this.folderId = folderId;
  }
}
