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
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResource;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneIdList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;

public class LandingZoneApiDispatchTest extends BaseAzureUnitTest {
  private static final UUID LANDING_ZONE_ID = UUID.randomUUID();
  private static final BearerToken BEARER_TOKEN = new BearerToken("fake-token");
  private final List<LandingZoneResource> listSubnets1 =
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

  private final List<LandingZoneResource> listResources1 =
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

  private LandingZoneApiDispatch landingZoneApiDispatch;
  @Mock private LandingZoneService landingZoneService;
  @Mock private FeatureConfiguration featureConfiguration;

  @BeforeEach
  public void setupLandingZoneResources() {
    when(landingZoneService.listResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, SubnetResourcePurpose.WORKSPACE_STORAGE_SUBNET))
        .thenReturn(listSubnets1);
    when(landingZoneService.listResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, ResourcePurpose.SHARED_RESOURCE))
        .thenReturn(listResources1);
    when(featureConfiguration.isAzureEnabled()).thenReturn(true);
    landingZoneApiDispatch = new LandingZoneApiDispatch(landingZoneService, featureConfiguration);
  }

  @Test
  public void listAzureLandingZoneResourcesByPurpose_SubnetResourcePurpose_Success()
      throws Exception {
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
  public void listAzureLandingZoneResourcesByPurpose_Success_NoResults() throws Exception {
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
  public void listAzureLandingZoneResourcesByPurpose_ResourcePurpose_Success() throws Exception {
    ApiAzureLandingZoneResourcesList response =
        landingZoneApiDispatch.listAzureLandingZoneResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, ResourcePurpose.SHARED_RESOURCE);

    verify(landingZoneService, times(1))
        .listResourcesByPurpose(
            ArgumentMatchers.eq(BEARER_TOKEN),
            ArgumentMatchers.eq(LANDING_ZONE_ID),
            ArgumentMatchers.eq(ResourcePurpose.SHARED_RESOURCE));

    assertNotNull(response);
    assertNotNull(response.getResources());
    assertEquals(1, response.getResources().size());
    assertEquals(
        3, response.getResources().stream().findFirst().get().getDeployedResources().size());
  }

  @Test
  public void listAzureLandingZoneIds_Success() throws Exception {
    // random test UUID
    UUID billingProfileId = UUID.fromString("01894362-c0f4-4d71-a459-19c8be47eb50");

    when(landingZoneService.listLandingZoneIds(BEARER_TOKEN, billingProfileId))
        .thenReturn(Arrays.asList(LANDING_ZONE_ID));

    ApiAzureLandingZoneIdList response =
        landingZoneApiDispatch.listAzureLandingZoneIds(BEARER_TOKEN, billingProfileId);

    verify(landingZoneService, times(1))
        .listLandingZoneIds(
            ArgumentMatchers.eq(BEARER_TOKEN), ArgumentMatchers.eq(billingProfileId));
    assertNotNull(response);
    assertNotNull(response.getLandingZoneIds());
    assertEquals(1, response.getLandingZoneIds().size());
    assertEquals(LANDING_ZONE_ID, response.getLandingZoneIds().get(0));
  }
}
