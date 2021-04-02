package bio.terra.workspace.service.resource;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.stairway.FlightMapTestUtils;
import bio.terra.workspace.common.BaseUnitTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class WsmResourceTypeTest extends BaseUnitTest {
  @Test
  public void flightMapSerialization() {
    // Throws, since WsmResourceType can't be serialized. We shouldn't use things that can't be
    // serialized.
    assertThrows(
        ClassCastException.class,
        () -> FlightMapTestUtils.serializeAndDeserialize(WsmResourceType.GCS_BUCKET));
    assertThrows(
        ClassCastException.class,
        () -> FlightMapTestUtils.serializeAndDeserialize(UUID.randomUUID()));
  }
}
