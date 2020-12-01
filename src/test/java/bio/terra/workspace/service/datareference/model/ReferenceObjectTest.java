package bio.terra.workspace.service.datareference.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.common.BaseUnitTest;
import org.junit.jupiter.api.Test;

public class ReferenceObjectTest extends BaseUnitTest {

  /**
   * This is a simple test for verifying that SnapshotReference's toJson() and fromJson() are
   * compatible. If this failing, you've likely broken one or both of those methods.
   */
  @Test
  public void SnapshotReferenceSerializationWorks() {
    SnapshotReference snapshot =
        SnapshotReference.create(/* instanceName= */ "foo", /* snapshot= */ "bar");
    assertEquals(snapshot, ReferenceObject.fromJson(snapshot.toJson()));
  }

  /**
   * Hard code serialized values to check that code changes do not break backwards compatibility of
   * stored JSON values. If this test fails, your change may not work with existing databases.
   */
  @Test
  public void SnapshotReferenceSerializationBackwardsCompatible() {
    SnapshotReference snapshotReference =
        (SnapshotReference)
            ReferenceObject.fromJson(
                "{\"@type\":\"SnapshotReference\",\"instanceName\":\"foo\",\"snapshot\":\"bar\"}");
    assertEquals("foo", snapshotReference.instanceName());
    assertEquals("bar", snapshotReference.snapshot());
  }
}
