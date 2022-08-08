package bio.terra.landingzone.db;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.landingzone.db.exception.DuplicateLandingZoneException;
import bio.terra.landingzone.sevice.model.LandingZone;
import bio.terra.workspace.app.configuration.external.LandingZoneDatabaseConfiguration;
import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class LandingZoneDaoTest extends BaseUnitTest {
  private static final String STUBBED_RESOURCE_GROUP = "stubbed_resource_group_id";
  @Autowired private LandingZoneDatabaseConfiguration config;
  @Autowired private WorkspaceDatabaseConfiguration workspaceDatabaseConfiguration;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private GcpCloudContextService gcpCloudContextService;

  @Autowired LandingZoneDao landingZoneDao;

  @Test
  void createAndGetLandingZoneRecord() {
    UUID landingZoneUuid = UUID.randomUUID();
    LandingZone landingZone =
        new LandingZone(
            landingZoneUuid,
            STUBBED_RESOURCE_GROUP,
            "definition1",
            "1",
            "",
            "description",
            Collections.emptyMap());

    landingZoneDao.createLandingZone(landingZone);

    LandingZone landingZoneRecord = landingZoneDao.getLandingZone(landingZoneUuid);

    assertNotNull(landingZoneRecord);

    assertLandingZonePropertiesAreEqual(landingZone, landingZoneRecord);

    landingZoneDao.deleteLandingZone(landingZoneUuid);
  }

  private void assertLandingZonePropertiesAreEqual(
      LandingZone originallandingZone, LandingZone landingZone) {
    assertPropertyValueEqual(
        originallandingZone.getLandingZoneId(), landingZone.getLandingZoneId(), "LandingZoneId");
    assertPropertyValueEqual(
        originallandingZone.getResourceGroupId(),
        landingZone.getResourceGroupId(),
        "ResourceGroupId");
    assertPropertyValueEqual(
        originallandingZone.getDefinition(), landingZone.getDefinition(), "Definition");
    assertPropertyValueEqual(originallandingZone.getVersion(), landingZone.getVersion(), "Version");
    assertPropertyValueEqual(
        originallandingZone.getDisplayName(), landingZone.getDisplayName(), "DisplayName");
    assertPropertyValueEqual(
        originallandingZone.getDescription(), landingZone.getDescription(), "Description");
    assertPropertyValueEqual(
        originallandingZone.getProperties(), landingZone.getProperties(), "Properties");
  }

  private static <T> void assertPropertyValueEqual(T original, T actual, String propertyName) {
    assertEquals(
        original, actual, String.format("Expected %S: , actual:", propertyName, original, actual));
  }
}
