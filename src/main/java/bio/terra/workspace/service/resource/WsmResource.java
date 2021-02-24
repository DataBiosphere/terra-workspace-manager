package bio.terra.workspace.service.resource;

import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.ReferenceObject;
import com.google.common.base.Strings;
import java.util.Objects;
import java.util.UUID;

/**
 * Top-level class for a Resource. Children of this class can be controlled resources, references,
 * or (future) monitored resources.
 */
public abstract class WsmResource {
  private final String name;
  private final CloningInstructions cloningInstructions;
  private final String description;
  private final UUID workspaceId;

  public WsmResource(
      String name, CloningInstructions cloningInstructions, String description, UUID workspaceId) {
    this.name = name;
    this.cloningInstructions = cloningInstructions;
    this.description = description;
    this.workspaceId = workspaceId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public CloningInstructions getCloningInstructions() {
    return cloningInstructions;
  }

  public abstract StewardshipType getStewardshipType();

  /**
   * Provide something to satisfy the requiremenet of the reference object column in the
   * workspace_data_reference table.
   *
   * @return
   */
  public abstract ReferenceObject getReferenceObject();

  /**
   * Validate the state of to this object. Subclasses should override this method, calling super()
   * first to validate parent class properties (even if those are abstract). This will prevent
   * different resource type concrete classes from repeating the same validation code.
   */
  public void validate() {
    if (Strings.isNullOrEmpty(getName())
        || getWorkspaceId() == null
        || getCloningInstructions() == null
        || getStewardshipType() == null
        || getReferenceObject() == null) {
      throw new IllegalStateException("Missing required field for WsmResource.");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof WsmResource)) {
      return false;
    }
    WsmResource that = (WsmResource) o;
    return Objects.equals(getName(), that.getName())
        && getCloningInstructions() == that.getCloningInstructions()
        && Objects.equals(getDescription(), that.getDescription())
        && Objects.equals(getWorkspaceId(), that.getWorkspaceId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getCloningInstructions(), getDescription(), getWorkspaceId());
  }
}
