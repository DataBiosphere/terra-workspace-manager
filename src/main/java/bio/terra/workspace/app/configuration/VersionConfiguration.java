package bio.terra.workspace.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/** Read from the version.properties file auto-generated at build time */
@Configuration
@PropertySource("classpath:/generated/version.properties")
@ConfigurationProperties(prefix = "version")
public class VersionConfiguration {
  private String gitHash;
  private String gitTag;
  private String build;

  public String getGitHash() {
    return gitHash;
  }

  public void setGitHash(String gitHash) {
    this.gitHash = gitHash;
  }

  public String getGitTag() {
    return gitTag;
  }

  public void setGitTag(String gitTag) {
    this.gitTag = gitTag;
  }

  public String getBuild() {
    return build;
  }

  public void setBuild(String build) {
    this.build = build;
  }
}
