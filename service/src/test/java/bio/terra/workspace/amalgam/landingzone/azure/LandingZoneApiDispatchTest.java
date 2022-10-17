package bio.terra.workspace.amalgam.landingzone.azure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.library.landingzones.deployment.LandingZonePurpose;
import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.landingzone.library.landingzones.deployment.SubnetResourcePurpose;
import bio.terra.landingzone.service.landingzone.azure.LandingZoneService;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResource;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResourcesByPurpose;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesList;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

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

  private final List<LandingZoneResource> listSubnets2 =
      List.of(
          LandingZoneResource.builder()
              .resourceName("fooSubnet21")
              .resourceParentId("fooNetworkVNetId1")
              .region("fooRegion1")
              .build());
  private final List<LandingZoneResource> listResources3 =
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

  private final List<LandingZoneResource> listResources4 =
      List.of(
          LandingZoneResource.builder()
              .resourceId("Id41")
              .resourceType("fooType41")
              .region("fooRegion1")
              .build());

  private final LandingZonePurpose purposeSubnets1 = SubnetResourcePurpose.AKS_NODE_POOL_SUBNET;
  private final LandingZonePurpose purposeSubnets2 = SubnetResourcePurpose.POSTGRESQL_SUBNET;
  private final LandingZonePurpose purposeSubnets3 = ResourcePurpose.SHARED_RESOURCE;
  private final LandingZonePurpose purposeSubnets4 = ResourcePurpose.WLZ_RESOURCE;

  @Autowired ObjectMapper objectMapper;
  @Autowired private LandingZoneApiDispatch landingZoneApiDispatch;

  @MockBean private LandingZoneService landingZoneService;
  @MockBean private FeatureConfiguration featureConfiguration;

  @BeforeAll
  public void setupLandingZoneResources() {
    LandingZoneResourcesByPurpose groupedResources =
        new LandingZoneResourcesByPurpose(
            Map.of(
                purposeSubnets4,
                listResources4,
                purposeSubnets3,
                listResources3,
                purposeSubnets1,
                listSubnets1,
                purposeSubnets2,
                listSubnets2));
    when(landingZoneService.listResourcesByPurpose(
            any(), any(), SubnetResourcePurpose.WORKSPACE_STORAGE_SUBNET))
        .thenReturn(listSubnets1);
    when(landingZoneService.listResourcesByPurpose(any(), any(), ResourcePurpose.SHARED_RESOURCE))
        .thenReturn(listResources3);
    when(landingZoneService.listResourcesByPurpose(
            any(), any(), SubnetResourcePurpose.WORKSPACE_BATCH_SUBNET))
        .thenReturn(Collections.emptyList());
    when(landingZoneService.listResourcesWithPurposes(any(), any())).thenReturn(groupedResources);
    when(featureConfiguration.isAzureEnabled()).thenReturn(true);
  }

  @Test
  public void listAzureLandingZoneResourcesByPurpose_Success() throws Exception {
    ApiAzureLandingZoneResourcesList response =
        landingZoneApiDispatch.listAzureLandingZoneResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, SubnetResourcePurpose.WORKSPACE_STORAGE_SUBNET);

    verify(
        landingZoneService.listResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, SubnetResourcePurpose.WORKSPACE_STORAGE_SUBNET),
        times(1));
    assertNotNull(response);
    assertNotNull(response.getResources());
    assertEquals(1, response.getResources().size());
    assertEquals(3, response.getResources().stream().findFirst().stream().count());
  }

  @Test
  public void listAzureLandingZoneResourcesByPurpose_Success_NoResults() throws Exception {
    ApiAzureLandingZoneResourcesList response =
        landingZoneApiDispatch.listAzureLandingZoneResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, SubnetResourcePurpose.WORKSPACE_BATCH_SUBNET);

    verify(
        landingZoneService.listResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, SubnetResourcePurpose.WORKSPACE_BATCH_SUBNET),
        times(1));
    assertNotNull(response);
    assertNotNull(response.getResources());
    assertEquals(1, response.getResources().size());
    assertEquals(0, response.getResources().stream().findFirst().stream().count());
  }

  @Test
  public void listAzureLandingZoneResourcesByPurpose_ResourcePurpose_Success() throws Exception {
    ApiAzureLandingZoneResourcesList response =
        landingZoneApiDispatch.listAzureLandingZoneResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, ResourcePurpose.SHARED_RESOURCE);

    verify(
        landingZoneService.listResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, ResourcePurpose.SHARED_RESOURCE),
        times(1));
    assertNotNull(response);
    assertNotNull(response.getResources());
    assertEquals(1, response.getResources().size());
    assertEquals(3, response.getResources().stream().findFirst().stream().count());
  }
}
