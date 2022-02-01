package bio.terra.workspace.app.configuration.external;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "reference.gitrepos")
public class GitRepoReferencedResourceConfiguration {
  private List<String> allowListedGitRepoHostNames;

  public void setAllowListedGitRepoHostNames(List<String> gitRepoHostNames) {
    this.allowListedGitRepoHostNames = gitRepoHostNames;
  }

  public List<String> getAllowListedGitRepoHostName() {
    return allowListedGitRepoHostNames;
  }
}
