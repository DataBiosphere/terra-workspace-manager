package bio.terra.workspace.service.spendprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.BaseUnitTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class SpendProfileIdTest extends BaseUnitTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Check that SpendProfileId serializes the same as UUID. This verifies historical backwards
   * compatibility.
   */
  @Test
  public void equivalentJsonUUIDSerialization() throws Exception {
    UUID uuid = UUID.randomUUID();
    SpendProfileId spendProfileId = SpendProfileId.create(uuid);
    String serialized = objectMapper.writeValueAsString(uuid);
    assertEquals(serialized, objectMapper.writeValueAsString(spendProfileId));
    assertEquals(spendProfileId, objectMapper.readValue(serialized, SpendProfileId.class));
  }

  @Test
  public void flightMapSerialization() {
    SpendProfileId spendProfileId = SpendProfileId.create(UUID.randomUUID());
    FlightMap flightMap = new FlightMap();
    flightMap.put("spend", spendProfileId);
    flightMap.put("uuid", spendProfileId.uuid());
    flightMap.put("bar", "barabas");

    FlightMap serialized = new FlightMap();
    serialized.fromJson(flightMap.toJson());

    assertEquals(spendProfileId, flightMap.get("foo", SpendProfileId.class));
    assertEquals(spendProfileId, flightMap.get("uuid", SpendProfileId.class));
  }
}
