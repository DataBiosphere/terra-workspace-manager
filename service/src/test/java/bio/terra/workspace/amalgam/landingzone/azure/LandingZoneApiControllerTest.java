package bio.terra.workspace.amalgam.landingzone.azure;

import static bio.terra.workspace.common.utils.MockMvcUtils.AUTH_HEADER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.job.LandingZoneJobService;
import bio.terra.landingzone.library.landingzones.deployment.LandingZonePurpose;
import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.landingzone.library.landingzones.deployment.SubnetResourcePurpose;
import bio.terra.landingzone.service.landingzone.azure.LandingZoneService;
import bio.terra.landingzone.service.landingzone.azure.exception.LandingZoneDeleteNotImplemented;
import bio.terra.landingzone.service.landingzone.azure.model.DeployedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneDefinition;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResource;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResourcesByPurpose;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.common.fixtures.AzureLandingZoneFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

public class LandingZoneApiControllerTest extends BaseAzureUnitTest {
  private static final String AZURE_LANDING_ZONE_PATH = "/api/landingzones/v1/azure";
  private static final String GET_CREATE_AZURE_LANDING_ZONE_RESULT =
      "/api/landingzones/v1/azure/create-result";
  private static final String LIST_AZURE_LANDING_ZONES_DEFINITIONS_PATH =
      "/api/landingzones/definitions/v1/azure";
  private static final String JOB_ID = "newJobId";
  private static final UUID LANDING_ZONE_ID = UUID.randomUUID();
  private static final BearerToken BEARER_TOKEN = new BearerToken("fake-token");
  @Autowired private MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @MockBean private LandingZoneService landingZoneService;
  @MockBean private FeatureConfiguration featureConfiguration;

