package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.clearSomePropertiesForResourceCloningToDifferentWorkspace;
import static bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties.FOLDER_ID_KEY;

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
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
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
      @Nullable UUID destinationFolderId,
      String name,
      @Nullable String description,
      String cloudInstanceName,
      String destinationProjectId) {
    return ControlledBigQueryDatasetResource.builder()
        .projectId(destinationProjectId)
        .datasetName(cloudInstanceName)
        .common(
            getControlledResourceCommonFields(
                sourceDataset,
                destinationWorkspaceId,
                destinationResourceId,
                destinationFolderId,
                name,
                description))
        .build();
  }

  public static ControlledGcsBucketResource buildDestinationControlledGcsBucket(
      ControlledGcsBucketResource sourceBucket,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId,
      String name,
      @Nullable String description,
      String cloudInstanceName) {
    return ControlledGcsBucketResource.builder()
        .bucketName(cloudInstanceName)
        .common(
            getControlledResourceCommonFields(
                sourceBucket,
                destinationWorkspaceId,
                destinationResourceId,
                destinationFolderId,
                name,
                description))
        .build();
  }

  private static ControlledResourceFields getControlledResourceCommonFields(
      ControlledResource sourceResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId,
      String name,
      String description) {
    List<ResourceLineageEntry> destinationResourceLineage =
        buildDestinationResourceLineage(
            sourceResource.getResourceLineage(),
            sourceResource.getWorkspaceId(),
            sourceResource.getResourceId());
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
                sourceResource, destinationWorkspaceId, destinationFolderId))
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
                    /*destinationFolderId=*/ null,
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
      @Nullable String description) {
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
                /*destinationFolderId=*/ null,
                name,
                description,
                sourceBucketResource)
            .toBuilder()
            .cloningInstructions(destCloningInstructions)
            .build();
    final ReferencedGcsBucketResource.Builder resultBuilder =
        ReferencedGcsBucketResource.builder()
            .wsmResourceFields(wsmResourceFields)
            .bucketName(sourceBucketResource.getBucketName());
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
                    /*destinationFolderId=*/ null,
                    name,
                    description,
                    sourceBucketFileResource));
    return resultBuilder.build();
  }

  private static ReferencedResource buildDestinationReferencedBigQueryDataset(
      ReferencedBigQueryDatasetResource sourceDatasetResource,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable String name,
      @Nullable String description) {
    // keep projectId and dataset name the same since they are for the referent
    final ReferencedBigQueryDatasetResource.Builder resultBuilder =
        sourceDatasetResource.toBuilder()
            .wsmResourceFields(
                buildDestinationResourceCommonFields(
                    destinationWorkspaceId,
                    destinationResourceId,
                    /*destinationFolderId=*/ null,
                    name,
                    description,
                    sourceDatasetResource));
    return resultBuilder.build();
  }

  public static ReferencedBigQueryDatasetResource
      buildDestinationReferencedBigQueryDatasetFromControlled(
          ControlledBigQueryDatasetResource sourceDatasetResource,
          UUID destinationWorkspaceId,
          UUID destinationResourceId,
          @Nullable String name,
          @Nullable String description) {
    CloningInstructions destCloningInstructions =
        // COPY_RESOURCE and COPY_DEFINITION aren't valid for referenced resources, so use
        // COPY_REFERENCE instead.
        sourceDatasetResource.getCloningInstructions() == CloningInstructions.COPY_RESOURCE
                || sourceDatasetResource.getCloningInstructions()
                    == CloningInstructions.COPY_DEFINITION
            ? CloningInstructions.COPY_REFERENCE
            : sourceDatasetResource.getCloningInstructions();

    WsmResourceFields wsmResourceFields =
        buildDestinationResourceCommonFields(
                destinationWorkspaceId,
                destinationResourceId,
                name,
                description,
                sourceDatasetResource)
            .toBuilder()
            .cloningInstructions(destCloningInstructions)
            .build();
    final ReferencedBigQueryDatasetResource.Builder resultBuilder =
        ReferencedBigQueryDatasetResource.builder()
            .wsmResourceFields(wsmResourceFields)
            .projectId(sourceDatasetResource.getProjectId())
            .datasetName(sourceDatasetResource.getDatasetName());
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
                    /*destinationFolderId=*/ null,
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
                    /*destinationFolderId=*/ null,
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
                    /*destinationFolderId=*/ null,
                    name,
                    description,
                    gitHubRepoResource));
    return resultBuilder.build();
  }

  private static WsmResourceFields buildDestinationResourceCommonFields(
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId,
      @Nullable String name,
      @Nullable String description,
      WsmResource sourceResource) {
    List<ResourceLineageEntry> destinationResourceLineage =
        buildDestinationResourceLineage(
            sourceResource.getResourceLineage(),
            sourceResource.getWorkspaceId(),
            sourceResource.getResourceId());
    WsmResourceFields.Builder<?> destinationResourceCommonFieldsBuilder =
        sourceResource.getWsmResourceFields().toBuilder();
    destinationResourceCommonFieldsBuilder
        .workspaceUuid(destinationWorkspaceId)
        .resourceId(destinationResourceId)
        .properties(
            maybeClearSomeResourcePropertiesBeforeCloning(
                sourceResource, destinationWorkspaceId, destinationFolderId))
        .resourceLineage(destinationResourceLineage);
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

  private static Map<String, String> maybeClearSomeResourcePropertiesBeforeCloning(
      WsmResource sourceResource, UUID destinationWorkspaceId, @Nullable UUID destinationFolderId) {
    Map<String, String> destinationResourceProperties = sourceResource.getProperties();
    if (!destinationWorkspaceId.equals(sourceResource.getWorkspaceId())) {
      destinationResourceProperties =
          clearSomePropertiesForResourceCloningToDifferentWorkspace(destinationResourceProperties);
    }
    HashMap<String, String> result = new HashMap<>(destinationResourceProperties);
    if (destinationFolderId != null) {
      result.put(FOLDER_ID_KEY, destinationFolderId.toString());
    }
    return ImmutableMap.copyOf(result);
  }
}
