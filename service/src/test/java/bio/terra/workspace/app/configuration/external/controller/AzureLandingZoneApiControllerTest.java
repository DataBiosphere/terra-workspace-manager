package bio.terra.workspace.app.configuration.external.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.landingzone.library.AzureLandingZoneManagerProvider;
import bio.terra.landingzone.library.landingzones.management.LandingZoneManager;
import bio.terra.landingzone.service.landingzone.azure.AzureLandingZoneService;
import bio.terra.landingzone.service.landingzone.azure.exception.AzureLandingZoneDeleteNotImplemented;
import bio.terra.landingzone.service.landingzone.azure.model.AzureLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.AzureLandingZoneDefinition;
import bio.terra.landingzone.service.landingzone.azure.model.AzureLandingZoneResource;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.generated.model.ApiAzureContext;
import bio.terra.workspace.generated.model.ApiCreateAzureLandingZoneRequestBody;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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

  @MockBean private AzureLandingZoneService azureLandingZoneService;
  @MockBean private AzureLandingZoneManagerProvider azureLandingZoneManagerProvider;
  @MockBean private FeatureConfiguration featureConfiguration;

  @Test
  public void createAzureLandingZoneSuccess() throws Exception {
    var azureLandingZone =
        AzureLandingZone.builder()
            .id("lz-123")
            .deployedResources(
                List.of(
                    AzureLandingZoneResource.builder()
                        .resourceId("resourceId")
                        .resourceType("resourceType")
                        .region("westus")
                        .tags(Map.of("param1", "value1"))
                        .build()))
            .build();
    when(azureLandingZoneService.createLandingZone(any(), any())).thenReturn(azureLandingZone);
    when(featureConfiguration.isAzureEnabled()).thenReturn(true);

    var requestBody =
        new ApiCreateAzureLandingZoneRequestBody()
            .azureContext(
                new ApiAzureContext()
                    .resourceGroupId("resourceGroup")
                    .subscriptionId("subscriptionId")
                    .tenantId("tenantId"))
            .definition("azureLandingZoneDefinition")
            .version("v1");
    mockMvc
        .perform(
            post(CREATE_AZURE_LANDING_ZONE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody))
                .characterEncoding("utf-8"))
        .andExpect(status().isCreated())
        .andExpect(MockMvcResultMatchers.jsonPath("$.id").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.resources").exists());
  }

  @Test
  public void listAzureLandingZoneDefinitionsSuccess() throws Exception {
    var definitions =
        List.of(
            AzureLandingZoneDefinition.builder()
                .definition("fooDefinition")
                .name("fooName")
                .description("fooDescription")
                .version("v1")
                .build(),
            AzureLandingZoneDefinition.builder()
                .definition("fooDefinition")
                .name("fooName")
                .description("fooDescription")
                .version("v2")
                .build(),
            AzureLandingZoneDefinition.builder()
                .definition("barDefinition")
                .name("barName")
                .description("barDescription")
                .version("v1")
                .build());
    var mockLandingZoneManager = mock(LandingZoneManager.class);
    when(azureLandingZoneService.listLandingZoneDefinitions()).thenReturn(definitions);
    when(azureLandingZoneManagerProvider.createLandingZoneManager(any(), any()))
        .thenReturn(mockLandingZoneManager);
    when(featureConfiguration.isAzureEnabled()).thenReturn(true);

    mockMvc
        .perform(get(LIST_AZURE_LANDING_ZONES_DEFINITIONS_PATH))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones").exists());
  }

  @Test
  public void deleteAzureLandingZoneSuccess() throws Exception {
    doNothing().when(azureLandingZoneService).deleteLandingZone(anyString());
    mockMvc
        .perform(delete(DELETE_AZURE_LANDING_ZONE_PATH + "/{landingZoneId}", "lz-1"))
        .andExpect(status().isNoContent());
  }

  @Test
  public void deleteAzureLandingZoneNotImplemented() throws Exception {
    doThrow(AzureLandingZoneDeleteNotImplemented.class)
        .when(azureLandingZoneService)
        .deleteLandingZone(anyString());
    mockMvc
        .perform(delete(DELETE_AZURE_LANDING_ZONE_PATH + "/{landingZoneId}", "lz-0"))
        .andExpect(status().isNotImplemented());
  }
}
