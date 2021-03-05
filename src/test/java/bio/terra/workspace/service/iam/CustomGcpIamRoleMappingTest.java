package bio.terra.workspace.service.iam;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;

import bio.terra.workspace.common.BaseUnitTest;
import org.junit.jupiter.api.Test;

/**
 * We currently enforce that in WSM, IAM roles are hierarchical: writers have all permissions of
 * readers, and owners have all permissions of writers. This holds for the definitions of our custom
 * GCP IAM roles.
 */
public class CustomGcpIamRoleMappingTest extends BaseUnitTest {
  @Test
  void bucketWriterContainsReader() {
    assertThat(
        CustomGcpIamRoleMapping.GCS_BUCKET_READER_PERMISSIONS,
        everyItem(in(CustomGcpIamRoleMapping.GCS_BUCKET_WRITER_PERMISSIONS)));
  }

  @Test
  void bucketOwnerContainsWriter() {
    assertThat(
        CustomGcpIamRoleMapping.GCS_BUCKET_WRITER_PERMISSIONS,
        everyItem(in(CustomGcpIamRoleMapping.GCS_BUCKET_OWNER_PERMISSIONS)));
  }

  @Test
  void bqDatasetWriterContainsReader() {
    assertThat(
        CustomGcpIamRoleMapping.BIGQUERY_DATASET_READER_PERMISSIONS,
        everyItem(in(CustomGcpIamRoleMapping.BIGQUERY_DATASET_WRITER_PERMISSIONS)));
  }

  @Test
  void bqDatasetOwnerContainsWriter() {
    assertThat(
        CustomGcpIamRoleMapping.BIGQUERY_DATASET_WRITER_PERMISSIONS,
        everyItem(in(CustomGcpIamRoleMapping.BIGQUERY_DATASET_OWNER_PERMISSIONS)));
  }
}
