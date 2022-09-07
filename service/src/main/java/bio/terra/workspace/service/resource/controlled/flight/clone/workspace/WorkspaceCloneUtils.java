package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
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
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
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
      UUID destinationResourceId,
      String name,
      String description) {
    // ReferenceResource doesn't have Builder, only leaf resources like ReferencedGcsBucketResource
    // have Builder. So each resource type must be built separately.
    final ReferencedResource destinationResource;
    switch (sourceReferencedResource.getResourceType()) {
      case REFERENCED_GCP_GCS_BUCKET:
        destinationResource =
            buildDestinationGcsBucketReference(
                sourceReferencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET),
                destinationWorkspaceId,
                destinationResourceId,
                name,
                description);
        break;
      case REFERENCED_GCP_GCS_OBJECT:
        destinationResource =
            buildDestinationGcsObjectReference(
                sourceReferencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT),
                destinationWorkspaceId,
                destinationResourceId,
                name,
                description);
        break;
      case REFERENCED_ANY_DATA_REPO_SNAPSHOT:
        destinationResource =
            buildDestinationDataRepoSnapshotReference(
                sourceReferencedResource.castByEnum(
                    WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT),
                destinationWorkspaceId,
                destinationResourceId,
                name,
                description);
        break;
      case REFERENCED_GCP_BIG_QUERY_DATASET:
        destinationResource =
            buildDestinationBigQueryDatasetReference(
                sourceReferencedResource.castByEnum(
                    WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET),
                destinationWorkspaceId,
                destinationResourceId,
                name,
                description);
        break;
      case REFERENCED_GCP_BIG_QUERY_DATA_TABLE:
        destinationResource =
            buildDestinationBigQueryDataTableReference(
                sourceReferencedResource.castByEnum(
                    WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE),
                destinationWorkspaceId,
                destinationResourceId,
                name,
                description);
        break;
      case REFERENCED_ANY_GIT_REPO:
        destinationResource =
            buildDestinationGitHubRepoReference(
                sourceReferencedResource.castByEnum(WsmResourceType.REFERENCED_ANY_GIT_REPO),
                destinationWorkspaceId,
                destinationResourceId,
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

  public static ControlledBigQueryDatasetResource buildDestinationControlledBigQueryDataset(
      ControlledBigQueryDatasetResource sourceDataset,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      String name,
      @Nullable String description,
      String cloudInstanceName,
      String destinationProjectId) {
    List<ResourceLineageEntry> destinationResourceLineage =
        createDestinationResourceLineage(
            sourceDataset.getResourceLineage(),
            sourceDataset.getWorkspaceId(),
            sourceDataset.getResourceId());
    return ControlledBigQueryDatasetResource.builder()
        .projectId(destinationProjectId)
        .datasetName(cloudInstanceName)
        .common(
            getControlledResourceCommonFields(
                sourceDataset,
                destinationWorkspaceId,
                destinationResourceId,
                name,
                description,
                destinationResourceLineage))
        .build();
  }

  public static ControlledGcsBucketResource buildDestinationControlledGcsBucket(
      ControlledGcsBucketResource sourceBucket,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      String name,
      @Nullable String description,
      String cloudInstanceName) {
    List<ResourceLineageEntry> destinationResourceLineage =
        createDestinationResourceLineage(
            sourceBucket.getResourceLineage(),
            sourceBucket.getWorkspaceId(),
            sourceBucket.getResourceId());
    return ControlledGcsBucketResource.builder()
        .bucketName(cloudInstanceName)
        .common(
            getControlledResourceCommonFields(
                sourceBucket,
                destinationWorkspaceId,
                destinationResourceId,
                name,
                description,
                destinationResourceLineage))
        .build();
  }

  private static ControlledResourceFields getControlledResourceCommonFields(
      ControlledResource sourceResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      String name,
      String description,
      List<ResourceLineageEntry> destinationResourceLineage) {
    return ControlledResourceFields.builder()
        .accessScope(sourceResource.getAccessScope())
        .assignedUser(sourceResource.getAssignedUser().orElse(null))
        .cloningInstructions(sourceResource.getCloningInstructions())
        .managedBy(sourceResource.getManagedBy())
        .privateResourceState(getPrivateResourceState(sourceResource))
        .name(name)
        .description(description)
        .workspaceUuid(destinationWorkspaceId)
        .resourceId(destinationResourceId)
        .resourceLineage(destinationResourceLineage)
        .properties(sourceResource.getProperties())
        .build();
  }

  private static PrivateResourceState getPrivateResourceState(ControlledResource sourceBucket) {
    var privateResourceState =
        sourceBucket.getAccessScope() == AccessScopeType.ACCESS_SCOPE_PRIVATE
            ? PrivateResourceState.INITIALIZING
            : PrivateResourceState.NOT_APPLICABLE;
    return privateResourceState;
  }

  /**
   * Create an input object for the clone of a reference, which is identical in all fields except
   * workspace ID, resource ID, and (possibly) name and description.
   *
   * @param sourceBucketResource - original resource to be cloned
   * @param destinationWorkspaceId - workspace ID for new reference
   * @param destinationResourceId - resource ID for new reference
   * @param name - resource name for cloned reference. Will use original name if this is null.
   * @param description - resource description for cloned reference. Uses original if left null.
   * @return referenced resource
   */
  private static ReferencedResource buildDestinationGcsBucketReference(
      ReferencedGcsBucketResource sourceBucketResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable String name,
      @Nullable String description) {

    List<ResourceLineageEntry> destinationResourceLineage =
        createDestinationResourceLineage(
            sourceBucketResource.getResourceLineage(),
            sourceBucketResource.getWorkspaceId(),
            sourceBucketResource.getResourceId());
    final ReferencedGcsBucketResource.Builder resultBuilder =
        sourceBucketResource.toBuilder()
            .workspaceId(destinationWorkspaceId)
            .resourceId(destinationResourceId)
            .resourceLineage(destinationResourceLineage);

    // apply optional override variables
    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);

    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationGcsObjectReference(
      ReferencedGcsObjectResource sourceBucketFileResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable String name,
      @Nullable String description) {
    List<ResourceLineageEntry> destinationResourceLineage =
        createDestinationResourceLineage(
            sourceBucketFileResource.getResourceLineage(),
            sourceBucketFileResource.getWorkspaceId(),
            sourceBucketFileResource.getResourceId());
    final ReferencedGcsObjectResource.Builder resultBuilder =
        sourceBucketFileResource.toBuilder()
            .workspaceId(destinationWorkspaceId)
            .resourceId(destinationResourceId)
            .resourceLineage(destinationResourceLineage);

    // apply optional override variables
    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationBigQueryDatasetReference(
      ReferencedBigQueryDatasetResource sourceBigQueryResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable String name,
      @Nullable String description) {
    // keep projectId and dataset name the same since they are for the referent
    List<ResourceLineageEntry> destinationResourceLineage =
        createDestinationResourceLineage(
            sourceBigQueryResource.getResourceLineage(),
            sourceBigQueryResource.getWorkspaceId(),
            sourceBigQueryResource.getResourceId());
    final ReferencedBigQueryDatasetResource.Builder resultBuilder =
        sourceBigQueryResource.toBuilder()
            .workspaceId(destinationWorkspaceId)
            .resourceId(destinationResourceId)
            .resourceLineage(destinationResourceLineage);

    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationBigQueryDataTableReference(
      ReferencedBigQueryDataTableResource sourceBigQueryResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable String name,
      @Nullable String description) {
    // keep projectId, dataset name and data table name the same since they are for the referent
    List<ResourceLineageEntry> destinationResourceLineage =
        createDestinationResourceLineage(
            sourceBigQueryResource.getResourceLineage(),
            sourceBigQueryResource.getWorkspaceId(),
            sourceBigQueryResource.getResourceId());
    final ReferencedBigQueryDataTableResource.Builder resultBuilder =
        sourceBigQueryResource.toBuilder()
            .workspaceId(destinationWorkspaceId)
            .resourceId(destinationResourceId)
            .resourceLineage(destinationResourceLineage);

    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationDataRepoSnapshotReference(
      ReferencedDataRepoSnapshotResource sourceReferencedDataRepoSnapshotResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable String name,
      @Nullable String description) {
    List<ResourceLineageEntry> destinationResourceLineage =
        createDestinationResourceLineage(
            sourceReferencedDataRepoSnapshotResource.getResourceLineage(),
            sourceReferencedDataRepoSnapshotResource.getWorkspaceId(),
            sourceReferencedDataRepoSnapshotResource.getResourceId());
    final ReferencedDataRepoSnapshotResource.Builder resultBuilder =
        sourceReferencedDataRepoSnapshotResource.toBuilder()
            .workspaceId(destinationWorkspaceId)
            .resourceId(destinationResourceId)
            .resourceLineage(destinationResourceLineage);

    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationGitHubRepoReference(
      ReferencedGitRepoResource gitHubRepoResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable String name,
      @Nullable String description) {
    List<ResourceLineageEntry> destinationResourceLineage =
        createDestinationResourceLineage(
            gitHubRepoResource.getResourceLineage(),
            gitHubRepoResource.getWorkspaceId(),
            gitHubRepoResource.getResourceId());
    ReferencedGitRepoResource.Builder resultBuilder =
        gitHubRepoResource.toBuilder()
            .workspaceId(destinationWorkspaceId)
            .resourceId(destinationResourceId)
            .resourceLineage(destinationResourceLineage);

    Optional.ofNullable(name).ifPresent(resultBuilder::name);
    Optional.ofNullable(description).ifPresent(resultBuilder::description);
    return resultBuilder.build();
  }

  @VisibleForTesting
  protected static List<ResourceLineageEntry> createDestinationResourceLineage(
      List<ResourceLineageEntry> sourceResourceLineage,
      UUID sourceWorkspaceId,
      UUID sourceResourceId) {
    // First, if source has resource lineage, copy it over
    List<ResourceLineageEntry> destinationResourceLineage =
        sourceResourceLineage != null ? sourceResourceLineage : new ArrayList<>();
    destinationResourceLineage.add(new ResourceLineageEntry(sourceWorkspaceId, sourceResourceId));
    return destinationResourceLineage;
  }
}
