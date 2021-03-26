package bio.terra.workspace.service.resource.referenced;

import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiDataReferenceMetadata;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import java.util.UUID;

public abstract class ReferencedResource extends WsmResource {
  public ReferencedResource(
      UUID workspaceId,
      UUID resourceId,
      String name,
      String description,
      CloningInstructions cloningInstructions) {
    super(workspaceId, resourceId, name, description, cloningInstructions);
  }

  public ReferencedResource(DbResource dbResource) {
    super(dbResource);
    if (dbResource.getStewardshipType() != StewardshipType.REFERENCED) {
      throw new InvalidMetadataException("Expected REFERENCE");
    }
  }

  public ApiDataReferenceMetadata toApiMetadata() {
    return new ApiDataReferenceMetadata()
        .referenceId(getResourceId())
        .workspaceId(getWorkspaceId())
        .name(getName())
        .description(getDescription())
        .cloningInstructions(getCloningInstructions().toApiModel());
  }

  @Override
  public StewardshipType getStewardshipType() {
    return StewardshipType.REFERENCED;
  }

  // Double-checked down casts when we need to re-specialize from a ReferenceResource
  public ReferencedBigQueryDatasetResource castToBigQueryDatasetResource() {
    validateSubclass(WsmResourceType.BIG_QUERY_DATASET);
    return (ReferencedBigQueryDatasetResource) this;
  }

  public ReferencedDataRepoSnapshotResource castToDataRepoSnapshotResource() {
    validateSubclass(WsmResourceType.DATA_REPO_SNAPSHOT);
    return (ReferencedDataRepoSnapshotResource) this;
  }

  public ReferencedGcsBucketResource castToGcsBucketResource() {
    validateSubclass(WsmResourceType.GCS_BUCKET);
    return (ReferencedGcsBucketResource) this;
  }

  private void validateSubclass(WsmResourceType expectedType) {
    if (getResourceType() != expectedType) {
      throw new InvalidMetadataException(
          String.format("Expected %s, found %s", expectedType, getResourceType()));
    }
  }
}
