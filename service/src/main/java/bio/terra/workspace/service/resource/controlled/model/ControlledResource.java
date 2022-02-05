package bio.terra.workspace.service.resource.controlled.model;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.db.model.UniquenessCheckParameters;
import bio.terra.workspace.generated.model.ApiControlledResourceMetadata;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.generated.model.ApiResourceMetadata;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Class for all controlled resource fields that are not common to all resource stewardship types
 * and are not specific to any particular resource type.
 */
public abstract class ControlledResource extends WsmResource {
  @Nullable private final String assignedUser;
  private final AccessScopeType accessScope;
  @Nullable private final PrivateResourceState privateResourceState;
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
      UUID applicationId,
      PrivateResourceState privateResourceState) {
    super(workspaceId, resourceId, name, description, cloningInstructions);
    this.assignedUser = assignedUser;
    this.accessScope = accessScope;
    this.managedBy = managedBy;
    this.applicationId = applicationId;
    this.privateResourceState = privateResourceState;
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
    this.privateResourceState = dbResource.getPrivateResourceState().orElse(null);
  }

  /**
   * The ResourceDao calls this method for controlled parameters. The return value
   * describes filtering the DAO should do to verify the uniqueness of the resource.
   * If the return is not present, then no validation check will be performed.
   *
   * @return optional uniqueness description
   */
  public abstract Optional<UniquenessCheckParameters> getUniquenessCheckParameters();

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

  public Optional<PrivateResourceState> getPrivateResourceState() {
    return Optional.ofNullable(privateResourceState);
  }

  public ControlledResourceCategory getCategory() {
    return ControlledResourceCategory.get(accessScope, managedBy);
  }

  @Override
  public StewardshipType getStewardshipType() {
    return StewardshipType.CONTROLLED;
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
                new ApiPrivateResourceUser().userName(assignedUser))
            .privateResourceState(
                getPrivateResourceState().map(PrivateResourceState::toApiModel).orElse(null));
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

    // Non-private resources must have NOT_APPLICABLE private resource state. Private resources can
    // have any of the private resource states, including NOT_APPLICABLE.
    if (getAccessScope() != AccessScopeType.ACCESS_SCOPE_PRIVATE
        && privateResourceState != PrivateResourceState.NOT_APPLICABLE) {
      throw new InconsistentFieldsException(
          "Private resource state must be NOT_APPLICABLE for all non-private resources.");
    }
  }

  // Double-checked down casts when we need to re-specialize from a ControlledResource
  public ControlledGcsBucketResource castToGcsBucketResource() {
    validateSubclass(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    return (ControlledGcsBucketResource) this;
  }

  public ControlledAzureIpResource castToAzureIpResource() {
    validateSubclass(WsmResourceType.CONTROLLED_AZURE_IP);
    return (ControlledAzureIpResource) this;
  }

  public ControlledAzureStorageResource castToAzureStorageResource() {
    validateSubclass(WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT);
    return (ControlledAzureStorageResource) this;
  }

  public ControlledAzureDiskResource castToAzureDiskResource() {
    validateSubclass(WsmResourceType.CONTROLLED_AZURE_DISK);
    return (ControlledAzureDiskResource) this;
  }

  public ControlledAzureNetworkResource castToAzureNetworkResource() {
    validateSubclass(WsmResourceType.CONTROLLED_AZURE_NETWORK);
    return (ControlledAzureNetworkResource) this;
  }

  public ControlledAzureVmResource castToAzureVmResource() {
    validateSubclass(WsmResourceType.CONTROLLED_AZURE_VM);
    return (ControlledAzureVmResource) this;
  }

  public ControlledAiNotebookInstanceResource castToAiNotebookInstanceResource() {
    validateSubclass(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
    return (ControlledAiNotebookInstanceResource) this;
  }

  public ControlledBigQueryDatasetResource castToBigQueryDatasetResource() {
    validateSubclass(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
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
