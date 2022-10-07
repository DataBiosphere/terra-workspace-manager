package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.clearSomePropertiesForResourceCloningToDifferentWorkspace;

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
import java.util.Map;
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
      case REFERENCED_GCP_GCS_BUCKET -> buildDestinationGcsBucketReference(
          sourceReferencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET),
          destinationWorkspaceId,
          destinationResourceId,
          name,
          description);
      case REFERENCED_GCP_GCS_OBJECT -> buildDestinationGcsObjectReference(
          sourceReferencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT),
          destinationWorkspaceId,
          destinationResourceId,
          name,
          description);
      case REFERENCED_ANY_DATA_REPO_SNAPSHOT -> buildDestinationDataRepoSnapshotReference(
          sourceReferencedResource.castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT),
          destinationWorkspaceId,
          destinationResourceId,
          name,
          description);
      case REFERENCED_GCP_BIG_QUERY_DATASET -> buildDestinationBigQueryDatasetReference(
          sourceReferencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET),
          destinationWorkspaceId,
          destinationResourceId,
          name,
          description);
      case REFERENCED_GCP_BIG_QUERY_DATA_TABLE -> buildDestinationBigQueryDataTableReference(
          sourceReferencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE),
          destinationWorkspaceId,
          destinationResourceId,
          name,
          description);
      case REFERENCED_ANY_GIT_REPO -> buildDestinationGitHubRepoReference(
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
      String destinationProjectId,
      @Nullable Map<String, String> properties) {
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
                destinationResourceLineage,
                properties))
        .build();
  }

  public static ControlledGcsBucketResource buildDestinationControlledGcsBucket(
      ControlledGcsBucketResource sourceBucket,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      String name,
      @Nullable String description,
      String cloudInstanceName,
      @Nullable Map<String, String> properties) {
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
                destinationResourceLineage,
                properties))
        .build();
  }

  private static ControlledResourceFields getControlledResourceCommonFields(
      ControlledResource sourceResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      String name,
      String description,
      List<ResourceLineageEntry> destinationResourceLineage,
      @Nullable Map<String, String> properties) {
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
        .properties(
            maybeClearSomeResourcePropertiesBeforeCloning(
                sourceResource, properties, destinationWorkspaceId))
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
            .wsmResourceFields(
                buildDestinationResourceCommonFields(
                    destinationWorkspaceId,
                    destinationResourceId,
                    name,
                    description,
                    destinationResourceLineage,
                    sourceBucketResource));

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
            .wsmResourceFields(
                buildDestinationResourceCommonFields(
                    destinationWorkspaceId,
                    destinationResourceId,
                    name,
                    description,
                    destinationResourceLineage,
                    sourceBucketFileResource));
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
            .wsmResourceFields(
                buildDestinationResourceCommonFields(
                    destinationWorkspaceId,
                    destinationResourceId,
                    name,
                    description,
                    destinationResourceLineage,
                    sourceBigQueryResource));
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
            .wsmResourceFields(
                buildDestinationResourceCommonFields(
                    destinationWorkspaceId,
                    destinationResourceId,
                    name,
                    description,
                    destinationResourceLineage,
                    sourceBigQueryResource));
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
            .wsmResourceFields(
                buildDestinationResourceCommonFields(
                    destinationWorkspaceId,
                    destinationResourceId,
                    name,
                    description,
                    destinationResourceLineage,
                    sourceReferencedDataRepoSnapshotResource));
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
            .wsmResourceFields(
                buildDestinationResourceCommonFields(
                    destinationWorkspaceId,
                    destinationResourceId,
                    name,
                    description,
                    destinationResourceLineage,
                    gitHubRepoResource));
    return resultBuilder.build();
  }

  private static WsmResourceFields buildDestinationResourceCommonFields(
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable String name,
      @Nullable String description,
      List<ResourceLineageEntry> destinationResourceLineage,
      WsmResource wsmResource) {
    WsmResourceFields.Builder<?> destinationResourceCommonFieldsBuilder =
        wsmResource.getWsmResourceFields().toBuilder();
    destinationResourceCommonFieldsBuilder
        .workspaceUuid(destinationWorkspaceId)
        .resourceId(destinationResourceId)
        .properties(
            maybeClearSomeResourcePropertiesBeforeCloning(
                wsmResource, wsmResource.getProperties(), destinationWorkspaceId))
        .resourceLineage(destinationResourceLineage);
    // apply optional override variables
    Optional.ofNullable(name).ifPresent(destinationResourceCommonFieldsBuilder::name);
    Optional.ofNullable(description).ifPresent(destinationResourceCommonFieldsBuilder::description);
    return destinationResourceCommonFieldsBuilder.build();
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

  private static Map<String, String> maybeClearSomeResourcePropertiesBeforeCloning(
      WsmResource sourceResource,
      Map<String, String> destinationProperties,
      UUID destinationWorkspaceId) {
    Map<String, String> destinationResourceProperties = destinationProperties;
    if (!destinationWorkspaceId.equals(sourceResource.getWorkspaceId())) {
      destinationResourceProperties =
          clearSomePropertiesForResourceCloningToDifferentWorkspace(destinationResourceProperties);
    }
    return destinationResourceProperties;
  }
}
