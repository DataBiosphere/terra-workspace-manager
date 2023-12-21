package scripts.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.GcpAiNotebookInstanceResource;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.ResourceList;
import bio.terra.workspace.model.ResourceMetadata;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;

/** Utils for creating and deleting mix kinds of controlled and referenced resources. */
public class MultiResourcesUtils {

  // Support for makeResources
  public static String makeName() {
    return RandomStringUtils.random(10, true, false);
  }

  @FunctionalInterface
  public interface SupplierException<T> {
    T get() throws Exception;
  }

  /**
   * Make 7 resources as a representative sample of WSM resource types. This creates a controlled
   * shared GCS bucket, private GCS bucket, shared BQ dataset, and private AI notebook, as well as
   * references to each of those buckets and datasets.
   *
   * <p>This method intentionally does not create one of every WSM resource type, as that leads to
   * several bloated tests. Each resource type should be tested individually in a relevant
   * standalone test.
   *
   * @param referencedGcpResourceApi api for referenced resources
   * @param controlledGcpResourceApi api for controlled resources
   * @param workspaceUuid workspace where we allocate
   * @return list of resources
   * @throws Exception whatever might come up
   */
  public static List<ResourceMetadata> makeResources(
      ReferencedGcpResourceApi referencedGcpResourceApi,
      ControlledGcpResourceApi controlledGcpResourceApi,
      UUID workspaceUuid)
      throws Exception {

    // Create a shared GCS bucket, a private GCS bucket, and a shared BQ dataset
    GcpGcsBucketResource sharedBucket =
        GcsBucketUtils.makeControlledGcsBucketUserShared(
                controlledGcpResourceApi,
                workspaceUuid,
                makeName(),
                CloningInstructionsEnum.NOTHING)
            .getGcpBucket();
    GcpGcsBucketResource privateBucket =
        GcsBucketUtils.makeControlledGcsBucketUserPrivate(
                controlledGcpResourceApi,
                workspaceUuid,
                makeName(),
                CloningInstructionsEnum.NOTHING)
            .getGcpBucket();
    GcpBigQueryDatasetResource sharedDataset =
        BqDatasetUtils.makeControlledBigQueryDatasetUserShared(
            controlledGcpResourceApi,
            workspaceUuid,
            makeName(),
            null,
            CloningInstructionsEnum.NOTHING);
    GcpAiNotebookInstanceResource notebook =
        NotebookUtils.makeControlledNotebookUserPrivate(
                workspaceUuid,
                /* instanceId= */ null,
                /* location= */ null,
                controlledGcpResourceApi,
                /* testValue= */ null,
                /* postStartupScript= */ null)
            .getAiNotebookInstance();
    // Create references to the above buckets and datasets
    GcpGcsBucketResource sharedBucketReference =
        GcsBucketUtils.makeGcsBucketReference(
            sharedBucket.getAttributes(),
            referencedGcpResourceApi,
            workspaceUuid,
            makeName(),
            CloningInstructionsEnum.NOTHING);
    GcpGcsBucketResource privateBucketReference =
        GcsBucketUtils.makeGcsBucketReference(
            privateBucket.getAttributes(),
            referencedGcpResourceApi,
            workspaceUuid,
            makeName(),
            CloningInstructionsEnum.NOTHING);
    GcpBigQueryDatasetResource datasetReference =
        BqDatasetUtils.makeBigQueryDatasetReference(
            sharedDataset.getAttributes(), referencedGcpResourceApi, workspaceUuid, makeName());
    return List.of(
        sharedBucket.getMetadata(),
        privateBucket.getMetadata(),
        sharedDataset.getMetadata(),
        notebook.getMetadata(),
        sharedBucketReference.getMetadata(),
        privateBucketReference.getMetadata(),
        datasetReference.getMetadata());
  }

  /**
   * Designed to cleanup the result of allocations from {@link #makeResources}
   *
   * @param resources list of resources to cleanup
   */
  public static void cleanupResources(
      List<ResourceMetadata> resources,
      ControlledGcpResourceApi controlledGcpResourceApi,
      UUID workspaceUuid)
      throws Exception {
    for (ResourceMetadata metadata : resources) {
      if (metadata.getStewardshipType() == StewardshipType.CONTROLLED) {
        switch (metadata.getResourceType()) {
          case GCS_BUCKET -> GcsBucketUtils.deleteControlledGcsBucket(
              metadata.getResourceId(), workspaceUuid, controlledGcpResourceApi);
          case BIG_QUERY_DATASET -> controlledGcpResourceApi.deleteBigQueryDataset(
              workspaceUuid, metadata.getResourceId());
          case AI_NOTEBOOK -> NotebookUtils.deleteControlledNotebookUserPrivate(
              workspaceUuid, metadata.getResourceId(), controlledGcpResourceApi);
          default -> throw new IllegalStateException(
              String.format(
                  "No cleanup method specified for resource type %s.", metadata.getResourceType()));
        }
      }
    }
  }

  /** Assert that each element of a ResourceList has the same provided resource type. */
  public static void assertResourceType(ResourceType type, ResourceList list) {
    list.getResources()
        .forEach(resource -> assertEquals(type, resource.getMetadata().getResourceType()));
  }
}
