package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.common.exception.InconsistentFieldsException;
import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Class for all controlled resource fields that are not common to all resource stewardship types
 * and are not specific to any particular resource type.
 */
public abstract class ControlledResource extends WsmResource {
  private final String assignedUser;
  private final ControlledAccessType controlledAccessType;

  public ControlledResource(
      UUID workspaceId,
      UUID resourceId,
      String name,
      String description,
      CloningInstructions cloningInstructions,
      String assignedUser,
      ControlledAccessType controlledAccessType) {
    super(workspaceId, resourceId, name, description, cloningInstructions);
    this.assignedUser = assignedUser;
    this.controlledAccessType = controlledAccessType;
  }

  public ControlledResource(DbResource dbResource) {
    super(dbResource);
    if (dbResource.getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InvalidMetadataException("Expected CONTROLLED");
    }
    this.assignedUser = dbResource.getAssignedUser().orElse(null);
    this.controlledAccessType = dbResource.getAccessType().orElse(null);
  }

  @Override
  public StewardshipType getStewardshipType() {
    return StewardshipType.CONTROLLED;
  }

  public Optional<String> getAssignedUser() {
    return Optional.ofNullable(assignedUser);
  }

  public ControlledAccessType getControlledAccessType() {
    return controlledAccessType;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() == null
        || attributesToJson() == null
        || getControlledAccessType() == null) {
      throw new MissingRequiredFieldException("Missing required field for ControlledResource.");
    }
    if (getAssignedUser().isPresent()
        && (getControlledAccessType() == ControlledAccessType.APP_SHARED
            || getControlledAccessType() == ControlledAccessType.USER_SHARED)) {
      throw new InconsistentFieldsException("Assigned user on SHARED resource");
    }
  }

  // Double-checked down casts when we need to re-specialize from a ControlledResource
  public ControlledGcsBucketResource castToGcsBucketResource() {
    validateSubclass(WsmResourceType.GCS_BUCKET);
    return (ControlledGcsBucketResource) this;
  }

  private void validateSubclass(WsmResourceType expectedType) {
    if (getResourceType() != expectedType) {
      throw new InvalidMetadataException(
          String.format("Expected %s, found %s", expectedType, getResourceType()));
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    ControlledResource that = (ControlledResource) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(assignedUser, that.assignedUser)
        .append(controlledAccessType, that.controlledAccessType)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(assignedUser)
        .append(controlledAccessType)
        .toHashCode();
  }
}
