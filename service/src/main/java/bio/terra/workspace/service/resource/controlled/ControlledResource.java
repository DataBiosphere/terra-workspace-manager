package bio.terra.workspace.service.resource.controlled;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiControlledResourceMetadata;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.generated.model.ApiResourceMetadata;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import java.util.Objects;
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
  private final UUID applicationId;

  public ControlledResource(
      UUID workspaceId,
      UUID resourceId,
      String name,
      String description,
      CloningInstructions cloningInstructions,
      String assignedUser,
      AccessScopeType accessScope,
      ManagedByType managedBy,
      UUID applicationId) {
    super(workspaceId, resourceId, name, description, cloningInstructions);
    this.assignedUser = assignedUser;
    this.accessScope = accessScope;
    this.managedBy = managedBy;
    this.applicationId = applicationId;
  }

  public ControlledResource(DbResource dbResource) {
    super(dbResource);
    if (dbResource.getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InvalidMetadataException("Expected CONTROLLED");
    }
    this.assignedUser = dbResource.getAssignedUser().orElse(null);
    this.accessScope = dbResource.getAccessScope().orElse(null);
    this.managedBy = dbResource.getManagedBy().orElse(null);
    this.applicationId = dbResource.getApplicationId().orElse(null);
  }

  @Override
  public StewardshipType getStewardshipType() {
    return StewardshipType.CONTROLLED;
  }

  /**
   * If specified, the assigned user must be equal to the user making the request.
   *
   * @return user email address for assignee, if any
   */
  public Optional<String> getAssignedUser() {
    return Optional.ofNullable(assignedUser);
  }

  public AccessScopeType getAccessScope() {
    return accessScope;
  }

  public ManagedByType getManagedBy() {
    return managedBy;
  }

  public UUID getApplicationId() {
    return applicationId;
  }

  public ControlledResourceCategory getCategory() {
    return ControlledResourceCategory.get(accessScope, managedBy);
  }

  @Override
  public ApiResourceMetadata toApiMetadata() {
    ApiResourceMetadata metadata = super.toApiMetadata();
    var controlled =
        new ApiControlledResourceMetadata()
            .accessScope(accessScope.toApiModel())
            .managedBy(managedBy.toApiModel())
            .privateResourceUser(
                // TODO: PF-616 figure out how to supply the assigned user's role
                new ApiPrivateResourceUser().userName(assignedUser));
    metadata.controlledResourceMetadata(controlled);
    return metadata;
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

    if (getApplicationId() != null && getManagedBy() != ManagedByType.MANAGED_BY_APPLICATION) {
      throw new InconsistentFieldsException(
          "Application managed resource without an application id");
    }
  }

  // Double-checked down casts when we need to re-specialize from a ControlledResource
  public ControlledGcsBucketResource castToGcsBucketResource() {
    validateSubclass(WsmResourceType.GCS_BUCKET);
    return (ControlledGcsBucketResource) this;
  }

  public ControlledAzureIpResource castToAzureIpResource() {
    validateSubclass(WsmResourceType.AZURE_IP);
    return (ControlledAzureIpResource) this;
  }

  public ControlledAzureDiskResource castToAzureDiskResource() {
    validateSubclass(WsmResourceType.AZURE_DISK);
    return (ControlledAzureDiskResource) this;
  }

  public ControlledAiNotebookInstanceResource castToAiNotebookInstanceResource() {
    validateSubclass(WsmResourceType.AI_NOTEBOOK_INSTANCE);
    return (ControlledAiNotebookInstanceResource) this;
  }

  public ControlledBigQueryDatasetResource castToBigQueryDatasetResource() {
    validateSubclass(WsmResourceType.BIG_QUERY_DATASET);
    return (ControlledBigQueryDatasetResource) this;
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

    if (!Objects.equals(assignedUser, that.assignedUser)) return false;
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
