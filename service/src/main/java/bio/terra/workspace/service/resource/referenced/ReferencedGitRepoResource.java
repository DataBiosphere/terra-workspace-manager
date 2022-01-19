package bio.terra.workspace.service.resource.referenced;

import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import javax.annotation.Nullable;

public class ReferencedGitRepoResource extends ReferencedResource {

  @JsonCreator
  public ReferencedGitRepoResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("sshUrl") String sshUrl) {
    super(workspaceId, resourceId, name, description, cloningInstructions);
  }

  @Override
  public WsmResourceType getResourceType() {
    return null;
  }

  @Override
  public String attributesToJson() {
    return null;
  }

  @Override
  public boolean checkAccess(FlightBeanBag context, AuthenticatedUserRequest userRequest) {
    return false;
  }
}
