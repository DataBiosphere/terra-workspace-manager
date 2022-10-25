package bio.terra.workspace.amalgam.landingzone.azure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesList;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

  private final SpendProfileId SPEND_PROFILE_ID = new SpendProfileId(UUID.randomUUID().toString());
  private final AzureCloudContext AZURE_CLOUD_CONTEXT =
      new AzureCloudContext(
          UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString());

  private LandingZoneApiDispatch landingZoneApiDispatch;
  @Mock private LandingZoneService landingZoneService;
  @Mock private FeatureConfiguration featureConfiguration;
  @Mock private AzureCloudContextService azureCloudContextService;

  @BeforeEach
  public void setupLandingZoneResources() {
    when(landingZoneService.listResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, SubnetResourcePurpose.WORKSPACE_STORAGE_SUBNET))
        .thenReturn(listSubnets1);
    when(landingZoneService.listResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, ResourcePurpose.SHARED_RESOURCE))
        .thenReturn(listResources1);
    when(featureConfiguration.isAzureEnabled()).thenReturn(true);
    landingZoneApiDispatch =
        new LandingZoneApiDispatch(
            landingZoneService, azureCloudContextService, featureConfiguration);
  }

  @Test
  public void listAzureLandingZoneResourcesByPurpose_SubnetResourcePurpose_Success()
      throws Exception {
    ApiAzureLandingZoneResourcesList response =
        landingZoneApiDispatch.listAzureLandingZoneResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, SubnetResourcePurpose.WORKSPACE_STORAGE_SUBNET);

    verify(landingZoneService, times(1))
        .listResourcesByPurpose(
            eq(BEARER_TOKEN),
            eq(LANDING_ZONE_ID),
            eq(SubnetResourcePurpose.WORKSPACE_STORAGE_SUBNET));
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
    landingZoneApiDispatch =
        new LandingZoneApiDispatch(
            landingZoneService, azureCloudContextService, featureConfiguration);

    ApiAzureLandingZoneResourcesList response =
        landingZoneApiDispatch.listAzureLandingZoneResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, SubnetResourcePurpose.WORKSPACE_BATCH_SUBNET);

    verify(landingZoneService, times(1))
        .listResourcesByPurpose(
            eq(BEARER_TOKEN),
            eq(LANDING_ZONE_ID),
            eq(SubnetResourcePurpose.WORKSPACE_BATCH_SUBNET));
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
            eq(BEARER_TOKEN), eq(LANDING_ZONE_ID), eq(ResourcePurpose.SHARED_RESOURCE));

    assertNotNull(response);
    assertNotNull(response.getResources());
    assertEquals(1, response.getResources().size());
    assertEquals(
        3, response.getResources().stream().findFirst().get().getDeployedResources().size());
  }

  @Test
  public void getLandingZoneId_Success() {
    var workspace = mock(Workspace.class);
    when(workspace.getSpendProfileId()).thenReturn(Optional.of(SPEND_PROFILE_ID));
    when(azureCloudContextService.getWorkspace(eq(AZURE_CLOUD_CONTEXT))).thenReturn(workspace);

    var landingZoneList = Collections.singletonList(mock(LandingZone.class));
    var expectedLandingZoneId = UUID.randomUUID();
    when(landingZoneList.get(0).landingZoneId()).thenReturn(expectedLandingZoneId);
    // this method should always return list which contains one item
    when(landingZoneService.getLandingZonesByBillingProfile(
            eq(BEARER_TOKEN), eq(UUID.fromString(SPEND_PROFILE_ID.getId()))))
        .thenReturn(landingZoneList);

    UUID landingZoneId = landingZoneApiDispatch.getLandingZoneId(BEARER_TOKEN, AZURE_CLOUD_CONTEXT);

    assertNotNull(landingZoneId);
    assertEquals(expectedLandingZoneId, landingZoneId);
  }

  @Test
  public void getLandingZoneId_billingProfileEmpty_failure() {
    var workspace = mock(Workspace.class);
    when(azureCloudContextService.getWorkspace(eq(AZURE_CLOUD_CONTEXT))).thenReturn(workspace);
    when(workspace.getSpendProfileId()).thenReturn(Optional.empty());

    assertThrows(
        LandingZoneNotFoundException.class,
        () -> landingZoneApiDispatch.getLandingZoneId(BEARER_TOKEN, AZURE_CLOUD_CONTEXT));
  }

  @Test
  public void getLandingZoneId_landingZoneNotFound_failure() {
    var workspace = mock(Workspace.class);
    when(workspace.getSpendProfileId()).thenReturn(Optional.of(SPEND_PROFILE_ID));
    when(azureCloudContextService.getWorkspace(eq(AZURE_CLOUD_CONTEXT))).thenReturn(workspace);
    when(landingZoneService.getLandingZonesByBillingProfile(
            eq(BEARER_TOKEN), eq(UUID.fromString(SPEND_PROFILE_ID.getId()))))
        .thenReturn(Collections.emptyList());

    assertThrows(
        IllegalStateException.class,
        () -> landingZoneApiDispatch.getLandingZoneId(BEARER_TOKEN, AZURE_CLOUD_CONTEXT));
  }
}
