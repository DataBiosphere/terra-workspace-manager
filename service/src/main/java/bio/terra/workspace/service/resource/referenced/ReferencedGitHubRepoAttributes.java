package bio.terra.workspace.service.resource.referenced;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

public class ReferencedGitHubRepoAttributes {

  private final String sshUrl;
  private final String httpsUrl;

  @JsonCreator
  public ReferencedGitHubRepoAttributes(
      @JsonProperty("httpsUrl") String httpsUrl,
      @JsonProperty("sshUrl") @Nullable String sshUrl) {
    this.httpsUrl = httpsUrl;
    this.sshUrl = sshUrl;
  }

  public @Nullable String getSshUrl() {
    return sshUrl;
  }

  public String getHttpsUrl() {
    return httpsUrl;
  }
}
