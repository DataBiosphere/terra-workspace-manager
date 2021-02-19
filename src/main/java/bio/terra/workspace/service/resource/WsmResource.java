package bio.terra.workspace.service.resource;

import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.ReferenceObject;
import com.google.common.base.Strings;
import java.util.UUID;

/**
 * Top-level class for a Resource, also known as a Data Reference. Children of this class can be
 * controlled resources, references, or (future) monitored resources.
 */
public abstract class WsmResource {
  private final String name;
  private final CloningInstructions cloningInstructions;
  private final String description;
  private final UUID workspaceId;
  private final String owner;

  public WsmResource(
      String name,
      CloningInstructions cloningInstructions,
      String description,
      UUID workspaceId,
      String owner) {
    this.name = name;
    this.cloningInstructions = cloningInstructions;
    this.description = description;
    this.workspaceId = workspaceId;
    this.owner = owner;
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

  public String getOwner() {
    return owner;
  }

  /**
   * Provide something to satisfy the requiremenet of the reference object column in the
   * workspace_data_reference table. TODO: can we get rid of this?
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
        || Strings.isNullOrEmpty(getOwner())
        || getReferenceObject() == null) {
      throw new IllegalStateException("Missing required field for WsmResource.");
    }
  }
}
