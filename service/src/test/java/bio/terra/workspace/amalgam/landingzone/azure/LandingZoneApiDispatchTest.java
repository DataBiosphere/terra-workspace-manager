package bio.terra.workspace.amalgam.landingzone.azure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.ConflictException;
import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.job.LandingZoneJobService;
import bio.terra.landingzone.job.model.JobReport;
import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.landingzone.library.landingzones.deployment.SubnetResourcePurpose;
import bio.terra.landingzone.library.landingzones.management.quotas.ResourceQuota;
import bio.terra.landingzone.service.landingzone.azure.LandingZoneService;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneRequest;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResource;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResourcesByPurpose;
import bio.terra.landingzone.service.landingzone.azure.model.StartLandingZoneCreation;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseSpringBootAzureUnitTest;
import bio.terra.workspace.common.fixtures.AzureLandingZoneFixtures;
import bio.terra.workspace.generated.model.ApiAzureLandingZone;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesList;
import bio.terra.workspace.generated.model.ApiCreateAzureLandingZoneRequestBody;
import bio.terra.workspace.generated.model.ApiCreateLandingZoneResult;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiResourceQuota;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class LandingZoneApiDispatchTest extends BaseSpringBootAzureUnitTest {
  private static final UUID LANDING_ZONE_ID = UUID.randomUUID();
  private static final UUID BILLING_PROFILE_ID = UUID.randomUUID();
  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final String JOB_ID = "CREATE_JOB_ID";
  private static final BearerToken BEARER_TOKEN = new BearerToken("fake-token");
  private static final OffsetDateTime CREATED_DATE = Instant.now().atOffset(ZoneOffset.UTC);
  private static final SpendProfileId SPEND_PROFILE_ID =
      new SpendProfileId(UUID.randomUUID().toString());

  private LandingZoneApiDispatch landingZoneApiDispatch;
  @Mock private LandingZoneService landingZoneService;
  @Mock private FeatureConfiguration featureConfiguration;
  @Mock private WorkspaceService workspaceService;

  @BeforeEach
  void setupLandingZoneTests() {
    when(featureConfiguration.isAzureEnabled()).thenReturn(true);
    landingZoneApiDispatch = new LandingZoneApiDispatch(landingZoneService, featureConfiguration);
  }

  @Test
  void createAzureLandingZone_ReturnJobRunning_Success() {
    List<LandingZone> landingZoneList = Collections.emptyList();
    String resultEndpoint = String.format("%s/%s/%s", "someServletPath", "create-result", JOB_ID);
    ApiCreateAzureLandingZoneRequestBody request =
        AzureLandingZoneFixtures.buildCreateAzureLandingZoneRequest(JOB_ID, BILLING_PROFILE_ID);
    when(landingZoneService.getLandingZonesByBillingProfile(BEARER_TOKEN, BILLING_PROFILE_ID))
        .thenReturn(landingZoneList);

    LandingZoneJobService.AsyncJobResult<StartLandingZoneCreation> createJobResult =
        AzureLandingZoneFixtures.createStartCreateJobResultWithStartLandingZoneCreation(
            JOB_ID,
            JobReport.StatusEnum.RUNNING,
            new StartLandingZoneCreation(
                LANDING_ZONE_ID, request.getDefinition(), request.getVersion()));
    when(landingZoneService.startLandingZoneCreationJob(
            eq(BEARER_TOKEN), eq(JOB_ID), any(LandingZoneRequest.class), any()))
        .thenReturn(createJobResult);
    landingZoneApiDispatch = new LandingZoneApiDispatch(landingZoneService, featureConfiguration);

    ApiCreateLandingZoneResult response =
        landingZoneApiDispatch.createAzureLandingZone(BEARER_TOKEN, request, resultEndpoint);

    assertNotNull(response);
    assertNotNull(response.getJobReport());
    assertEquals(JOB_ID, response.getJobReport().getId());
    assertEquals(ApiJobReport.StatusEnum.RUNNING, response.getJobReport().getStatus());
    assertEquals(LANDING_ZONE_ID, response.getLandingZoneId());
    assertEquals(request.getVersion(), response.getVersion());
    assertEquals(request.getDefinition(), response.getDefinition());
  }

  @Test
  void createAzureLandingZone_LandingZoneExistsForBillingProfileThrows()
      throws LandingZoneInvalidInputException {
    List<LandingZone> landingZoneList =
        List.of(
            new LandingZone(
                LANDING_ZONE_ID,
                BILLING_PROFILE_ID,
                "definition",
                "version",
                "region",
                CREATED_DATE));
    String resultEndpoint = String.format("%s/%s/%s", "someServletPath", "create-result", JOB_ID);
    ApiCreateAzureLandingZoneRequestBody request =
        AzureLandingZoneFixtures.buildCreateAzureLandingZoneRequest(JOB_ID, BILLING_PROFILE_ID);
    when(landingZoneService.getLandingZonesByBillingProfile(eq(BEARER_TOKEN), any()))
        .thenReturn(landingZoneList);

    landingZoneApiDispatch = new LandingZoneApiDispatch(landingZoneService, featureConfiguration);

    assertThrows(
        LandingZoneInvalidInputException.class,
        () -> landingZoneApiDispatch.createAzureLandingZone(BEARER_TOKEN, request, resultEndpoint));
  }

  @Test
  void createAzureLandingZone_LandingZoneDoesNotExistsExceptionIsHandledAndStartsJob() {
    when(landingZoneService.getLandingZonesByBillingProfile(eq(BEARER_TOKEN), any()))
        .thenThrow(bio.terra.landingzone.db.exception.LandingZoneNotFoundException.class);
    String resultEndpoint = String.format("%s/%s/%s", "someServletPath", "create-result", JOB_ID);
    ApiCreateAzureLandingZoneRequestBody request =
        AzureLandingZoneFixtures.buildCreateAzureLandingZoneRequest(JOB_ID, BILLING_PROFILE_ID);

    LandingZoneJobService.AsyncJobResult<StartLandingZoneCreation> createJobResult =
        AzureLandingZoneFixtures.createStartCreateJobResultWithStartLandingZoneCreation(
            JOB_ID,
            JobReport.StatusEnum.RUNNING,
            new StartLandingZoneCreation(
                LANDING_ZONE_ID, request.getDefinition(), request.getVersion()));
    when(landingZoneService.startLandingZoneCreationJob(
            eq(BEARER_TOKEN), any(), any(LandingZoneRequest.class), any()))
        .thenReturn(createJobResult);

    landingZoneApiDispatch = new LandingZoneApiDispatch(landingZoneService, featureConfiguration);

    ApiCreateLandingZoneResult result =
        landingZoneApiDispatch.createAzureLandingZone(BEARER_TOKEN, request, resultEndpoint);

    assertEquals(result.getLandingZoneId(), LANDING_ZONE_ID);
  }

  @Test
  void listAzureLandingZoneResources_TagPropagation() {
    setupLandingZoneResources();
    ApiAzureLandingZoneResourcesList response =
        landingZoneApiDispatch.listAzureLandingZoneResources(BEARER_TOKEN, LANDING_ZONE_ID);

    verify(landingZoneService, times(1))
        .listResourcesWithPurposes(eq(BEARER_TOKEN), eq(LANDING_ZONE_ID));
    assertNotNull(response);
    assertNotNull(response.getResources());
    assertEquals(2, response.getResources().size());
    response
        .getResources()
        .forEach(
            resGroup -> {
              assertEquals(
                  3,
                  resGroup.getDeployedResources().size(),
                  "deployed resources size for type " + resGroup.getPurpose());
              resGroup
                  .getDeployedResources()
                  .forEach(
                      res -> {
                        assertNotNull(
                            res.getTags(), "tags null for resource id " + res.getResourceId());
                        assertEquals(
                            2,
                            res.getTags().size(),
                            "tags size for resource id " + res.getResourceId());
                      });
            });
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
    landingZoneApiDispatch = new LandingZoneApiDispatch(landingZoneService, featureConfiguration);

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
    landingZoneApiDispatch = new LandingZoneApiDispatch(landingZoneService, featureConfiguration);
    ApiAzureLandingZoneList response =
        landingZoneApiDispatch.listAzureLandingZones(BEARER_TOKEN, BILLING_PROFILE_ID);

    verify(landingZoneService, times(1))
        .getLandingZonesByBillingProfile(eq(BEARER_TOKEN), eq(BILLING_PROFILE_ID));

    assertNotNull(response);
    assertNotNull(response.getLandingzones());
    assertEquals(1, response.getLandingzones().size());

    ApiAzureLandingZone firstLandingZone = response.getLandingzones().stream().findFirst().get();
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
    landingZoneApiDispatch = new LandingZoneApiDispatch(landingZoneService, featureConfiguration);
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
    landingZoneApiDispatch = new LandingZoneApiDispatch(landingZoneService, featureConfiguration);
    ApiAzureLandingZoneList response =
        landingZoneApiDispatch.listAzureLandingZones(BEARER_TOKEN, null);

    verify(landingZoneService, times(1)).listLandingZones(eq(BEARER_TOKEN));

    assertNotNull(response);
    assertNotNull(response.getLandingzones());
    assertEquals(2, response.getLandingzones().size());
  }

  private void setupLandingZoneResources() {
    List<LandingZoneResource> listSubnets1 =
        List.of(
            LandingZoneResource.builder()
                .resourceName("fooSubnet11")
                .resourceParentId("fooNetworkVNetId1")
                .region("fooRegion1")
                .tags(Map.of("subnetTag1", "subnetValue1", "subnetTag2", "subnetValue2"))
                .build(),
            LandingZoneResource.builder()
                .resourceName("fooSubnet12")
                .resourceParentId("fooNetworkVNetId2")
                .region("fooRegion2")
                .tags(Map.of("subnetTag1", "subnetValue1", "subnetTag3", "subnetValue3"))
                .build(),
            LandingZoneResource.builder()
                .resourceName("fooSubnet13")
                .resourceParentId("fooNetworkVNetId1")
                .region("fooRegion1")
                .tags(Map.of("subnetTag1", "subnetValue1", "subnetTag4", "subnetValue4"))
                .build());

    List<LandingZoneResource> listResources1 =
        List.of(
            LandingZoneResource.builder()
                .resourceId("Id31")
                .resourceType("fooType31")
                .region("fooRegion1")
                .tags(Map.of("resourceTag1", "resourceValue1", "resourceTag2", "resourceValue2"))
                .build(),
            LandingZoneResource.builder()
                .resourceId("Id32")
                .resourceType("fooType32")
                .region("fooRegion2")
                .tags(Map.of("resourceTag1", "resourceValue1", "resourceTag3", "resourceValue3"))
                .build(),
            LandingZoneResource.builder()
                .resourceId("Id33")
                .resourceType("fooType33")
                .region("fooRegion1")
                .tags(Map.of("resourceTag1", "resourceValue1", "resourceTag4", "resourceValue4"))
                .build());

    when(landingZoneService.listResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, SubnetResourcePurpose.WORKSPACE_STORAGE_SUBNET))
        .thenReturn(listSubnets1);
    when(landingZoneService.listResourcesByPurpose(
            BEARER_TOKEN, LANDING_ZONE_ID, ResourcePurpose.SHARED_RESOURCE))
        .thenReturn(listResources1);
    when(landingZoneService.listResourcesWithPurposes(BEARER_TOKEN, LANDING_ZONE_ID))
        .thenReturn(
            new LandingZoneResourcesByPurpose(
                Map.of(
                    SubnetResourcePurpose.WORKSPACE_STORAGE_SUBNET, listSubnets1,
                    ResourcePurpose.SHARED_RESOURCE, listResources1)));
    landingZoneApiDispatch = new LandingZoneApiDispatch(landingZoneService, featureConfiguration);
  }

  @Test
  public void getLandingZoneId_Success() {
    Workspace workspace = mock(Workspace.class);
    when(workspace.getSpendProfileId()).thenReturn(Optional.of(SPEND_PROFILE_ID));
    when(workspaceService.getWorkspace(eq(WORKSPACE_ID))).thenReturn(workspace);

    List<LandingZone> landingZoneList = Collections.singletonList(mock(LandingZone.class));
    UUID expectedLandingZoneId = UUID.randomUUID();
    when(landingZoneList.get(0).landingZoneId()).thenReturn(expectedLandingZoneId);
    // this method should always return list which contains one item
    when(landingZoneService.getLandingZonesByBillingProfile(
            eq(BEARER_TOKEN), eq(UUID.fromString(SPEND_PROFILE_ID.getId()))))
        .thenReturn(landingZoneList);

    UUID landingZoneId = landingZoneApiDispatch.getLandingZoneId(BEARER_TOKEN, workspace);

    assertNotNull(landingZoneId);
    assertEquals(expectedLandingZoneId, landingZoneId);
  }

  @Test
  public void getLandingZoneId_billingProfileEmpty_failure() {
    Workspace workspace = mock(Workspace.class);
    when(workspaceService.getWorkspace(eq(WORKSPACE_ID))).thenReturn(workspace);
    when(workspace.getSpendProfileId()).thenReturn(Optional.empty());

    assertThrows(
        LandingZoneNotFoundException.class,
        () -> landingZoneApiDispatch.getLandingZoneId(BEARER_TOKEN, workspace));
  }

  @Test
  public void getLandingZoneId_landingZoneNotFound_failure() {
    Workspace workspace = mock(Workspace.class);
    when(workspace.getSpendProfileId()).thenReturn(Optional.of(SPEND_PROFILE_ID));
    when(workspaceService.getWorkspace(eq(WORKSPACE_ID))).thenReturn(workspace);
    when(landingZoneService.getLandingZonesByBillingProfile(
            eq(BEARER_TOKEN), eq(UUID.fromString(SPEND_PROFILE_ID.getId()))))
        .thenReturn(Collections.emptyList());

    assertThrows(
        LandingZoneNotFoundException.class,
        () -> landingZoneApiDispatch.getLandingZoneId(BEARER_TOKEN, workspace));
  }

  @Test
  public void getResourceQuota_returnsValidQuotaInformation() {

    String azureResourceId =
        "/subscription/00000000-0000-0000-0000-000000000000/resourceGroups/mrg/providers/Microsoft.Batch/batchAccounts/myaccount";
    Map<String, Object> quotaValues = new HashMap<>();
    quotaValues.put("key1", 1);
    quotaValues.put("key2", false);
    quotaValues.put("key3", "value");

    String resourceType = "Microsoft.Batch/batchAccounts";
    when(landingZoneService.getResourceQuota(BEARER_TOKEN, LANDING_ZONE_ID, azureResourceId))
        .thenReturn(new ResourceQuota(azureResourceId, resourceType, quotaValues));

    ApiResourceQuota apiResourceQuota =
        landingZoneApiDispatch.getResourceQuota(BEARER_TOKEN, LANDING_ZONE_ID, azureResourceId);

    assertThat(apiResourceQuota.getResourceType(), equalTo(resourceType));
    assertThat(apiResourceQuota.getAzureResourceId(), equalTo(azureResourceId));
    assertThat(apiResourceQuota.getQuotaValues(), equalTo(quotaValues));
  }
}
