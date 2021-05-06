package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.service.spendprofile.SpendProfileId;
import com.google.auto.value.AutoValue;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal representation of a request to create a workspace.
 *
 * <p>While this object is nearly identical to the {@code Workspace} object, there is a semantic
 * difference: a {@code Workspace} is a fully formed workspace, while a {@code WorkspaceRequest}
 * only contains fields specified by clients.
 */
@AutoValue
public abstract class WorkspaceRequest {

  /** The globally unique identifier of this workspace */
  public abstract UUID workspaceId();

  /**
   * An ID used for idempotency. WorkspaceRequests with matching jobIds are considered duplicate
   * requests.
   */
  public abstract String jobId();

  /**
   * The spend profile ID associated with this project, if one exists.
   *
   * <p>In the future, this will correlate to a spend profile in the Spend Profile Manager. For now,
   * it's just a unique identifier. To associate a GCP project with a workspace, the workspace must
   * have a spend profile. They are not needed otherwise.
   */
  public abstract Optional<SpendProfileId> spendProfileId();

  /** Temporary feature flag indicating whether this workspace uses MC Terra features. */
  public abstract WorkspaceStage workspaceStage();

  public abstract Optional<String> displayName();

  public abstract Optional<String> description();

  public static WorkspaceRequest.Builder builder() {
    return new AutoValue_WorkspaceRequest.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract WorkspaceRequest.Builder workspaceId(UUID value);

    public abstract WorkspaceRequest.Builder jobId(String value);

    public abstract WorkspaceRequest.Builder spendProfileId(Optional<SpendProfileId> value);

    public abstract WorkspaceRequest.Builder workspaceStage(WorkspaceStage value);

    public abstract WorkspaceRequest.Builder displayName(Optional<String> value);

    public abstract WorkspaceRequest.Builder description(Optional<String> value);

    public abstract WorkspaceRequest build();
  }
}
