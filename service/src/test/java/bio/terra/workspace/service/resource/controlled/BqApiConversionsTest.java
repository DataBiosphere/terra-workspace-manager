package bio.terra.workspace.service.resource.controlled;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.BQ_DATASET_WITHOUT_EXPIRATION;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.BQ_DATASET_WITH_EXPIRATION;
import static bio.terra.workspace.service.resource.controlled.BqApiConversions.fromBqExpirationTime;
import static bio.terra.workspace.service.resource.controlled.BqApiConversions.toBqExpirationTime;
import static bio.terra.workspace.service.resource.controlled.BqApiConversions.toUpdateParameters;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import org.junit.jupiter.api.Test;

public class BqApiConversionsTest extends BaseUnitTest {
  @Test
  public void testToUpdateParameters() {
    final ApiGcpBigQueryDatasetUpdateParameters updateParameters1 =
        toUpdateParameters(BQ_DATASET_WITH_EXPIRATION);
    assertEquals(5900, updateParameters1.getDefaultTableLifetime());
    assertEquals(5901, updateParameters1.getDefaultPartitionLifetime());

    final ApiGcpBigQueryDatasetUpdateParameters updateParameters2 =
        toUpdateParameters(BQ_DATASET_WITHOUT_EXPIRATION);
    assertEquals(0, updateParameters2.getDefaultTableLifetime());
    assertEquals(0, updateParameters2.getDefaultPartitionLifetime());
  }

  @Test
  public void testFromBqExpirationTime() {
    assertNull(fromBqExpirationTime(null));
    assertEquals(123, fromBqExpirationTime(Long.valueOf(123000)));
  }

  @Test
  public void testToBqExpirationTime() {
    assertNull(toBqExpirationTime(null));
    assertEquals(456000, toBqExpirationTime(456));
  }

  @Test
  public void testRoundTrip() {
    assertEquals(null, fromBqExpirationTime(toBqExpirationTime(null)));
    assertEquals(789, fromBqExpirationTime(toBqExpirationTime(789)));
  }
}
