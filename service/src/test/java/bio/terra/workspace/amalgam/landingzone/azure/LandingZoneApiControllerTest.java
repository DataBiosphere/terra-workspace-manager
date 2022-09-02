package bio.terra.workspace.amalgam.landingzone.azure;

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
import bio.terra.landingzone.service.landingzone.azure.exception.LandingZoneDeleteNotImplemented;
import bio.terra.landingzone.service.landingzone.azure.model.DeployedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneDefinition;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.MockBeanUnitTest;
import bio.terra.workspace.common.fixtures.AzureLandingZoneFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
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

public class LandingZoneApiControllerTest extends MockBeanUnitTest {
  private static final String CREATE_AZURE_LANDING_ZONE = "/api/landingzones/v1/azure";
  private static final String GET_CREATE_AZURE_LANDING_ZONE_RESULT =
      "/api/landingzones/v1/azure/create-result";
  private static final String LIST_AZURE_LANDING_ZONES_DEFINITIONS_PATH =
      "/api/landingzones/definitions/v1/azure";
  private static final String DELETE_AZURE_LANDING_ZONE_PATH = "/api/landingzones/v1/azure";
  private static final String JOB_ID = "newJobId";

  @Autowired private MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @Test
  public void createAzureLandingZoneJobRunning() throws Exception {
    LandingZoneJobService.AsyncJobResult<DeployedLandingZone> asyncJobResult =
        AzureLandingZoneFixtures.createJobResultWithRunningState(JOB_ID);

    when(getMockLandingZoneService().startLandingZoneCreationJob(any(), any(), any())).thenReturn(JOB_ID);
    when(getMockLandingZoneService().getAsyncJobResult(any())).thenReturn(asyncJobResult);
    when(getMockFeatureConfiguration().isAzureEnabled()).thenReturn(true);

    var requestBody = AzureLandingZoneFixtures.buildCreateAzureLandingZoneRequest(JOB_ID);

    mockMvc
        .perform(
            post(CREATE_AZURE_LANDING_ZONE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody))
                .characterEncoding("utf-8"))
        .andExpect(status().is(HttpStatus.SC_ACCEPTED))
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport.id").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport.id", Matchers.is(JOB_ID)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingZone").doesNotExist());
  }

  @Test
  public void createAzureLandingZoneJobSucceeded() throws Exception {
    LandingZoneJobService.AsyncJobResult<DeployedLandingZone> asyncJobResult =
        AzureLandingZoneFixtures.createJobResultWithSucceededState(JOB_ID, "lzId");

    when(getMockLandingZoneService().startLandingZoneCreationJob(any(), any(), any())).thenReturn(JOB_ID);
    when(getMockLandingZoneService().getAsyncJobResult(any())).thenReturn(asyncJobResult);
    when(getMockFeatureConfiguration().isAzureEnabled()).thenReturn(true);

    // TODO SG: check if we need name and description??
    var requestBody = AzureLandingZoneFixtures.buildCreateAzureLandingZoneRequest(JOB_ID);

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
  public void createAzureLandingZoneWithoutDefinitionValidationFailed() throws Exception {
    LandingZoneJobService.AsyncJobResult<DeployedLandingZone> asyncJobResult =
        AzureLandingZoneFixtures.createJobResultWithSucceededState(JOB_ID, "lzId");

    when(getMockLandingZoneService().startLandingZoneCreationJob(any(), any(), any())).thenReturn(JOB_ID);
    when(getMockLandingZoneService().getAsyncJobResult(any())).thenReturn(asyncJobResult);
    when(getMockFeatureConfiguration().isAzureEnabled()).thenReturn(true);

    var requestBody =
        AzureLandingZoneFixtures.buildCreateAzureLandingZoneRequestWithoutDefinition(JOB_ID);

    mockMvc
        .perform(
            post(CREATE_AZURE_LANDING_ZONE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody))
                .characterEncoding("utf-8"))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void createAzureLandingZoneWithoutTargetValidationFailed() throws Exception {
    LandingZoneJobService.AsyncJobResult<DeployedLandingZone> asyncJobResult =
        AzureLandingZoneFixtures.createJobResultWithSucceededState(JOB_ID, "lzId");

    when(getMockLandingZoneService().startLandingZoneCreationJob(any(), any(), any())).thenReturn(JOB_ID);
    when(getMockLandingZoneService().getAsyncJobResult(any())).thenReturn(asyncJobResult);
    when(getMockFeatureConfiguration().isAzureEnabled()).thenReturn(true);

    var requestBody =
        AzureLandingZoneFixtures.buildCreateAzureLandingZoneRequestWithoutTarget(JOB_ID);

    mockMvc
        .perform(
            post(CREATE_AZURE_LANDING_ZONE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody))
                .characterEncoding("utf-8"))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void getCreateAzureLandingZoneResultJobRunning() throws Exception {
    LandingZoneJobService.AsyncJobResult<DeployedLandingZone> asyncJobResult =
        AzureLandingZoneFixtures.createJobResultWithRunningState(JOB_ID);

    when(getMockLandingZoneService().getAsyncJobResult(any())).thenReturn(asyncJobResult);

    mockMvc
        .perform(get(GET_CREATE_AZURE_LANDING_ZONE_RESULT + "/{jobId}", JOB_ID))
        .andExpect(status().is(HttpStatus.SC_ACCEPTED))
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport.id").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport.id", Matchers.is(JOB_ID)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingZone").doesNotExist());
  }

  @Test
  public void getCreateAzureLandingZoneResultJobSucceeded() throws Exception {
    LandingZoneJobService.AsyncJobResult<DeployedLandingZone> asyncJobResult =
        AzureLandingZoneFixtures.createJobResultWithSucceededState(JOB_ID, "lzId");

    when(getMockLandingZoneService().getAsyncJobResult(any())).thenReturn(asyncJobResult);

    mockMvc
        .perform(get(GET_CREATE_AZURE_LANDING_ZONE_RESULT + "/{jobId}", JOB_ID))
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
    when(getMockLandingZoneService().listLandingZoneDefinitions()).thenReturn(definitions);
    when(getMockFeatureConfiguration().isAzureEnabled()).thenReturn(true);

    mockMvc
        .perform(get(LIST_AZURE_LANDING_ZONES_DEFINITIONS_PATH))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones").isArray());
  }

  @Test
  public void deleteAzureLandingZoneSuccess() throws Exception {
    doNothing().when(getMockLandingZoneService()).deleteLandingZone(anyString());
    mockMvc
        .perform(delete(DELETE_AZURE_LANDING_ZONE_PATH + "/{landingZoneId}", "lz-1"))
        .andExpect(status().isNoContent());
  }

  @Test
  public void deleteAzureLandingZoneNotImplemented() throws Exception {
    doThrow(LandingZoneDeleteNotImplemented.class)
        .when(getMockLandingZoneService())
        .deleteLandingZone(anyString());
    mockMvc
        .perform(delete(DELETE_AZURE_LANDING_ZONE_PATH + "/{landingZoneId}", "lz-0"))
        .andExpect(status().isNotImplemented());
  }
}
