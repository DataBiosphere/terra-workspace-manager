package bio.terra.workspace.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.common.mocks.MockMvcUtils;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;

// Test Utils for GCP (unit & connected) tests
public class GcpTestUtils {

  public static void assertApiGcsBucketEquals(
      ApiGcpGcsBucketResource expectedBucket, ApiGcpGcsBucketResource actualBucket) {
    MockMvcUtils.assertResourceMetadataEquals(
        expectedBucket.getMetadata(), actualBucket.getMetadata());
    assertEquals(expectedBucket.getAttributes(), actualBucket.getAttributes());
  }

  public static void assertApiBqDatasetEquals(
      ApiGcpBigQueryDatasetResource expectedDataset, ApiGcpBigQueryDatasetResource actualDataset) {
    MockMvcUtils.assertResourceMetadataEquals(
        expectedDataset.getMetadata(), actualDataset.getMetadata());
    assertEquals(expectedDataset.getAttributes(), actualDataset.getAttributes());
  }
}
