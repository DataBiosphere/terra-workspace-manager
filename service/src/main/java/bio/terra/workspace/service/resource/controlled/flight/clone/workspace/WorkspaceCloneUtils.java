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
import bio.terra.workspace.service.resource.model.WsmResourceFields;
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
    return switch (sourceReferencedResource.getResourceType()) {
      case REFERENCED_GCP_GCS_BUCKET -> buildDestinationReferencedGcsBucket(
          sourceReferencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET),
          destinationWorkspaceId,
          destinationResourceId,
          name,
          description);
      case REFERENCED_GCP_GCS_OBJECT -> buildDestinationReferencedGcsObject(
          sourceReferencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT),
          destinationWorkspaceId,
          destinationResourceId,
          name,
          description);
      case REFERENCED_ANY_DATA_REPO_SNAPSHOT -> buildDestinationReferencedDataRepoSnapshot(
          sourceReferencedResource.castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT),
          destinationWorkspaceId,
          destinationResourceId,
          name,
          description);
      case REFERENCED_GCP_BIG_QUERY_DATASET -> buildDestinationReferencedBigQueryDataset(
          sourceReferencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET),
          destinationWorkspaceId,
          destinationResourceId,
          name,
          description);
      case REFERENCED_GCP_BIG_QUERY_DATA_TABLE -> buildDestinationReferencedBigQueryDataTable(
          sourceReferencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE),
          destinationWorkspaceId,
          destinationResourceId,
          name,
          description);
      case REFERENCED_ANY_GIT_REPO -> buildDestinationReferencedGitHubRepo(
          sourceReferencedResource.castByEnum(WsmResourceType.REFERENCED_ANY_GIT_REPO),
          destinationWorkspaceId,
          destinationResourceId,
          name,
          description);
      default -> throw new BadRequestException(
          String.format(
              "Resource type %s not supported as a referenced resource",
              sourceReferencedResource.getResourceType().toString()));
    };
  }

  public static ControlledBigQueryDatasetResource buildDestinationControlledBigQueryDataset(
      ControlledBigQueryDatasetResource sourceDataset,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      String name,
      @Nullable String description,
      String cloudInstanceName,
      String destinationProjectId) {
    return ControlledBigQueryDatasetResource.builder()
        .projectId(destinationProjectId)
        .datasetName(cloudInstanceName)
        .common(
            getControlledResourceCommonFields(
                sourceDataset, destinationWorkspaceId, destinationResourceId, name, description))
        .build();
  }

  public static ControlledGcsBucketResource buildDestinationControlledGcsBucket(
      ControlledGcsBucketResource sourceBucket,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      String name,
      @Nullable String description,
      String cloudInstanceName) {
    return ControlledGcsBucketResource.builder()
        .bucketName(cloudInstanceName)
        .common(
            getControlledResourceCommonFields(
                sourceBucket, destinationWorkspaceId, destinationResourceId, name, description))
        .build();
  }

  private static ControlledResourceFields getControlledResourceCommonFields(
      ControlledResource sourceResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      String name,
      String description) {
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
        .resourceLineage(
            buildDestinationResourceLineage(
                sourceResource.getResourceLineage(),
                sourceResource.getWorkspaceId(),
                sourceResource.getResourceId()))
        .properties(sourceResource.getProperties())
        .build();
  }

  private static PrivateResourceState getPrivateResourceState(ControlledResource sourceBucket) {
    return sourceBucket.getAccessScope() == AccessScopeType.ACCESS_SCOPE_PRIVATE
        ? PrivateResourceState.INITIALIZING
        : PrivateResourceState.NOT_APPLICABLE;
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
  private static ReferencedResource buildDestinationReferencedGcsBucket(
      ReferencedGcsBucketResource sourceBucketResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable String name,
      @Nullable String description) {

    final ReferencedGcsBucketResource.Builder resultBuilder =
        sourceBucketResource.toBuilder()
            .wsmResourceFields(
                buildDestinationResourceCommonFields(
                    destinationWorkspaceId,
                    destinationResourceId,
                    name,
                    description,
                    sourceBucketResource));

    return resultBuilder.build();
  }

  public static ReferencedGcsBucketResource buildDestinationReferencedGcsBucketFromControlled(
      ControlledGcsBucketResource sourceBucketResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable String name,
      @Nullable String description,
      String bucketName) {
    CloningInstructions destCloningInstructions =
        // COPY_RESOURCE and COPY_DEFINITION aren't valid for referenced resources, so use
        // COPY_REFERENCE instead.
        sourceBucketResource.getCloningInstructions() == CloningInstructions.COPY_RESOURCE
                || sourceBucketResource.getCloningInstructions()
                    == CloningInstructions.COPY_DEFINITION
            ? CloningInstructions.COPY_REFERENCE
            : sourceBucketResource.getCloningInstructions();
    WsmResourceFields wsmResourceFields =
        buildDestinationResourceCommonFields(
                destinationWorkspaceId,
                destinationResourceId,
                name,
                description,
                sourceBucketResource)
            .toBuilder()
            .cloningInstructions(destCloningInstructions)
            .build();
    final ReferencedGcsBucketResource.Builder resultBuilder =
        ReferencedGcsBucketResource.builder()
            .wsmResourceFields(wsmResourceFields)
            .bucketName(bucketName);
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationReferencedGcsObject(
      ReferencedGcsObjectResource sourceBucketFileResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable String name,
      @Nullable String description) {
    final ReferencedGcsObjectResource.Builder resultBuilder =
        sourceBucketFileResource.toBuilder()
            .wsmResourceFields(
                buildDestinationResourceCommonFields(
                    destinationWorkspaceId,
                    destinationResourceId,
                    name,
                    description,
                    sourceBucketFileResource));
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationReferencedBigQueryDataset(
      ReferencedBigQueryDatasetResource sourceBigQueryResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable String name,
      @Nullable String description) {
    // keep projectId and dataset name the same since they are for the referent
    final ReferencedBigQueryDatasetResource.Builder resultBuilder =
        sourceBigQueryResource.toBuilder()
            .wsmResourceFields(
                buildDestinationResourceCommonFields(
                    destinationWorkspaceId,
                    destinationResourceId,
                    name,
                    description,
                    sourceBigQueryResource));
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationReferencedBigQueryDataTable(
      ReferencedBigQueryDataTableResource sourceBigQueryResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable String name,
      @Nullable String description) {
    // keep projectId, dataset name and data table name the same since they are for the referent
    final ReferencedBigQueryDataTableResource.Builder resultBuilder =
        sourceBigQueryResource.toBuilder()
            .wsmResourceFields(
                buildDestinationResourceCommonFields(
                    destinationWorkspaceId,
                    destinationResourceId,
                    name,
                    description,
                    sourceBigQueryResource));
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationReferencedDataRepoSnapshot(
      ReferencedDataRepoSnapshotResource sourceReferencedDataRepoSnapshotResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable String name,
      @Nullable String description) {
    final ReferencedDataRepoSnapshotResource.Builder resultBuilder =
        sourceReferencedDataRepoSnapshotResource.toBuilder()
            .wsmResourceFields(
                buildDestinationResourceCommonFields(
                    destinationWorkspaceId,
                    destinationResourceId,
                    name,
                    description,
                    sourceReferencedDataRepoSnapshotResource));
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationReferencedGitHubRepo(
      ReferencedGitRepoResource gitHubRepoResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable String name,
      @Nullable String description) {
    ReferencedGitRepoResource.Builder resultBuilder =
        gitHubRepoResource.toBuilder()
            .wsmResourceFields(
                buildDestinationResourceCommonFields(
                    destinationWorkspaceId,
                    destinationResourceId,
                    name,
                    description,
                    gitHubRepoResource));
    return resultBuilder.build();
  }

  private static WsmResourceFields buildDestinationResourceCommonFields(
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable String name,
      @Nullable String description,
      WsmResource sourceResource) {
    WsmResourceFields.Builder<?> destinationResourceCommonFieldsBuilder =
        sourceResource.getWsmResourceFields().toBuilder();
    destinationResourceCommonFieldsBuilder
        .workspaceUuid(destinationWorkspaceId)
        .resourceId(destinationResourceId)
        .resourceLineage(
            buildDestinationResourceLineage(
                sourceResource.getResourceLineage(),
                sourceResource.getWorkspaceId(),
                sourceResource.getResourceId()));
    // apply optional override variables
    Optional.ofNullable(name).ifPresent(destinationResourceCommonFieldsBuilder::name);
    Optional.ofNullable(description).ifPresent(destinationResourceCommonFieldsBuilder::description);
    return destinationResourceCommonFieldsBuilder.build();
  }

  @VisibleForTesting
  protected static List<ResourceLineageEntry> buildDestinationResourceLineage(
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
