package bio.terra.workspace.serdes;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import org.junit.jupiter.api.Test;

/** Test Stairway serialization of the ControlledGcsBucketResource class */
public class ControlledGcsBucketResourceTest extends BaseUnitTest {

  @Test
  public void testValidateOk() {
    // will throw if anything is amiss
    ControlledResourceFixtures.BUCKET_RESOURCE.validate();
  }

  @Test
  public void testValidateThrows() {
    assertThrows(
        IllegalStateException.class, ControlledResourceFixtures.INVALID_BUCKET_RESOURCE::validate);
  }
}
