package bio.terra.workspace.service.resource.referenced;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReferencedGitRepoAttributes {

  private final String gitUrl;

  @JsonCreator
  public ReferencedGitRepoAttributes(@JsonProperty("gitUrl") String gitUrl) {
    this.gitUrl = gitUrl;
  }

  public String getGitUrl() {
    return gitUrl;
  }
}
