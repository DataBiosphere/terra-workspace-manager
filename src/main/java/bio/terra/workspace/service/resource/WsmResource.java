package bio.terra.workspace.service.resource;

import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.referenced.ReferencedResource;
import com.google.common.base.Strings;
import java.util.UUID;

/**
 * Top-level class for a Resource. Children of this class can be controlled resources, references,
 * or (future) monitored resources.
 */
public abstract class WsmResource {
  private final UUID workspaceId;
  private final UUID resourceId;
  private final String name;
  private final String description;
  private final CloningInstructions cloningInstructions;

  /**
   * construct from individual fields
   *
   * @param workspaceId unique identifier of the workspace where this resource lives (or is going to
   *     live)
   * @param resourceId unique identifier of the resource
   * @param name resource name; unique within a workspace
   * @param description free-form text description of the resource
   * @param cloningInstructions how to treat the resource when cloning the workspace
   */
  public WsmResource(
      UUID workspaceId,
      UUID resourceId,
      String name,
      String description,
      CloningInstructions cloningInstructions) {
    this.workspaceId = workspaceId;
    this.resourceId = resourceId;
    this.name = name;
    this.description = description;
    this.cloningInstructions = cloningInstructions;
  }

  /** construct from database data */
  public WsmResource(DbResource dbResource) {
    this(
        dbResource.getWorkspaceId(),
        dbResource.getResourceId(),
        dbResource.getName().orElse(null),
        dbResource.getDescription().orElse(null),
        dbResource.getCloningInstructions());
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getResourceId() {
    return resourceId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public CloningInstructions getCloningInstructions() {
    return cloningInstructions;
  }

  /**
   * Sub-classes must identify their stewardship type
   *
   * @return stewardship type
   */
  public abstract StewardshipType getStewardshipType();

  /**
   * Sub-classes must identify their resource type
   *
   * @return resource type
   */
  public abstract WsmResourceType getResourceType();

  /**
   * Attributes string, serialized as JSON. Includes only those attributes of the cloud resource
   * that are necessary for identification. The structure of the cloud resource attributes can be
   * whatever is useful for the resource. It need not be a flat POJO.
   *
   * @return json string
   */
  public abstract String attributesToJson();

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
        || getResourceId() == null) {
      throw new MissingRequiredFieldException("Missing required field for WsmResource.");
    }
  }

  public ReferencedResource castToReferenceResource() {
    if (getStewardshipType() != StewardshipType.REFERENCED) {
      throw new InvalidMetadataException("Resource is not a referenced resource");
    }
    return (ReferencedResource) this;
  }

  public ControlledResource castToControlledResource() {
    if (getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InvalidMetadataException("Resource is not a controlled resource");
    }
    return (ControlledResource) this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    WsmResource that = (WsmResource) o;

    if (workspaceId != null ? !workspaceId.equals(that.workspaceId) : that.workspaceId != null)
      return false;
    if (resourceId != null ? !resourceId.equals(that.resourceId) : that.resourceId != null)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null) return false;
    if (description != null ? !description.equals(that.description) : that.description != null)
      return false;
    return cloningInstructions == that.cloningInstructions;
  }

  @Override
  public int hashCode() {
    int result = workspaceId != null ? workspaceId.hashCode() : 0;
    result = 31 * result + (resourceId != null ? resourceId.hashCode() : 0);
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + (cloningInstructions != null ? cloningInstructions.hashCode() : 0);
    return result;
  }
}
