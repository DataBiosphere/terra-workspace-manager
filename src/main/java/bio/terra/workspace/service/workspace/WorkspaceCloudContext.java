package bio.terra.workspace.service.workspace;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

/**
 * The cloud contexts associated with a resource.
 *
 * <p>This uses AutoValue's builder pattern to ensure it's serializable with Jackson. Otherwise,
 * Jackson doesn't understand how to deserialize AutoValue classes.
 *
 * <p>A workspace can have resources with each cloud provider, its "cloud context."
 * <li>For GCP, a Google Project is associated with every resource.
 */
@AutoValue
@JsonSerialize(as = WorkspaceCloudContext.class)
@JsonDeserialize(builder = AutoValue_WorkspaceCloudContext.Builder.class)
public abstract class WorkspaceCloudContext {
  /** The Google Project id for workspaces with Google context. May be null if no project exists. */
  @Nullable
  @JsonProperty("googleProjectId")
  public abstract String googleProjectId();

  // Convenience method for quickly creating an empty WorkspaceCloudContext.
  public static WorkspaceCloudContext none() {
    return builder().googleProjectId(null).build();
  }

  public static WorkspaceCloudContext.Builder builder() {
    return new AutoValue_WorkspaceCloudContext.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    @Nullable
    @JsonProperty("googleProjectId")
    public abstract WorkspaceCloudContext.Builder googleProjectId(String value);

    public abstract WorkspaceCloudContext build();
  }
}
