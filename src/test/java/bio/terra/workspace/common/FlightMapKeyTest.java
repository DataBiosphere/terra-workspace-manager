package bio.terra.workspace.common;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.service.workspace.flight.GoogleBucketFlightMapKeys;
import org.junit.jupiter.api.Test;

public class FlightMapKeyTest extends BaseUnitTest {

  @Test
  public void testLookup() {
    final FlightMap flightMap = new FlightMap();
    flightMap.put(GoogleBucketFlightMapKeys.LOCATION.getKey(), "us");

    assertThat(
        flightMap.get(GoogleBucketFlightMapKeys.LOCATION.getKey(), String.class), equalTo("us"));
    var retrieved = flightMap.get(GoogleBucketFlightMapKeys.LOCATION.getKey(), String.class);
    assertThat(retrieved, equalTo("us"));
  }

  @Test
  public void testCast() {
    final Object foo = "hello";
    final String asCast = (String) GoogleBucketFlightMapKeys.LOCATION.getKlass().cast(foo);
    assertThat(asCast, equalTo(foo));
  }

  //  private static <T> T lookup(FlightMap flightMap, FlightMapKey flightMapKey) {
  //    return flightMap.get(flightMapKey.getKey(), flightMapKey.getKlass());
  //  }
}
