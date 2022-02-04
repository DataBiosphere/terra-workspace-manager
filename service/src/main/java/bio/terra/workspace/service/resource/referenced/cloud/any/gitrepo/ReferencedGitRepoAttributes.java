package bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReferencedGitRepoAttributes {

  private final String gitRepoUrl;

  @JsonCreator
  public ReferencedGitRepoAttributes(@JsonProperty("gitRepoUrl") String gitRepoUrl) {
    this.gitRepoUrl = gitRepoUrl;
  }

  public String getGitRepoUrl() {
    return gitRepoUrl;
  }
}
