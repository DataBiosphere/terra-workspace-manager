package bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset;

import static bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures.BQ_DATASET_WITHOUT_EXPIRATION;
import static bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures.BQ_DATASET_WITH_EXPIRATION;
import static bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.BigQueryApiConversions.fromBqExpirationTime;
import static bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.BigQueryApiConversions.toBqExpirationTime;
import static bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.BigQueryApiConversions.toUpdateParameters;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import org.junit.jupiter.api.Test;

public class BigQueryApiConversionsTest extends BaseUnitTest {
  @Test
  public void testToUpdateParameters() {
    ApiGcpBigQueryDatasetUpdateParameters updateParameters1 =
        toUpdateParameters(BQ_DATASET_WITH_EXPIRATION);
    assertEquals(5900, updateParameters1.getDefaultTableLifetime());
    assertEquals(5901, updateParameters1.getDefaultPartitionLifetime());

    ApiGcpBigQueryDatasetUpdateParameters updateParameters2 =
        toUpdateParameters(BQ_DATASET_WITHOUT_EXPIRATION);
    assertEquals(0, updateParameters2.getDefaultTableLifetime());
    assertEquals(0, updateParameters2.getDefaultPartitionLifetime());
  }

  @Test
  public void testFromBqExpirationTime() {
    assertNull(fromBqExpirationTime(null));
    assertEquals(123, fromBqExpirationTime(123000L));
  }

  @Test
  public void testToBqExpirationTime() {
    assertNull(toBqExpirationTime(null));
    assertNull(toBqExpirationTime(0L));
    assertEquals(456000L, toBqExpirationTime(456L));
  }

  @Test
  public void testRoundTrip() {
    assertNull(fromBqExpirationTime(toBqExpirationTime(null)));
    assertEquals(789L, fromBqExpirationTime(toBqExpirationTime(789L)));
  }
}
