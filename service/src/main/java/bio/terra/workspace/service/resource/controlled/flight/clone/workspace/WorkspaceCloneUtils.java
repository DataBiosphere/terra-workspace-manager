package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.referenced.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.ReferencedGcsObjectResource;
import bio.terra.workspace.service.resource.referenced.ReferencedGitHubRepoResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResource;
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
      UUID destinationWorkspaceId,
      String name,
      String description) {
    final ReferencedResource destinationResource;
    switch (sourceReferencedResource.getResourceType()) {
      case GCS_BUCKET:
        destinationResource =
            buildDestinationGcsBucketReference(
                sourceReferencedResource.castToGcsBucketResource(),
                destinationWorkspaceId,
                name,
                description);
        break;
      case GCS_OBJECT:
        destinationResource =
            buildDestinationGcsObjectReference(
                sourceReferencedResource.castToGcsObjectResource(),
                destinationWorkspaceId,
                name,
                description);
        break;
      case DATA_REPO_SNAPSHOT:
        destinationResource =
            buildDestinationDataRepoSnapshotReference(
                sourceReferencedResource.castToDataRepoSnapshotResource(),
                destinationWorkspaceId,
                name,
                description);
        break;
      case BIG_QUERY_DATASET:
        destinationResource =
            buildDestinationBigQueryDatasetReference(
                sourceReferencedResource.castToBigQueryDatasetResource(),
                destinationWorkspaceId,
                name,
                description);
        break;
      case BIG_QUERY_DATA_TABLE:
        destinationResource =
            buildDestinationBigQueryDataTableReference(
                sourceReferencedResource.castToBigQueryDataTableResource(),
                destinationWorkspaceId,
                name,
                description);
        break;
      case GITHUB_REPO:
        destinationResource =
            buildDestinationGitHubRepoReference(
                sourceReferencedResource.castToGitHubRepoResource(),
                destinationWorkspaceId,
                name,
                description);
        break;
      case AI_NOTEBOOK_INSTANCE:
      default:
        throw new BadRequestException(
            String.format(
                "Resource type %s not supported",
                sourceReferencedResource.getResourceType().toString()));
    }
    return destinationResource;
  }

  /**
   * Create an input object for the clone of a reference, which is identical in all fields except
   * workspace ID, resource ID, and (possibly) name and description.
   *
   * @param sourceBucketResource - original resource to be cloned
   * @param destinationWorkspaceId - workspace ID for new reference
   * @param name - resource name for cloned reference. Will use original name if this is null.
   * @param description - resource description for cloned reference. Uses original if left null.
   * @return
   */
  private static ReferencedResource buildDestinationGcsBucketReference(
      ReferencedGcsBucketResource sourceBucketResource,
      UUID destinationWorkspaceId,
      @Nullable String name,
      @Nullable String description) {

    final ReferencedGcsBucketResource.Builder resultBuilder =
        sourceBucketResource.toBuilder()
            .workspaceId(destinationWorkspaceId)
            .resourceId(UUID.randomUUID());
    // apply optional override variables
    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationGcsObjectReference(
      ReferencedGcsObjectResource sourceBucketFileResource,
      UUID destinationWorkspaceId,
      @Nullable String name,
      @Nullable String description) {
    final ReferencedGcsObjectResource.Builder resultBuilder =
        sourceBucketFileResource.toBuilder()
            .workspaceId(destinationWorkspaceId)
            .resourceId(UUID.randomUUID());
    // apply optional override variables
    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationBigQueryDatasetReference(
      ReferencedBigQueryDatasetResource sourceBigQueryResource,
      UUID destinationWorkspaceId,
      @Nullable String name,
      @Nullable String description) {
    // keep projectId and dataset name the same since they are for the referent
    final ReferencedBigQueryDatasetResource.Builder resultBuilder =
        sourceBigQueryResource.toBuilder()
            .workspaceId(destinationWorkspaceId)
            .resourceId(UUID.randomUUID());
    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationBigQueryDataTableReference(
      ReferencedBigQueryDataTableResource sourceBigQueryResource,
      UUID destinationWorkspaceId,
      @Nullable String name,
      @Nullable String description) {
    // keep projectId, dataset name and data table name the same since they are for the referent
    final ReferencedBigQueryDataTableResource.Builder resultBuilder =
        sourceBigQueryResource.toBuilder()
            .workspaceId(destinationWorkspaceId)
            .resourceId(UUID.randomUUID());
    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationDataRepoSnapshotReference(
      ReferencedDataRepoSnapshotResource sourceReferencedDataRepoSnapshotResource,
      UUID destinationWorkspaceId,
      @Nullable String name,
      @Nullable String description) {
    final ReferencedDataRepoSnapshotResource.Builder resultBuilder =
        sourceReferencedDataRepoSnapshotResource.toBuilder()
            .workspaceId(destinationWorkspaceId)
            .resourceId(UUID.randomUUID());
    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationGitHubRepoReference(
      ReferencedGitHubRepoResource gitHubRepoResource,
      UUID destinationWorkspaceId,
      @Nullable String name,
      @Nullable String description) {
    ReferencedGitHubRepoResource.Builder resultBuilder =
        gitHubRepoResource.toBuilder()
            .workspaceId(destinationWorkspaceId)
            .resourceId(UUID.randomUUID());
    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }
}