  @Test
  public void createAzureLandingZoneJobRunning() throws Exception {
    LandingZoneJobService.AsyncJobResult<DeployedLandingZone> asyncJobResult =
        AzureLandingZoneFixtures.createJobResultWithRunningState(JOB_ID);

    when(landingZoneService.startLandingZoneCreationJob(any(), any(), any(), any()))
        .thenReturn(JOB_ID);
    when(landingZoneService.getAsyncJobResult(any(), any())).thenReturn(asyncJobResult);
    when(featureConfiguration.isAzureEnabled()).thenReturn(true);

    var requestBody = AzureLandingZoneFixtures.buildCreateAzureLandingZoneRequest(JOB_ID);

    mockMvc
        .perform(
            post(AZURE_LANDING_ZONE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody))
                .characterEncoding("utf-8")
                .header(AUTH_HEADER, "Bearer " + BEARER_TOKEN.getToken()))
        .andExpect(status().is(HttpStatus.SC_ACCEPTED))
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport.id").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport.id", Matchers.is(JOB_ID)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingZone").doesNotExist());
  }

  @Test
  public void createAzureLandingZoneJobSucceeded() throws Exception {
    LandingZoneJobService.AsyncJobResult<DeployedLandingZone> asyncJobResult =
        AzureLandingZoneFixtures.createJobResultWithSucceededState(JOB_ID, LANDING_ZONE_ID);

    when(landingZoneService.startLandingZoneCreationJob(any(), any(), any(), any()))
        .thenReturn(JOB_ID);
    when(landingZoneService.getAsyncJobResult(any(), any())).thenReturn(asyncJobResult);
    when(featureConfiguration.isAzureEnabled()).thenReturn(true);

    // TODO SG: check if we need name and description??
    var requestBody = AzureLandingZoneFixtures.buildCreateAzureLandingZoneRequest(JOB_ID);

    mockMvc
        .perform(
            post(AZURE_LANDING_ZONE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody))
                .characterEncoding("utf-8")
                .header(AUTH_HEADER, "Bearer " + BEARER_TOKEN.getToken()))
        .andExpect(status().is(HttpStatus.SC_OK))
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport.id").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingZone").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingZone.id").exists())
        .andExpect(
            MockMvcResultMatchers.jsonPath(
                "$.landingZone.id", Matchers.is(LANDING_ZONE_ID.toString())));
  }

  @Test
  public void createAzureLandingZoneWithoutDefinitionValidationFailed() throws Exception {
    LandingZoneJobService.AsyncJobResult<DeployedLandingZone> asyncJobResult =
        AzureLandingZoneFixtures.createJobResultWithSucceededState(JOB_ID, LANDING_ZONE_ID);

    when(landingZoneService.startLandingZoneCreationJob(any(), any(), any(), any()))
        .thenReturn(JOB_ID);
    when(landingZoneService.getAsyncJobResult(any(), any())).thenReturn(asyncJobResult);
    when(featureConfiguration.isAzureEnabled()).thenReturn(true);

    var requestBody =
        AzureLandingZoneFixtures.buildCreateAzureLandingZoneRequestWithoutDefinition(JOB_ID);

    mockMvc
        .perform(
            post(AZURE_LANDING_ZONE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody))
                .characterEncoding("utf-8")
                .header(AUTH_HEADER, "Bearer " + BEARER_TOKEN.getToken()))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void createAzureLandingZoneWithoutTargetValidationFailed() throws Exception {
    LandingZoneJobService.AsyncJobResult<DeployedLandingZone> asyncJobResult =
        AzureLandingZoneFixtures.createJobResultWithSucceededState(JOB_ID, LANDING_ZONE_ID);

    when(landingZoneService.startLandingZoneCreationJob(any(), any(), any(), any()))
        .thenReturn(JOB_ID);
    when(landingZoneService.getAsyncJobResult(any(), any())).thenReturn(asyncJobResult);
    when(featureConfiguration.isAzureEnabled()).thenReturn(true);

    var requestBody =
        AzureLandingZoneFixtures.buildCreateAzureLandingZoneRequestWithoutTarget(JOB_ID);

    mockMvc
        .perform(
            post(AZURE_LANDING_ZONE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody))
                .characterEncoding("utf-8")
                .header(AUTH_HEADER, "Bearer " + BEARER_TOKEN.getToken()))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void getCreateAzureLandingZoneResultJobRunning() throws Exception {
    LandingZoneJobService.AsyncJobResult<DeployedLandingZone> asyncJobResult =
        AzureLandingZoneFixtures.createJobResultWithRunningState(JOB_ID);

    when(landingZoneService.getAsyncJobResult(any(), any())).thenReturn(asyncJobResult);

    mockMvc
        .perform(
            get(GET_CREATE_AZURE_LANDING_ZONE_RESULT + "/{jobId}", JOB_ID)
                .header(AUTH_HEADER, "Bearer " + BEARER_TOKEN.getToken()))
        .andExpect(status().is(HttpStatus.SC_ACCEPTED))
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport.id").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport.id", Matchers.is(JOB_ID)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingZone").doesNotExist());
  }

  @Test
  public void getCreateAzureLandingZoneResultJobSucceeded() throws Exception {
    LandingZoneJobService.AsyncJobResult<DeployedLandingZone> asyncJobResult =
        AzureLandingZoneFixtures.createJobResultWithSucceededState(JOB_ID, LANDING_ZONE_ID);

    when(landingZoneService.getAsyncJobResult(any(), any())).thenReturn(asyncJobResult);

    mockMvc
        .perform(
            get(GET_CREATE_AZURE_LANDING_ZONE_RESULT + "/{jobId}", JOB_ID)
                .header(AUTH_HEADER, "Bearer " + BEARER_TOKEN.getToken()))
        .andExpect(status().is(HttpStatus.SC_OK))
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport.id").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingZone").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingZone.id").exists())
        .andExpect(
            MockMvcResultMatchers.jsonPath(
                "$.landingZone.id", Matchers.is(LANDING_ZONE_ID.toString())));
  }

  @Test
  public void listAzureLandingZoneDefinitionsSuccess() throws Exception {
    var definitions =
        List.of(
            LandingZoneDefinition.builder()
                .definition("fooDefinition")
                .name("fooName")
                .description("fooDescription")
                .version("v1")
                .build(),
            LandingZoneDefinition.builder()
                .definition("fooDefinition")
                .name("fooName")
                .description("fooDescription")
                .version("v2")
                .build(),
            LandingZoneDefinition.builder()
                .definition("barDefinition")
                .name("barName")
                .description("barDescription")
                .version("v1")
                .build());
    when(landingZoneService.listLandingZoneDefinitions(any())).thenReturn(definitions);
    when(featureConfiguration.isAzureEnabled()).thenReturn(true);

    mockMvc
        .perform(
            get(LIST_AZURE_LANDING_ZONES_DEFINITIONS_PATH)
                .header(AUTH_HEADER, "Bearer " + BEARER_TOKEN.getToken()))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones").isArray());
  }

  @Test
  public void deleteAzureLandingZoneSuccess() throws Exception {
    doNothing().when(landingZoneService).deleteLandingZone(anyString());
    mockMvc
        .perform(
            delete(AZURE_LANDING_ZONE_PATH + "/{landingZoneId}", LANDING_ZONE_ID)
                .header(AUTH_HEADER, "Bearer " + BEARER_TOKEN.getToken()))
        .andExpect(status().isNoContent());
  }

  @Test
  public void deleteAzureLandingZoneNotImplemented() throws Exception {
    doThrow(LandingZoneDeleteNotImplemented.class)
        .when(landingZoneService)
        .deleteLandingZone(anyString());
    mockMvc
        .perform(
            delete(AZURE_LANDING_ZONE_PATH + "/{landingZoneId}", LANDING_ZONE_ID)
                .header(AUTH_HEADER, "Bearer " + BEARER_TOKEN.getToken()))
        .andExpect(status().isNotImplemented());
  }

  @Test
  public void listAzureLandingZoneResourcesSuccess() throws Exception {
    var listSubnets1 =
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
    LandingZonePurpose purposeSubnets1 = SubnetResourcePurpose.AKS_NODE_POOL_SUBNET;

    var listSubnets2 =
        List.of(
            LandingZoneResource.builder()
                .resourceName("fooSubnet21")
                .resourceParentId("fooNetworkVNetId1")
                .region("fooRegion1")
                .build());
    LandingZonePurpose purposeSubnets2 = SubnetResourcePurpose.POSTGRESQL_SUBNET;

    var listResources3 =
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
    LandingZonePurpose purposeSubnets3 = ResourcePurpose.SHARED_RESOURCE;

    var listResources4 =
        List.of(
            LandingZoneResource.builder()
                .resourceId("Id41")
                .resourceType("fooType41")
                .region("fooRegion1")
                .build());
    LandingZonePurpose purposeSubnets4 = ResourcePurpose.WLZ_RESOURCE;

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

    when(landingZoneService.listResourcesWithPurposes(any(), any())).thenReturn(groupedResources);
    when(featureConfiguration.isAzureEnabled()).thenReturn(true);

    mockMvc
        .perform(
            get(AZURE_LANDING_ZONE_PATH + "/{landingZoneId}/resources", LANDING_ZONE_ID)
                .header(AUTH_HEADER, "Bearer " + BEARER_TOKEN.getToken()))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.id").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.id", Matchers.is(LANDING_ZONE_ID.toString())))
        .andExpect(MockMvcResultMatchers.jsonPath("$.resources").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.resources").isArray())
        .andExpect(
            MockMvcResultMatchers.jsonPath(
                "$.resources[0].purpose",
                Matchers.in(
                    List.of(
                        purposeSubnets1.toString(), purposeSubnets2.toString(),
                        purposeSubnets3.toString(), purposeSubnets4.toString()))))
        .andExpect(MockMvcResultMatchers.jsonPath("$.resources[0].deployedResources").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.resources[0].deployedResources").isArray());
  }

  @Test
  public void listAzureLandingZoneResources_NoResources() throws Exception {
    LandingZoneResourcesByPurpose groupedResources =
        new LandingZoneResourcesByPurpose(Collections.emptyMap());

    when(landingZoneService.listResourcesWithPurposes(any(), any())).thenReturn(groupedResources);
    when(featureConfiguration.isAzureEnabled()).thenReturn(true);

    mockMvc
        .perform(
            get(AZURE_LANDING_ZONE_PATH + "/{landingZoneId}/resources", LANDING_ZONE_ID)
                .header(AUTH_HEADER, "Bearer " + BEARER_TOKEN.getToken()))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.id").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.resources").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.resources").isArray());
  }
}
