package bio.terra.workspace.app.configuration.external.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.landingzone.job.LandingZoneJobService;
import bio.terra.landingzone.job.model.JobReport;
import bio.terra.landingzone.library.LandingZoneManagerProvider;
import bio.terra.landingzone.service.landingzone.azure.LandingZoneService;
import bio.terra.landingzone.service.landingzone.azure.exception.LandingZoneDeleteNotImplemented;
import bio.terra.landingzone.service.landingzone.azure.model.DeployedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneDefinition;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResource;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.generated.model.ApiAzureContext;
import bio.terra.workspace.generated.model.ApiCreateAzureLandingZoneRequestBody;
import bio.terra.workspace.generated.model.ApiJobControl;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@AutoConfigureMockMvc
public class AzureLandingZoneApiControllerTest extends BaseConnectedTest {
  private static final String CREATE_AZURE_LANDING_ZONE = "/api/azure/landingzones/v1";
  private static final String LIST_AZURE_LANDING_ZONES_DEFINITIONS_PATH =
      "/api/azure/landingzones/definitions/v1";
  private static final String DELETE_AZURE_LANDING_ZONE_PATH = "/api/azure/landingzones/v1";

  @Autowired private MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @MockBean private LandingZoneService landingZoneService;
  @MockBean private LandingZoneManagerProvider landingZoneManagerProvider;
  @MockBean private FeatureConfiguration featureConfiguration;

  @Test
  public void createAzureLandingZoneJobRunning() throws Exception {
    var jobId = "newJobId";
    LandingZoneJobService.AsyncJobResult<DeployedLandingZone> asyncJobResult =
        new LandingZoneJobService.AsyncJobResult<>();
    asyncJobResult.jobReport(
        new JobReport()
            .id(jobId)
            .description("description")
            .status(JobReport.StatusEnum.RUNNING)
            .statusCode(HttpStatus.SC_ACCEPTED)
            .submitted(Instant.now().toString())
            .resultURL("create-result/"));

    when(landingZoneService.startLandingZoneCreationJob(any(), any(), any())).thenReturn(jobId);
    when(landingZoneService.getAsyncJobResult(any())).thenReturn(asyncJobResult);
    when(featureConfiguration.isAzureEnabled()).thenReturn(true);

    // TODO SG: check if we need name and description??
    var requestBody =
        new ApiCreateAzureLandingZoneRequestBody()
            .azureContext(
                new ApiAzureContext()
                    .resourceGroupId("resourceGroup")
                    .subscriptionId("subscriptionId")
                    .tenantId("tenantId"))
            .jobControl(new ApiJobControl().id(jobId))
            .definition("azureLandingZoneDefinition")
            .version("v1")
            .name("test");
    mockMvc
        .perform(
            post(CREATE_AZURE_LANDING_ZONE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody))
                .characterEncoding("utf-8"))
        .andExpect(status().is(HttpStatus.SC_ACCEPTED))
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport.id").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport.id", Matchers.is(jobId)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingZone").doesNotExist());
  }

  @Test
  public void createAzureLandingZoneJobSucceeded() throws Exception {
    var jobId = "newJobId";
    LandingZoneJobService.AsyncJobResult<DeployedLandingZone> asyncJobResult =
        new LandingZoneJobService.AsyncJobResult<>();
    asyncJobResult.jobReport(
        new JobReport()
            .id(jobId)
            .description("description")
            .status(JobReport.StatusEnum.SUCCEEDED)
            .statusCode(HttpStatus.SC_OK)
            .submitted(Instant.now().toString())
            .resultURL("create-result/"));
    asyncJobResult.result(
        new DeployedLandingZone(
            "lzId",
            Collections.singletonList(
                new LandingZoneResource("resId", "resourceType", null, "westus"))));

    when(landingZoneService.startLandingZoneCreationJob(any(), any(), any())).thenReturn(jobId);
    when(landingZoneService.getAsyncJobResult(any())).thenReturn(asyncJobResult);
    when(featureConfiguration.isAzureEnabled()).thenReturn(true);

    // TODO SG: check if we need name and description??
    var requestBody =
        new ApiCreateAzureLandingZoneRequestBody()
            .azureContext(
                new ApiAzureContext()
                    .resourceGroupId("resourceGroup")
                    .subscriptionId("subscriptionId")
                    .tenantId("tenantId"))
            .jobControl(new ApiJobControl().id("jobId"))
            .definition("azureLandingZoneDefinition")
            .version("v1")
            .name("test");
    mockMvc
        .perform(
            post(CREATE_AZURE_LANDING_ZONE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody))
                .characterEncoding("utf-8"))
        .andExpect(status().is(HttpStatus.SC_OK))
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport.id").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingZone").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingZone.id").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingZone.id", Matchers.is("lzId")));
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
    when(landingZoneService.listLandingZoneDefinitions()).thenReturn(definitions);
    when(featureConfiguration.isAzureEnabled()).thenReturn(true);

    mockMvc
        .perform(get(LIST_AZURE_LANDING_ZONES_DEFINITIONS_PATH))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones").isArray());
  }

  @Test
  public void deleteAzureLandingZoneSuccess() throws Exception {
    doNothing().when(landingZoneService).deleteLandingZone(anyString());
    mockMvc
        .perform(delete(DELETE_AZURE_LANDING_ZONE_PATH + "/{landingZoneId}", "lz-1"))
        .andExpect(status().isNoContent());
  }

  @Test
  public void deleteAzureLandingZoneNotImplemented() throws Exception {
    doThrow(LandingZoneDeleteNotImplemented.class)
        .when(landingZoneService)
        .deleteLandingZone(anyString());
    mockMvc
        .perform(delete(DELETE_AZURE_LANDING_ZONE_PATH + "/{landingZoneId}", "lz-0"))
        .andExpect(status().isNotImplemented());
  }
}
