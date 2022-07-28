package bio.terra.landingzone.db;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.landingzone.sevice.model.LandingZone;
import bio.terra.workspace.common.BaseUnitTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class LandingZoneDaoTest extends BaseUnitTest {
  LandingZoneDao landingZoneDao;

  @Test
  public void createLandingZoneRecord() {
    UUID landingZoneUuid = UUID.randomUUID();
    LandingZone landingZone =
        new LandingZone(
            landingZoneUuid,
            "stubbed_resource_group_id",
            "definition1",
            "1",
            "",
            "description",
            null);
    landingZoneDao.createLandingZone(landingZone);
    LandingZone landingZoneRecord = landingZoneDao.getLandingZone(landingZoneUuid);

    assertEquals(landingZoneRecord, landingZone);
    landingZoneDao.deleteLandingZone(landingZoneUuid);
  }
}
