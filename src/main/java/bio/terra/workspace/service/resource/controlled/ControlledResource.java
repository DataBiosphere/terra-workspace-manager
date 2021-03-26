package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.common.exception.InconsistentFieldsException;
import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.controlled.exception.ControlledResourceNotImplementedException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import java.util.Optional;
import java.util.UUID;

/**
 * Class for all controlled resource fields that are not common to all resource stewardship types
 * and are not specific to any particular resource type.
 */
public abstract class ControlledResource extends WsmResource {
  private final String assignedUser;
  private final AccessScopeType accessScope;
  private final ManagedByType managedBy;

  public ControlledResource(
      UUID workspaceId,
      UUID resourceId,
      String name,
      String description,
      CloningInstructions cloningInstructions,
      String assignedUser,
      AccessScopeType accessScope,
      ManagedByType managedBy) {
    super(workspaceId, resourceId, name, description, cloningInstructions);
    this.assignedUser = assignedUser;
    this.accessScope = accessScope;
    this.managedBy = managedBy;
  }

  public ControlledResource(DbResource dbResource) {
    super(dbResource);
    if (dbResource.getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InvalidMetadataException("Expected CONTROLLED");
    }
    this.assignedUser = dbResource.getAssignedUser().orElse(null);
    this.accessScope = dbResource.getAccessScope().orElse(null);
    this.managedBy = dbResource.getManagedBy().orElse(null);
  }

  @Override
  public StewardshipType getStewardshipType() {
    return StewardshipType.CONTROLLED;
  }

  public Optional<String> getAssignedUser() {
    return Optional.ofNullable(assignedUser);
  }

  public AccessScopeType getAccessScope() {
    return accessScope;
  }

  public ManagedByType getManagedBy() {
    return managedBy;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() == null
        || attributesToJson() == null
        || getAccessScope() == null
        || getManagedBy() == null) {
      throw new MissingRequiredFieldException("Missing required field for ControlledResource.");
    }
    if (getAssignedUser().isPresent() && getAccessScope() == AccessScopeType.ACCESS_SCOPE_SHARED) {
      throw new InconsistentFieldsException("Assigned user on SHARED resource");
    }
    if (getManagedBy() == ManagedByType.MANAGED_BY_APPLICATION) {
      throw new ControlledResourceNotImplementedException(
          "WSM does not support application managed resources yet");
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
    if (!super.equals(o)) return false;

    ControlledResource that = (ControlledResource) o;

    if (assignedUser != null ? !assignedUser.equals(that.assignedUser) : that.assignedUser != null)
      return false;
    if (accessScope != that.accessScope) return false;
    return managedBy == that.managedBy;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (assignedUser != null ? assignedUser.hashCode() : 0);
    result = 31 * result + (accessScope != null ? accessScope.hashCode() : 0);
    result = 31 * result + (managedBy != null ? managedBy.hashCode() : 0);
    return result;
  }
}
