package bio.terra.workspace.service.workspace;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/**
 * The cloud provider and its associated resource for a workspace.
 *
 * <p>Each workspace has resources in one cloud provider, its "cloud context."
 * <li>For GCP, a Google Project is associated with every resource.
 */
@AutoValue
public abstract class WorkspaceCloudContext {
  public abstract CloudType cloudType();

  /** The Google Project id. Only present if the provider is {@link CloudType#GOOGLE}. */
  public abstract Optional<String> googleProjectId();

  public static WorkspaceCloudContext createGoogleContext(String projectId) {
    return new AutoValue_WorkspaceCloudContext(CloudType.GOOGLE, Optional.of(projectId));
  }

  public static WorkspaceCloudContext none() {
    return new AutoValue_WorkspaceCloudContext(CloudType.NONE, Optional.empty());
  }
}
