package bio.terra.workspace.service.datareference.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.common.BaseUnitTest;
import org.junit.jupiter.api.Test;

class ReferenceObjectTest extends BaseUnitTest {

  /**
   * This is a simple test for verifying that SnapshotReference's toJson() and fromJson() are
   * compatible. If this failing, you've likely broken one or both of those methods.
   */
  @Test
  void SnapshotReferenceSerializationWorks() {
    SnapshotReference snapshot =
        SnapshotReference.create(/* instanceName= */ "foo", /* snapshot= */ "bar");
    assertEquals(snapshot, ReferenceObject.fromJson(snapshot.toJson()));
  }

  /**
   * Hard code serialized values to check that code changes do not break backwards compatibility of
   * stored JSON values. If this test fails, your change may not work with existing databases.
   */
  @Test
  void SnapshotReferenceSerializationBackwardsCompatible() {
    SnapshotReference snapshotReference =
        (SnapshotReference)
            ReferenceObject.fromJson(
                "{\"@type\":\"SnapshotReference\",\"instanceName\":\"foo\",\"snapshot\":\"bar\"}");
    assertEquals("foo", snapshotReference.instanceName());
    assertEquals("bar", snapshotReference.snapshot());
  }

  /**
   * This is a simple test for verifying that GoogleBucketReference's toJson() and fromJson() are
   * compatible. If this failing, you've likely broken one or both of those methods.
   */
  @Test
  public void GoogleBucketReferenceSerializationWorks() {
    GoogleBucketReference bucket = GoogleBucketReference.create(/* bucketName= */ "foo");
    assertEquals(bucket, ReferenceObject.fromJson(bucket.toJson()));
  }

  /**
   * Hard code serialized values to check that code changes do not break backwards compatibility of
   * stored JSON values. If this test fails, your change may not work with existing databases.
   */
  @Test
  public void GoogleBucketReferenceSerializationBackwardsCompatible() {
    GoogleBucketReference bucketReference =
        (GoogleBucketReference)
            ReferenceObject.fromJson(
                "{\"@type\":\"GoogleBucketReference\",\"bucketName\":\"foo\"}");
    assertEquals("foo", bucketReference.bucketName());
  }

  /**
   * This is a simple test for verifying that BigQueryDataset's toJson() and fromJson() are
   * compatible. If this failing, you've likely broken one or both of those methods.
   */
  @Test
  public void BigQueryDatasetReferenceSerializationWorks() {
    BigQueryDatasetReference dataset =
        BigQueryDatasetReference.create(/* projectId= */ "foo", /* datasetName= */ "bar");
    assertEquals(dataset, ReferenceObject.fromJson(dataset.toJson()));
  }

  /**
   * Hard code serialized values to check that code changes do not break backwards compatibility of
   * stored JSON values. If this test fails, your change may not work with existing databases.
   */
  @Test
  public void BigQueryDatasetReferenceSerializationBackwardsCompatible() {
    BigQueryDatasetReference datasetReference =
        (BigQueryDatasetReference)
            ReferenceObject.fromJson(
                "{\"@type\":\"BigQueryDatasetReference\",\"projectId\":\"foo\",\"datasetName\":\"bar\"}");
    assertEquals("foo", datasetReference.projectId());
    assertEquals("bar", datasetReference.datasetName());
  }
}
