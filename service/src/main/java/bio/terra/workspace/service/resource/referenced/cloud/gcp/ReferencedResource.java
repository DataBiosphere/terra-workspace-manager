package bio.terra.workspace.service.resource.referenced.cloud.gcp;

import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo.ReferencedGitRepoResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsobject.ReferencedGcsObjectResource;
import java.util.UUID;
import javax.annotation.Nullable;

public abstract class ReferencedResource extends WsmResource {
  public ReferencedResource(
      UUID workspaceId,
      UUID resourceId,
      String name,
      @Nullable String description,
      CloningInstructions cloningInstructions) {
    super(workspaceId, resourceId, name, description, cloningInstructions);
  }

  public ReferencedResource(DbResource dbResource) {
    super(dbResource);
    if (dbResource.getStewardshipType() != StewardshipType.REFERENCED) {
      throw new InvalidMetadataException("Expected REFERENCE");
    }
  }

  @Override
  public StewardshipType getStewardshipType() {
    return StewardshipType.REFERENCED;
  }

  // Double-checked down casts when we need to re-specialize from a ReferenceResource
  public ReferencedBigQueryDatasetResource castToBigQueryDatasetResource() {
    validateSubclass(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
    return (ReferencedBigQueryDatasetResource) this;
  }

  public ReferencedBigQueryDataTableResource castToBigQueryDataTableResource() {
    validateSubclass(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
    return (ReferencedBigQueryDataTableResource) this;
  }

  public ReferencedDataRepoSnapshotResource castToDataRepoSnapshotResource() {
    validateSubclass(WsmResourceType.REFERENCED_DATA_REPO_SNAPSHOT);
    return (ReferencedDataRepoSnapshotResource) this;
  }

  public ReferencedGcsBucketResource castToGcsBucketResource() {
    validateSubclass(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
    return (ReferencedGcsBucketResource) this;
  }

  public ReferencedGcsObjectResource castToGcsObjectResource() {
    validateSubclass(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
    return (ReferencedGcsObjectResource) this;
  }

  public ReferencedGitRepoResource castToGitRepoResource() {
    validateSubclass(WsmResourceType.REFERENCED_GIT_REPO);
    return (ReferencedGitRepoResource) this;
  }

  /**
   * Check for a user's access to the resource being referenced. This call should talk to an
   * external service (a cloud platform, Terra Data Repo, etc) specific to the referenced resource
   * type. This call will impersonate a user via the provided credentials.
   *
   * @param context A FlightBeanBag holding Service objects for talking to various external services
   * @param userRequest Credentials of the user to impersonate for validation
   */
  public abstract boolean checkAccess(FlightBeanBag context, AuthenticatedUserRequest userRequest);

  private void validateSubclass(WsmResourceType expectedType) {
    if (getResourceType() != expectedType) {
      throw new InvalidMetadataException(
          String.format("Expected %s, found %s", expectedType, getResourceType()));
    }
  }
}
