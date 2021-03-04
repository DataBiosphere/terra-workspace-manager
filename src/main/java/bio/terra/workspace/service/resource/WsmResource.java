package bio.terra.workspace.service.resource;

import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import com.google.common.base.Strings;
import java.util.UUID;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

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
   * that are necessary for identification.
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    WsmResource that = (WsmResource) o;

    return new EqualsBuilder()
        .append(workspaceId, that.workspaceId)
        .append(resourceId, that.resourceId)
        .append(name, that.name)
        .append(description, that.description)
        .append(cloningInstructions, that.cloningInstructions)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(workspaceId)
        .append(resourceId)
        .append(name)
        .append(description)
        .append(cloningInstructions)
        .toHashCode();
  }
}
