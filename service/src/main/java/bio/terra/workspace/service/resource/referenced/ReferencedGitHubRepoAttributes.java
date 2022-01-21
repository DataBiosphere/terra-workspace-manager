package bio.terra.workspace.service.resource.referenced;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

public class ReferencedGitHubRepoAttributes {

  private final String gitUrl;

  @JsonCreator
  public ReferencedGitHubRepoAttributes(
      @JsonProperty("gitUrl") String gitUrl) {
    this.gitUrl = gitUrl;
  }

  public String getGitUrl() {
    return gitUrl;
  }
}
