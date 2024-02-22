package bio.terra.workspace.service.resource.controlled.cloud.gcp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;

import bio.terra.workspace.common.BaseSpringBootUnitTest;
import org.junit.jupiter.api.Test;

/**
 * WSM resource role definitions currently assume that the WRITER resource-level role is a superset
 * of the READER resource-level role. These tests validate that assumption.
 */
public class CustomGcpIamRoleMappingTest extends BaseSpringBootUnitTest {
  @Test
  void bucketWriterContainsReader() {
    assertThat(
        CustomGcpIamRoleMapping.GCS_BUCKET_READER_PERMISSIONS,
        everyItem(in(CustomGcpIamRoleMapping.GCS_BUCKET_WRITER_PERMISSIONS)));
  }

  @Test
  void bqDatasetWriterContainsReader() {
    assertThat(
        CustomGcpIamRoleMapping.BIG_QUERY_DATASET_READER_PERMISSIONS,
        everyItem(in(CustomGcpIamRoleMapping.BIG_QUERY_DATASET_WRITER_PERMISSIONS)));
  }
}
