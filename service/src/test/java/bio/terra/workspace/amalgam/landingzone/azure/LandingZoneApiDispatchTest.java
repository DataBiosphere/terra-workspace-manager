package bio.terra.workspace.amalgam.landingzone.azure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.ConflictException;
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
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class LandingZoneApiDispatchTest extends BaseAzureUnitTest {
  private static final UUID LANDING_ZONE_ID = UUID.randomUUID();
  private static final UUID BILLING_PROFILE_ID = UUID.randomUUID();
  private final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final BearerToken BEARER_TOKEN = new BearerToken("fake-token");
  private static final OffsetDateTime CREATED_DATE = Instant.now().atOffset(ZoneOffset.UTC);

  private final SpendProfileId SPEND_PROFILE_ID = new SpendProfileId(UUID.randomUUID().toString());

  private LandingZoneApiDispatch landingZoneApiDispatch;
  @Mock private LandingZoneService landingZoneService;
  @Mock private FeatureConfiguration featureConfiguration;
  @Mock private WorkspaceService workspaceService;

  @BeforeEach
  void setupLandingZoneTests() {
    when(featureConfiguration.isAzureEnabled()).thenReturn(true);
    landingZoneApiDispatch =
        new LandingZoneApiDispatch(landingZoneService, workspaceService, featureConfiguration);
  }

  @Test
  void listAzureLandingZoneResourcesByPurpose_SubnetResourcePurpose_Success() {
    setupLandingZoneResources();
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
  void listAzureLandingZoneResourcesByPurpose_ResourcePurpose_Success() {
    setupLandingZoneResources();
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
  void listAzureLandingZoneResourcesByPurpose_Success_NoResults() {
    when(landingZoneService.listResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, SubnetResourcePurpose.WORKSPACE_BATCH_SUBNET))
        .thenReturn(Collections.emptyList());
    landingZoneApiDispatch =
        new LandingZoneApiDispatch(landingZoneService, workspaceService, featureConfiguration);

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
    landingZoneApiDispatch =
        new LandingZoneApiDispatch(landingZoneService, workspaceService, featureConfiguration);
    ApiAzureLandingZoneList response =
        landingZoneApiDispatch.listAzureLandingZones(BEARER_TOKEN, BILLING_PROFILE_ID);

    verify(landingZoneService, times(1))
        .getLandingZonesByBillingProfile(eq(BEARER_TOKEN), eq(BILLING_PROFILE_ID));

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
  void listAzureLandingZonesByBillingProfile_twoLandingZoneIdsThrows() throws ConflictException {
    when(landingZoneService.getLandingZonesByBillingProfile(BEARER_TOKEN, BILLING_PROFILE_ID))
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
                    .billingProfileId(BILLING_PROFILE_ID)
                    .definition("definition")
                    .version("1")
                    .createdDate(CREATED_DATE)
                    .build()));
    landingZoneApiDispatch =
        new LandingZoneApiDispatch(landingZoneService, workspaceService, featureConfiguration);
    assertThrows(
        ConflictException.class,
        () -> landingZoneApiDispatch.listAzureLandingZones(BEARER_TOKEN, BILLING_PROFILE_ID));
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
    landingZoneApiDispatch =
        new LandingZoneApiDispatch(landingZoneService, workspaceService, featureConfiguration);
    ApiAzureLandingZoneList response =
        landingZoneApiDispatch.listAzureLandingZones(BEARER_TOKEN, null);

    verify(landingZoneService, times(1)).listLandingZones(eq(BEARER_TOKEN));

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
    landingZoneApiDispatch =
        new LandingZoneApiDispatch(landingZoneService, workspaceService, featureConfiguration);
  }

  @Test
  public void getLandingZoneId_Success() {
    var workspace = mock(Workspace.class);
    when(workspace.getSpendProfileId()).thenReturn(Optional.of(SPEND_PROFILE_ID));
    when(workspaceService.getWorkspace(eq(WORKSPACE_ID))).thenReturn(workspace);

    var landingZoneList = Collections.singletonList(mock(LandingZone.class));
    var expectedLandingZoneId = UUID.randomUUID();
    when(landingZoneList.get(0).landingZoneId()).thenReturn(expectedLandingZoneId);
    // this method should always return list which contains one item
    when(landingZoneService.getLandingZonesByBillingProfile(
            eq(BEARER_TOKEN), eq(UUID.fromString(SPEND_PROFILE_ID.getId()))))
        .thenReturn(landingZoneList);

    UUID landingZoneId = landingZoneApiDispatch.getLandingZoneId(BEARER_TOKEN, WORKSPACE_ID);

    assertNotNull(landingZoneId);
    assertEquals(expectedLandingZoneId, landingZoneId);
  }

  @Test
  public void getLandingZoneId_billingProfileEmpty_failure() {
    var workspace = mock(Workspace.class);
    when(workspaceService.getWorkspace(eq(WORKSPACE_ID))).thenReturn(workspace);
    when(workspace.getSpendProfileId()).thenReturn(Optional.empty());

    assertThrows(
        LandingZoneNotFoundException.class,
        () -> landingZoneApiDispatch.getLandingZoneId(BEARER_TOKEN, WORKSPACE_ID));
  }

  @Test
  public void getLandingZoneId_landingZoneNotFound_failure() {
    var workspace = mock(Workspace.class);
    when(workspace.getSpendProfileId()).thenReturn(Optional.of(SPEND_PROFILE_ID));
    when(workspaceService.getWorkspace(eq(WORKSPACE_ID))).thenReturn(workspace);
    when(landingZoneService.getLandingZonesByBillingProfile(
            eq(BEARER_TOKEN), eq(UUID.fromString(SPEND_PROFILE_ID.getId()))))
        .thenReturn(Collections.emptyList());

    assertThrows(
        IllegalStateException.class,
        () -> landingZoneApiDispatch.getLandingZoneId(BEARER_TOKEN, WORKSPACE_ID));
  }
}
