package bio.terra.workspace.amalgam.landingzone.azure;

import static bio.terra.workspace.common.mocks.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.mocks.MockMvcUtils.addAuth;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.landingzone.job.LandingZoneJobService;
import bio.terra.landingzone.job.model.JobReport;
import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.landingzone.library.landingzones.deployment.SubnetResourcePurpose;
import bio.terra.landingzone.service.landingzone.azure.model.DeletedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.DeployedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneDefinition;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResourcesByPurpose;
import bio.terra.landingzone.service.landingzone.azure.model.StartLandingZoneCreation;
import bio.terra.landingzone.service.landingzone.azure.model.StartLandingZoneDeletion;
import bio.terra.workspace.common.BaseAzureSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.AzureLandingZoneFixtures;
import bio.terra.workspace.generated.model.ApiCreateAzureLandingZoneRequestBody;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneRequestBody;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

public class LandingZoneApiControllerTest extends BaseAzureSpringBootUnitTest {
  private static final String AZURE_LANDING_ZONE_PATH = "/api/landingzones/v1/azure";
  private static final String GET_CREATE_AZURE_LANDING_ZONE_RESULT =
      "/api/landingzones/v1/azure/create-result";
  private static final String LIST_AZURE_LANDING_ZONES_DEFINITIONS_PATH =
      "/api/landingzones/definitions/v1/azure";
  private static final String JOB_ID = "newJobId";
  private static final UUID LANDING_ZONE_ID = UUID.randomUUID();
  private static final UUID BILLING_PROFILE_ID = UUID.randomUUID();
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  public void createAzureLandingZoneJobRunning() throws Exception {
    LandingZoneJobService.AsyncJobResult<StartLandingZoneCreation> asyncJobResult =
        AzureLandingZoneFixtures.createStartCreateJobResult(
            JOB_ID, JobReport.StatusEnum.RUNNING, LANDING_ZONE_ID);

    when(mockLandingZoneService().startLandingZoneCreationJob(any(), any(), any(), any()))
        .thenReturn(asyncJobResult);
    when(mockFeatureConfiguration().isAzureEnabled()).thenReturn(true);

    ApiCreateAzureLandingZoneRequestBody requestBody =
        AzureLandingZoneFixtures.buildCreateAzureLandingZoneRequest(JOB_ID, BILLING_PROFILE_ID);

    mockMvc
        .perform(
            addAuth(
                post(AZURE_LANDING_ZONE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .characterEncoding("utf-8"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_ACCEPTED))
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport.id").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport.id", Matchers.is(JOB_ID)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingZone").doesNotExist());
  }

  @Test
  public void createAzureLandingZoneJobSucceeded() throws Exception {
    LandingZoneJobService.AsyncJobResult<StartLandingZoneCreation> asyncJobResult =
        AzureLandingZoneFixtures.createStartCreateJobResult(
            JOB_ID, JobReport.StatusEnum.SUCCEEDED, LANDING_ZONE_ID);

    when(mockLandingZoneService().startLandingZoneCreationJob(any(), any(), any(), any()))
        .thenReturn(asyncJobResult);
    when(mockFeatureConfiguration().isAzureEnabled()).thenReturn(true);

    // TODO SG: check if we need name and description??
    ApiCreateAzureLandingZoneRequestBody requestBody =
        AzureLandingZoneFixtures.buildCreateAzureLandingZoneRequest(JOB_ID, BILLING_PROFILE_ID);

    mockMvc
        .perform(
            addAuth(
                post(AZURE_LANDING_ZONE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .characterEncoding("utf-8"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_OK))
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport.id").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingZoneId").exists())
        .andExpect(
            MockMvcResultMatchers.jsonPath(
                "$.landingZoneId", Matchers.is(LANDING_ZONE_ID.toString())));
  }

  @Test
  public void createAzureLandingZoneWithoutDefinitionValidationFailed() throws Exception {
    LandingZoneJobService.AsyncJobResult<StartLandingZoneCreation> asyncJobResult =
        AzureLandingZoneFixtures.createStartCreateJobResult(
            JOB_ID, JobReport.StatusEnum.RUNNING, LANDING_ZONE_ID);

    when(mockLandingZoneService().startLandingZoneCreationJob(any(), any(), any(), any()))
        .thenReturn(asyncJobResult);
    when(mockFeatureConfiguration().isAzureEnabled()).thenReturn(true);

    ApiCreateAzureLandingZoneRequestBody requestBody =
        AzureLandingZoneFixtures.buildCreateAzureLandingZoneRequestWithoutDefinition(JOB_ID);

    mockMvc
        .perform(
            addAuth(
                post(AZURE_LANDING_ZONE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .characterEncoding("utf-8"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void createAzureLandingZoneWithoutTargetValidationFailed() throws Exception {
    LandingZoneJobService.AsyncJobResult<DeployedLandingZone> asyncJobResult =
        AzureLandingZoneFixtures.createJobResultWithSucceededState(JOB_ID, LANDING_ZONE_ID);

    when(mockLandingZoneService().getAsyncJobResult(any(), any())).thenReturn(asyncJobResult);
    when(mockFeatureConfiguration().isAzureEnabled()).thenReturn(true);

    ApiCreateAzureLandingZoneRequestBody requestBody =
        AzureLandingZoneFixtures.buildCreateAzureLandingZoneRequestWithoutTarget(JOB_ID);

    mockMvc
        .perform(
            addAuth(
                post(AZURE_LANDING_ZONE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .characterEncoding("utf-8"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void getCreateAzureLandingZoneResultJobRunning() throws Exception {
    LandingZoneJobService.AsyncJobResult<DeployedLandingZone> asyncJobResult =
        AzureLandingZoneFixtures.createJobResultWithRunningState(JOB_ID);

    when(mockLandingZoneService().getAsyncJobResult(any(), any())).thenReturn(asyncJobResult);

    mockMvc
        .perform(
            addAuth(get(GET_CREATE_AZURE_LANDING_ZONE_RESULT + "/{jobId}", JOB_ID), USER_REQUEST))
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

    when(mockLandingZoneService().getAsyncJobResult(any(), any())).thenReturn(asyncJobResult);

    mockMvc
        .perform(
            addAuth(get(GET_CREATE_AZURE_LANDING_ZONE_RESULT + "/{jobId}", JOB_ID), USER_REQUEST))
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
    List<LandingZoneDefinition> definitions =
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
    when(mockLandingZoneService().listLandingZoneDefinitions(any())).thenReturn(definitions);
    when(mockFeatureConfiguration().isAzureEnabled()).thenReturn(true);

    mockMvc
        .perform(addAuth(get(LIST_AZURE_LANDING_ZONES_DEFINITIONS_PATH), USER_REQUEST))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones").isArray());
  }

  @Test
  public void deleteAzureLandingZoneSuccess() throws Exception {
    LandingZoneJobService.AsyncJobResult<StartLandingZoneDeletion> asyncJobResult =
        AzureLandingZoneFixtures.createStartDeleteJobResultWithRunningState(
            JOB_ID, LANDING_ZONE_ID);
    when(mockLandingZoneService().startLandingZoneDeletionJob(any(), any(), any(), any()))
        .thenReturn(asyncJobResult);
    when(mockFeatureConfiguration().isAzureEnabled()).thenReturn(true);

    ApiDeleteAzureLandingZoneRequestBody requestBody =
        AzureLandingZoneFixtures.buildDeleteAzureLandingZoneRequest(JOB_ID);

    mockMvc
        .perform(
            addAuth(
                post(AZURE_LANDING_ZONE_PATH + "/{landingZoneId}", LANDING_ZONE_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .characterEncoding("utf-8"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_ACCEPTED))
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport.id").exists());
  }

  @ParameterizedTest
  @MethodSource("getDeleteAzureLandingZoneResultScenario")
  public void getDeleteAzureLandingZoneResultSuccess(
      JobReport.StatusEnum jobStatus,
      int expectedHttpStatus,
      ResultMatcher landingZoneMatcher,
      ResultMatcher resourcesMatcher)
      throws Exception {
    LandingZoneJobService.AsyncJobResult<DeletedLandingZone> asyncJobResult =
        AzureLandingZoneFixtures.createDeleteJobResult(
            JOB_ID, LANDING_ZONE_ID, jobStatus, BILLING_PROFILE_ID);
    when(mockLandingZoneService().getAsyncDeletionJobResult(any(), any(), any()))
        .thenReturn(asyncJobResult);
    when(mockFeatureConfiguration().isAzureEnabled()).thenReturn(true);

    mockMvc
        .perform(
            addAuth(
                get(
                        AZURE_LANDING_ZONE_PATH + "/{landingZoneId}/delete-result/{jobId}",
                        LANDING_ZONE_ID,
                        JOB_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .characterEncoding("utf-8"),
                USER_REQUEST))
        .andExpect(status().is(expectedHttpStatus))
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport.id").exists())
        .andExpect(landingZoneMatcher)
        .andExpect(resourcesMatcher);
  }

  private static Stream<Arguments> getDeleteAzureLandingZoneResultScenario() {
    return Stream.of(
        Arguments.of(
            JobReport.StatusEnum.SUCCEEDED,
            HttpStatus.SC_OK,
            MockMvcResultMatchers.jsonPath("$.landingZoneId").exists(),
            MockMvcResultMatchers.jsonPath("$.resources").exists()),
        Arguments.of(
            JobReport.StatusEnum.RUNNING,
            HttpStatus.SC_ACCEPTED,
            MockMvcResultMatchers.jsonPath("$.landingZoneId").doesNotExist(),
            MockMvcResultMatchers.jsonPath("$.resources").doesNotExist()));
  }

  @Test
  void listAzureLandingZoneResourcesSuccess() throws Exception {
    LandingZoneResourcesByPurpose groupedResources =
        AzureLandingZoneFixtures.createListLandingZoneResourcesByPurposeResult();
    when(mockLandingZoneService().listResourcesWithPurposes(any(), any()))
        .thenReturn(groupedResources);
    when(mockFeatureConfiguration().isAzureEnabled()).thenReturn(true);
    mockMvc
        .perform(
            addAuth(
                get(AZURE_LANDING_ZONE_PATH + "/{landingZoneId}/resources", LANDING_ZONE_ID),
                USER_REQUEST))
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
                        SubnetResourcePurpose.AKS_NODE_POOL_SUBNET.toString(),
                        SubnetResourcePurpose.POSTGRESQL_SUBNET.toString(),
                        ResourcePurpose.SHARED_RESOURCE.toString(),
                        ResourcePurpose.WLZ_RESOURCE.toString()))))
        .andExpect(MockMvcResultMatchers.jsonPath("$.resources[0].deployedResources").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.resources[0].deployedResources").isArray());
  }

  @Test
  void listAzureLandingZoneResourcesNoResourcesSuccess() throws Exception {
    LandingZoneResourcesByPurpose groupedResources =
        new LandingZoneResourcesByPurpose(Collections.emptyMap());

    when(mockLandingZoneService().listResourcesWithPurposes(any(), any()))
        .thenReturn(groupedResources);
    when(mockFeatureConfiguration().isAzureEnabled()).thenReturn(true);
    mockMvc
        .perform(
            addAuth(
                get(AZURE_LANDING_ZONE_PATH + "/{landingZoneId}/resources", LANDING_ZONE_ID),
                USER_REQUEST))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.id").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.resources").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.resources").isArray());
  }

  @Test
  void getAzureLandingZoneByLandingZoneIdSuccess() throws Exception {
    LandingZone landingZone =
        LandingZone.builder()
            .landingZoneId(LANDING_ZONE_ID)
            .billingProfileId(BILLING_PROFILE_ID)
            .definition("definition")
            .version("version")
            .createdDate(Instant.now().atOffset(ZoneOffset.UTC))
            .build();
    when(mockLandingZoneService().getLandingZone(any(), eq(LANDING_ZONE_ID)))
        .thenReturn(landingZone);
    when(mockFeatureConfiguration().isAzureEnabled()).thenReturn(true);
    mockMvc
        .perform(
            addAuth(
                get(AZURE_LANDING_ZONE_PATH + "/{landingZoneId}", LANDING_ZONE_ID), USER_REQUEST))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingZoneId").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.definition").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.version").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.billingProfileId").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.createdDate").exists());
  }

  @Test
  void listAzureLandingZoneByBillingProfileIdSuccess() throws Exception {
    LandingZone landingZone =
        LandingZone.builder()
            .landingZoneId(LANDING_ZONE_ID)
            .billingProfileId(BILLING_PROFILE_ID)
            .definition("definition")
            .version("version")
            .createdDate(Instant.now().atOffset(ZoneOffset.UTC))
            .build();
    when(mockLandingZoneService().getLandingZonesByBillingProfile(any(), eq(BILLING_PROFILE_ID)))
        .thenReturn(List.of(landingZone));
    when(mockFeatureConfiguration().isAzureEnabled()).thenReturn(true);
    mockMvc
        .perform(
            addAuth(
                get(
                    AZURE_LANDING_ZONE_PATH + "?billingProfileId={billingProfileId}",
                    BILLING_PROFILE_ID),
                USER_REQUEST))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones").isArray())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones[0].landingZoneId").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones[0].billingProfileId").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones[0].definition").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones[0].version").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones[0].createdDate").exists());
  }

  @Test
  void listAzureLandingZonesSuccess() throws Exception {
    LandingZone landingZone =
        LandingZone.builder()
            .landingZoneId(LANDING_ZONE_ID)
            .billingProfileId(BILLING_PROFILE_ID)
            .definition("definition")
            .version("version")
            .createdDate(Instant.now().atOffset(ZoneOffset.UTC))
            .build();
    when(mockLandingZoneService().listLandingZones(any())).thenReturn(List.of(landingZone));
    when(mockFeatureConfiguration().isAzureEnabled()).thenReturn(true);
    mockMvc
        .perform(addAuth(get(AZURE_LANDING_ZONE_PATH), USER_REQUEST))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones").isArray())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones[0].landingZoneId").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones[0].billingProfileId").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones[0].definition").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones[0].version").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.landingzones[0].createdDate").exists());
  }

  @Test
  void getAzureLandingZoneByLandingZoneIdUserNotAuthorizedFailed() throws Exception {
    doThrow(new ForbiddenException("User is not authorized to read Landing Zone"))
        .when(mockLandingZoneService())
        .getLandingZone(any(), eq(LANDING_ZONE_ID));

    when(mockFeatureConfiguration().isAzureEnabled()).thenReturn(true);
    mockMvc
        .perform(
            addAuth(
                get(AZURE_LANDING_ZONE_PATH + "/{landingZoneId}", LANDING_ZONE_ID), USER_REQUEST))
        .andExpect(status().isForbidden());
  }
}
