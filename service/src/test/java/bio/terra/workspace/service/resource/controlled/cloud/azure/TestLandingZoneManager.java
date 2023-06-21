package bio.terra.workspace.service.resource.controlled.cloud.azure;

import bio.terra.landingzone.db.LandingZoneDao;
import bio.terra.landingzone.db.exception.DuplicateLandingZoneException;
import bio.terra.landingzone.db.model.LandingZoneRecord;
import bio.terra.workspace.common.utils.AzureTestUtils;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * There is a MRG that has pre-staged resources that look like they were deployed by a landing zone.
 * This class inserts the necessary database records to connect to that MRG and make it seem like a
 * Landing Zone.
 */
public class TestLandingZoneManager {
  private final LandingZoneDao landingZoneDao;
  private final AzureTestUtils azureTestUtils;

  public TestLandingZoneManager(LandingZoneDao landingZoneDao, AzureTestUtils azureTestUtils) {
    this.landingZoneDao = landingZoneDao;
    this.azureTestUtils = azureTestUtils;
  }

  public void createLandingZoneDbRecord(UUID landingZoneId) {
    String definition = "QuasiLandingZone";
    String version = "v1";
    // create record in LZ database
    try {
      landingZoneDao.createLandingZone(
          LandingZoneRecord.builder()
              .landingZoneId(landingZoneId)
              .definition(definition)
              .version(version)
              .description(String.format("Definition:%s Version:%s", definition, version))
              .displayName(definition)
              .properties(null)
              .resourceGroupId(azureTestUtils.getAzureCloudContext().getAzureResourceGroupId())
              .subscriptionId(azureTestUtils.getAzureCloudContext().getAzureSubscriptionId())
              .tenantId(azureTestUtils.getAzureCloudContext().getAzureTenantId())
              .billingProfileId(UUID.fromString(azureTestUtils.getSpendProfileId().getId()))
              .createdDate(OffsetDateTime.of(LocalDate.now(), LocalTime.now(), ZoneOffset.UTC))
              .build());
    } catch (DuplicateLandingZoneException dlze) {
      // this is ok, 1 landing zone is used for all tests
    }
  }
}
