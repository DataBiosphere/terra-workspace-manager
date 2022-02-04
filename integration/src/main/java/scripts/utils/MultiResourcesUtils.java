package scripts.utils;

import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.DataRepoSnapshotResource;
import bio.terra.workspace.model.GcpBigQueryDataTableAttributes;
import bio.terra.workspace.model.GcpBigQueryDataTableResource;
import bio.terra.workspace.model.GcpBigQueryDatasetAttributes;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpGcsBucketAttributes;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GitRepoAttributes;
import bio.terra.workspace.model.GitRepoResource;
import bio.terra.workspace.model.ResourceMetadata;
import bio.terra.workspace.model.StewardshipType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;

/** Utils for creating and deleting mix kinds of controlled and referenced resources. */
public class MultiResourcesUtils {

  // Support for makeResources
  private static String makeName() {
    return RandomStringUtils.random(10, true, false);
  }

  @FunctionalInterface
  public interface SupplierException<T> {
    T get() throws Exception;
  }

  /**
   * Make a bunch of resources
   *
   * @param referencedGcpResourceApi api for referenced resources
   * @param controlledGcpResourceApi api for controlled resources
   * @param workspaceId workspace where we allocate
   * @param dataRepoSnapshotId ID of the TDR snapshot to use for snapshot references
   * @param dataRepoInstanceName Instance ID to use for snapshot references
   * @param bucket GCS Bucket to use for bucket references
   * @param bqTable BigQuery table to use for BQ dataset and table references.
   * @param gitRepo Git repository to use for git references
   * @param resourceCount number of resources to allocate
   * @return list of resources
   * @throws Exception whatever might come up
   */
  public static List<ResourceMetadata> makeResources(
      ReferencedGcpResourceApi referencedGcpResourceApi,
      ControlledGcpResourceApi controlledGcpResourceApi,
      UUID workspaceId,
      String dataRepoSnapshotId,
      String dataRepoInstanceName,
      GcpGcsBucketAttributes bucket,
      GcpBigQueryDataTableAttributes bqTable,
      GitRepoAttributes gitRepo,
      int resourceCount)
      throws Exception {

    // Array of resource allocators
    List<SupplierException<ResourceMetadata>> makers =
        List.of(
            // BQ dataset reference
            () -> {
              // Use the same BQ dataset specified for table references
              GcpBigQueryDatasetAttributes dataset =
                  new GcpBigQueryDatasetAttributes()
                      .projectId(bqTable.getProjectId())
                      .datasetId(bqTable.getDatasetId());
              GcpBigQueryDatasetResource resource =
                  BqDatasetUtils.makeBigQueryDatasetReference(
                      dataset, referencedGcpResourceApi, workspaceId, makeName());
              return resource.getMetadata();
            },

            // TDR snapshot reference
            () -> {
              DataRepoSnapshotResource resource =
                  DataRepoUtils.makeDataRepoSnapshotReference(
                      referencedGcpResourceApi,
                      workspaceId,
                      makeName(),
                      dataRepoSnapshotId,
                      dataRepoInstanceName);
              return resource.getMetadata();
            },

            // GCS bucket reference
            () -> {
              GcpGcsBucketResource resource =
                  GcsBucketUtils.makeGcsBucketReference(
                      bucket,
                      referencedGcpResourceApi,
                      workspaceId,
                      makeName(),
                      CloningInstructionsEnum.NOTHING);
              return resource.getMetadata();
            },

            // GCS bucket controlled shared
            () -> {
              GcpGcsBucketResource resource =
                  GcsBucketUtils.makeControlledGcsBucketUserShared(
                          controlledGcpResourceApi,
                          workspaceId,
                          makeName(),
                          CloningInstructionsEnum.NOTHING)
                      .getGcpBucket();
              return resource.getMetadata();
            },

            // GCS bucket controlled private
            () -> {
              GcpGcsBucketResource resource =
                  GcsBucketUtils.makeControlledGcsBucketUserPrivate(
                          controlledGcpResourceApi,
                          workspaceId,
                          makeName(),
                          CloningInstructionsEnum.NOTHING)
                      .getGcpBucket();
              return resource.getMetadata();
            },

            // BQ dataset controlled shared
            () -> {
              GcpBigQueryDatasetResource resource =
                  BqDatasetUtils.makeControlledBigQueryDatasetUserShared(
                      controlledGcpResourceApi,
                      workspaceId,
                      makeName(),
                      null,
                      CloningInstructionsEnum.NOTHING);
              return resource.getMetadata();
            },

            // BQ data table reference
            () -> {
              GcpBigQueryDataTableResource resource =
                  BqDatasetUtils.makeBigQueryDataTableReference(
                      bqTable, referencedGcpResourceApi, workspaceId, makeName());
              return resource.getMetadata();
            },

            // GitHub repository reference
            () -> {
              GitRepoResource resource =
                  GitRepoUtils.makeGitRepoReference(
                      gitRepo, referencedGcpResourceApi, workspaceId, makeName());
              return resource.getMetadata();
            });

    // Build the resources
    List<ResourceMetadata> resources = new ArrayList<>();
    for (int i = 0; i < resourceCount; i++) {
      int index = i % makers.size();
      resources.add(makers.get(index).get());
    }
    return resources;
  }

  /**
   * Designed to cleanup the result of allocations from {@link #makeResources}
   *
   * @param resources list of resources to cleanup
   */
  public static void cleanupResources(
      List<ResourceMetadata> resources,
      ControlledGcpResourceApi controlledGcpResourceApi,
      UUID workspaceId)
      throws Exception {
    for (ResourceMetadata metadata : resources) {
      if (metadata.getStewardshipType() == StewardshipType.CONTROLLED) {
        switch (metadata.getResourceType()) {
          case GCS_BUCKET:
            GcsBucketUtils.deleteControlledGcsBucket(
                metadata.getResourceId(), workspaceId, controlledGcpResourceApi);
            break;
          case BIG_QUERY_DATASET:
            controlledGcpResourceApi.deleteBigQueryDataset(workspaceId, metadata.getResourceId());
            break;
          default:
            throw new IllegalStateException(
                String.format(
                    "No cleanup method specified for resource type %s.",
                    metadata.getResourceType()));
        }
      }
    }
  }
}
