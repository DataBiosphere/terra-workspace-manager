package bio.terra.workspace.service.iam;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.resource.controlled.mappings.CustomGcpIamRoleMapping;
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
  void bqDatasetWriterContainsReader() {
    assertThat(
        CustomGcpIamRoleMapping.BIG_QUERY_DATASET_READER_PERMISSIONS,
        everyItem(in(CustomGcpIamRoleMapping.BIG_QUERY_DATASET_WRITER_PERMISSIONS)));
  }
}
