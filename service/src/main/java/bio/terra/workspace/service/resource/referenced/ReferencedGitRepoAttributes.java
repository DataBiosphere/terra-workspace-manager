package bio.terra.workspace.service.resource.referenced;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReferencedGitRepoAttributes {

  private final String gitCloneUrl;

  @JsonCreator
  public ReferencedGitRepoAttributes(@JsonProperty("gitCloneUrl") String gitCloneUrl) {
    this.gitCloneUrl = gitCloneUrl;
  }

  public String getGitCloneUrl() {
    return gitCloneUrl;
  }
}
