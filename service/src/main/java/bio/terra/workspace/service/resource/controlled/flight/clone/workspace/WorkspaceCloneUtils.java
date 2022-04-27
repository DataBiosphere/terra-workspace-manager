package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo.ReferencedGitRepoResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsobject.ReferencedGcsObjectResource;
import bio.terra.workspace.service.workspace.model.WsmCloneResourceResult;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

public class WorkspaceCloneUtils {

  private WorkspaceCloneUtils() {}

  public static WsmCloneResourceResult flightStatusToCloneResult(
      FlightStatus subflightStatus, WsmResource resource) {
    switch (subflightStatus) {
      default:
      case WAITING:
      case READY:
      case QUEUED:
      case READY_TO_RESTART:
      case RUNNING:
        throw new IllegalStateException(
            String.format("Unexpected status %s for finished flight", subflightStatus));
      case SUCCESS:
        if (CloningInstructions.COPY_NOTHING == resource.getCloningInstructions()) {
          return WsmCloneResourceResult.SKIPPED;
        } else {
          return WsmCloneResourceResult.SUCCEEDED;
        }
      case ERROR:
      case FATAL:
        return WsmCloneResourceResult.FAILED;
    }
  }

  public static ReferencedResource buildDestinationReferencedResource(
      ReferencedResource sourceReferencedResource,
      UUID destinationWorkspaceUuid,
      String name,
      String description) {
    final ReferencedResource destinationResource;
    switch (sourceReferencedResource.getResourceType()) {
      case REFERENCED_GCP_GCS_BUCKET:
        destinationResource =
            buildDestinationGcsBucketReference(
                sourceReferencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET),
                destinationWorkspaceUuid,
                name,
                description);
        break;
      case REFERENCED_GCP_GCS_OBJECT:
        destinationResource =
            buildDestinationGcsObjectReference(
                sourceReferencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT),
                destinationWorkspaceUuid,
                name,
                description);
        break;
      case REFERENCED_ANY_DATA_REPO_SNAPSHOT:
        destinationResource =
            buildDestinationDataRepoSnapshotReference(
                sourceReferencedResource.castByEnum(
                    WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT),
                destinationWorkspaceUuid,
                name,
                description);
        break;
      case REFERENCED_GCP_BIG_QUERY_DATASET:
        destinationResource =
            buildDestinationBigQueryDatasetReference(
                sourceReferencedResource.castByEnum(
                    WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET),
                destinationWorkspaceUuid,
                name,
                description);
        break;
      case REFERENCED_GCP_BIG_QUERY_DATA_TABLE:
        destinationResource =
            buildDestinationBigQueryDataTableReference(
                sourceReferencedResource.castByEnum(
                    WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE),
                destinationWorkspaceUuid,
                name,
                description);
        break;
      case REFERENCED_ANY_GIT_REPO:
        destinationResource =
            buildDestinationGitHubRepoReference(
                sourceReferencedResource.castByEnum(WsmResourceType.REFERENCED_ANY_GIT_REPO),
                destinationWorkspaceUuid,
                name,
                description);
        break;
      default:
        throw new BadRequestException(
            String.format(
                "Resource type %s not supported as a referenced resource",
                sourceReferencedResource.getResourceType().toString()));
    }
    return destinationResource;
  }

  /**
   * Create an input object for the clone of a reference, which is identical in all fields except
   * workspace ID, resource ID, and (possibly) name and description.
   *
   * @param sourceBucketResource - original resource to be cloned
   * @param destinationWorkspaceUuid - workspace ID for new reference
   * @param name - resource name for cloned reference. Will use original name if this is null.
   * @param description - resource description for cloned reference. Uses original if left null.
   * @return referenced resource
   */
  private static ReferencedResource buildDestinationGcsBucketReference(
      ReferencedGcsBucketResource sourceBucketResource,
      UUID destinationWorkspaceUuid,
      @Nullable String name,
      @Nullable String description) {

    final ReferencedGcsBucketResource.Builder resultBuilder =
        sourceBucketResource.toBuilder()
            .workspaceUuid(destinationWorkspaceUuid)
            .resourceId(UUID.randomUUID());
    // apply optional override variables
    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationGcsObjectReference(
      ReferencedGcsObjectResource sourceBucketFileResource,
      UUID destinationWorkspaceUuid,
      @Nullable String name,
      @Nullable String description) {
    final ReferencedGcsObjectResource.Builder resultBuilder =
        sourceBucketFileResource.toBuilder()
            .workspaceUuid(destinationWorkspaceUuid)
            .resourceId(UUID.randomUUID());
    // apply optional override variables
    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationBigQueryDatasetReference(
      ReferencedBigQueryDatasetResource sourceBigQueryResource,
      UUID destinationWorkspaceUuid,
      @Nullable String name,
      @Nullable String description) {
    // keep projectId and dataset name the same since they are for the referent
    final ReferencedBigQueryDatasetResource.Builder resultBuilder =
        sourceBigQueryResource.toBuilder()
            .workspaceUuid(destinationWorkspaceUuid)
            .resourceId(UUID.randomUUID());
    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationBigQueryDataTableReference(
      ReferencedBigQueryDataTableResource sourceBigQueryResource,
      UUID destinationWorkspaceUuid,
      @Nullable String name,
      @Nullable String description) {
    // keep projectId, dataset name and data table name the same since they are for the referent
    final ReferencedBigQueryDataTableResource.Builder resultBuilder =
        sourceBigQueryResource.toBuilder()
            .workspaceUuid(destinationWorkspaceUuid)
            .resourceId(UUID.randomUUID());
    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationDataRepoSnapshotReference(
      ReferencedDataRepoSnapshotResource sourceReferencedDataRepoSnapshotResource,
      UUID destinationWorkspaceUuid,
      @Nullable String name,
      @Nullable String description) {
    final ReferencedDataRepoSnapshotResource.Builder resultBuilder =
        sourceReferencedDataRepoSnapshotResource.toBuilder()
            .workspaceUuid(destinationWorkspaceUuid)
            .resourceId(UUID.randomUUID());
    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationGitHubRepoReference(
      ReferencedGitRepoResource gitHubRepoResource,
      UUID destinationWorkspaceUuid,
      @Nullable String name,
      @Nullable String description) {
    ReferencedGitRepoResource.Builder resultBuilder =
        gitHubRepoResource.toBuilder()
            .workspaceUuid(destinationWorkspaceUuid)
            .resourceId(UUID.randomUUID());
    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }
}
