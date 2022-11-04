package bio.terra.workspace.amalgam.landingzone.azure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.landingzone.library.landingzones.deployment.SubnetResourcePurpose;
import bio.terra.landingzone.service.landingzone.azure.LandingZoneService;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResource;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesList;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;

public class LandingZoneApiDispatchTest extends BaseAzureUnitTest {
  private static final UUID LANDING_ZONE_ID = UUID.randomUUID();
  private static final UUID BILLING_PROFILE_ID = UUID.randomUUID();
  private static final BearerToken BEARER_TOKEN = new BearerToken("fake-token");
  private static final OffsetDateTime CREATED_DATE = Instant.now().atOffset(ZoneOffset.UTC);

  private LandingZoneApiDispatch landingZoneApiDispatch;
  @Mock private LandingZoneService landingZoneService;
  @Mock private FeatureConfiguration featureConfiguration;

  @BeforeEach
  void setupLandingZoneTests() {
    when(featureConfiguration.isAzureEnabled()).thenReturn(true);
  }

  @Test
  void listAzureLandingZoneResourcesByPurpose_SubnetResourcePurpose_Success() {
    setupLandingZoneResources();
    ApiAzureLandingZoneResourcesList response =
        landingZoneApiDispatch.listAzureLandingZoneResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, SubnetResourcePurpose.WORKSPACE_STORAGE_SUBNET);

    verify(landingZoneService, times(1))
        .listResourcesByPurpose(
            ArgumentMatchers.eq(BEARER_TOKEN),
            ArgumentMatchers.eq(LANDING_ZONE_ID),
            ArgumentMatchers.eq(SubnetResourcePurpose.WORKSPACE_STORAGE_SUBNET));
    assertNotNull(response);
    assertNotNull(response.getResources());
    assertEquals(1, response.getResources().size());
    assertEquals(
        3, response.getResources().stream().findFirst().get().getDeployedResources().size());
  }

  @Test
  void listAzureLandingZoneResourcesByPurpose_Success_NoResults() {
    when(landingZoneService.listResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, SubnetResourcePurpose.WORKSPACE_BATCH_SUBNET))
        .thenReturn(Collections.emptyList());
    landingZoneApiDispatch = new LandingZoneApiDispatch(landingZoneService, featureConfiguration);

    ApiAzureLandingZoneResourcesList response =
        landingZoneApiDispatch.listAzureLandingZoneResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, SubnetResourcePurpose.WORKSPACE_BATCH_SUBNET);

    verify(landingZoneService, times(1))
        .listResourcesByPurpose(
            ArgumentMatchers.eq(BEARER_TOKEN),
            ArgumentMatchers.eq(LANDING_ZONE_ID),
            ArgumentMatchers.eq(SubnetResourcePurpose.WORKSPACE_BATCH_SUBNET));
    assertNotNull(response);
    assertNotNull(response.getResources());
    assertEquals(1, response.getResources().size());
    assertEquals(
        0, response.getResources().stream().findFirst().get().getDeployedResources().size());
  }

  @Test
  void listAzureLandingZonesByBillingProfile_Success() {
    when(landingZoneService.getLandingZonesByBillingProfile(BEARER_TOKEN, BILLING_PROFILE_ID))
        .thenReturn(
            List.of(
                LandingZone.builder()
                    .landingZoneId(LANDING_ZONE_ID)
                    .billingProfileId(BILLING_PROFILE_ID)
                    .definition("definition")
                    .version("1")
                    .createdDate(CREATED_DATE)
                    .build()));
    landingZoneApiDispatch = new LandingZoneApiDispatch(landingZoneService, featureConfiguration);
    ApiAzureLandingZoneList response =
        landingZoneApiDispatch.listAzureLandingZones(BEARER_TOKEN, BILLING_PROFILE_ID);

    verify(landingZoneService, times(1))
        .getLandingZonesByBillingProfile(
            ArgumentMatchers.eq(BEARER_TOKEN), ArgumentMatchers.eq(BILLING_PROFILE_ID));

    assertNotNull(response);
    assertNotNull(response.getLandingzones());
    assertEquals(1, response.getLandingzones().size());

    var firstLandingZone = response.getLandingzones().stream().findFirst().get();
    assertEquals(LANDING_ZONE_ID, firstLandingZone.getLandingZoneId());
    assertEquals(BILLING_PROFILE_ID, firstLandingZone.getBillingProfileId());
    assertEquals("definition", firstLandingZone.getDefinition());
    assertEquals("1", firstLandingZone.getVersion());
    assertEquals(CREATED_DATE, firstLandingZone.getCreatedDate());
  }

  @Test
  void listAzureLandingZones_Success() {
    when(landingZoneService.listLandingZones(BEARER_TOKEN))
        .thenReturn(
            List.of(
                LandingZone.builder()
                    .landingZoneId(LANDING_ZONE_ID)
                    .billingProfileId(BILLING_PROFILE_ID)
                    .definition("definition")
                    .version("1")
                    .createdDate(CREATED_DATE)
                    .build(),
                LandingZone.builder()
                    .landingZoneId(UUID.randomUUID())
                    .billingProfileId(UUID.randomUUID())
                    .definition("definition")
                    .version("1")
                    .createdDate(CREATED_DATE)
                    .build()));
    landingZoneApiDispatch = new LandingZoneApiDispatch(landingZoneService, featureConfiguration);
    ApiAzureLandingZoneList response =
        landingZoneApiDispatch.listAzureLandingZones(BEARER_TOKEN, null);

    verify(landingZoneService, times(1)).listLandingZones(ArgumentMatchers.eq(BEARER_TOKEN));

    assertNotNull(response);
    assertNotNull(response.getLandingzones());
    assertEquals(2, response.getLandingzones().size());
  }

  private void setupLandingZoneResources() {
    final List<LandingZoneResource> listSubnets1 =
        List.of(
            LandingZoneResource.builder()
                .resourceName("fooSubnet11")
                .resourceParentId("fooNetworkVNetId1")
                .region("fooRegion1")
                .build(),
            LandingZoneResource.builder()
                .resourceName("fooSubnet12")
                .resourceParentId("fooNetworkVNetId2")
                .region("fooRegion2")
                .build(),
            LandingZoneResource.builder()
                .resourceName("fooSubnet13")
                .resourceParentId("fooNetworkVNetId1")
                .region("fooRegion1")
                .build());

    final List<LandingZoneResource> listResources1 =
        List.of(
            LandingZoneResource.builder()
                .resourceId("Id31")
                .resourceType("fooType31")
                .region("fooRegion1")
                .build(),
            LandingZoneResource.builder()
                .resourceId("Id32")
                .resourceType("fooType32")
                .region("fooRegion2")
                .build(),
            LandingZoneResource.builder()
                .resourceId("Id33")
                .resourceType("fooType33")
                .region("fooRegion1")
                .build());

    when(landingZoneService.listResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, SubnetResourcePurpose.WORKSPACE_STORAGE_SUBNET))
        .thenReturn(listSubnets1);
    when(landingZoneService.listResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, ResourcePurpose.SHARED_RESOURCE))
        .thenReturn(listResources1);
    landingZoneApiDispatch = new LandingZoneApiDispatch(landingZoneService, featureConfiguration);
  }
}
