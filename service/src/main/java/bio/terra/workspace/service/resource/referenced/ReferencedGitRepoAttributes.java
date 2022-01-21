package bio.terra.workspace.service.resource.referenced;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

public class ReferencedGitRepoAttributes {

  private final String gitUrl;

  @JsonCreator
  public ReferencedGitRepoAttributes(
      @JsonProperty("gitUrl") String gitUrl) {
    this.gitUrl = gitUrl;
  }

  public String getGitUrl() {
    return gitUrl;
  }
}
