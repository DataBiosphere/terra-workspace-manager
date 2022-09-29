package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.utils.MockMvcUtils.CREATE_AZURE_SAS_TOKEN_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.CREATE_AZURE_VM_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureVmRequestBody;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureStorageAccessService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

public class ControlledAzureResourceApiControllerTest extends BaseAzureUnitTest {
  AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest(
          "fake@email.com", "subjectId123456", Optional.of("ThisIsNotARealBearerToken"));

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @Autowired ControlledAzureResourceApiController controller;

  @MockBean ControlledResourceService controlledResourceServiceMock;
  @MockBean WorkspaceService workspaceServiceMock;
  @MockBean JobService jobServiceMock;

  @Test
  public void createAzureVm400WithNoParameters() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    mockMvc
        .perform(
            addAuth(
                post(String.format(CREATE_AZURE_VM_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content("{}"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void createAzureVmWithoutDisk() throws Exception {
    UUID workspaceId = UUID.randomUUID();

    final ApiControlledResourceCommonFields commonFields =
        ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi();

    var creationParameters = ControlledResourceFixtures.getAzureVmCreationParameters().diskId(null);
    final ApiCreateControlledAzureVmRequestBody vmRequest =
        new ApiCreateControlledAzureVmRequestBody()
            .common(commonFields)
            .azureVm(creationParameters)
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));

    ControlledAzureVmResource resource =
        controller.buildControlledAzureVmResource(
            creationParameters, controller.toCommonFields(workspaceId, commonFields, USER_REQUEST));

    when(jobServiceMock.retrieveAsyncJobResult(any(), eq(ControlledAzureVmResource.class)))
        .thenReturn(
            new JobService.AsyncJobResult<ControlledAzureVmResource>()
                .result(resource)
                .jobReport(new ApiJobReport().status(ApiJobReport.StatusEnum.SUCCEEDED)));

    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(CREATE_AZURE_VM_PATH_FORMAT, workspaceId))
                        .content(objectMapper.writeValueAsString(vmRequest)),
                    USER_REQUEST)))
        .andExpect(status().is(HttpStatus.SC_OK));
  }
}

class CreateAzureStorageContainerSasTokenTest extends BaseAzureUnitTest {
  AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest(
          "fake@email.com", "subjectId123456", Optional.of("ThisIsNotARealBearerToken"));

  @Autowired MockMvc mockMvc;
  @Autowired AzureConfiguration azureConfiguration;
  @MockBean ControlledResourceMetadataManager controlledResourceMetadataManager;
  @MockBean SamService samService;
  @MockBean AzureStorageAccessService azureStorageAccessService;

  private UUID workspaceId;
  private UUID storageContainerId;

  private ControlledAzureStorageResource accountResource;
  private ControlledAzureStorageContainerResource containerResource;

  @BeforeEach
  public void setup() throws Exception {
    workspaceId = UUID.randomUUID();
    storageContainerId = UUID.randomUUID();
    UUID storageAccountId = UUID.randomUUID();

    containerResource =
        ControlledAzureStorageContainerResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(workspaceId)
                    .resourceId(storageContainerId)
                    .build())
            .storageAccountId(storageAccountId)
            .storageContainerName("testcontainer")
            .build();

    when(controlledResourceMetadataManager.validateControlledResourceAndAction(
            any(), eq(workspaceId), eq(storageContainerId), any()))
        .thenReturn(containerResource);

    accountResource =
        ControlledAzureStorageResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(workspaceId)
                    .resourceId(storageAccountId)
                    .build())
            .storageAccountName("testaccount")
            .region("eastus")
            .build();

    when(controlledResourceMetadataManager.validateControlledResourceAndAction(
            any(), eq(workspaceId), eq(storageAccountId), any()))
        .thenReturn(accountResource);

    when(samService.getUserEmailFromSam(eq(USER_REQUEST))).thenReturn(USER_REQUEST.getEmail());

    when(azureStorageAccessService.createAzureStorageContainerSasToken(
            eq(workspaceId),
            eq(containerResource),
            eq(accountResource),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(new AzureStorageAccessService.AzureSasBundle("sasToken", "sasUrl"));
  }

  @Test
  void createSASToken400BadDuration() throws Exception {
    azureConfiguration.setSasTokenExpiryTimeMaximumMinutesOffset(
        240L); // maximum of 240 minutes (4 hours)

    // expiration duration must be positive
    mockMvc
        .perform(
            addAuth(
                post(String.format(
                        CREATE_AZURE_SAS_TOKEN_PATH_FORMAT, workspaceId, storageContainerId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .queryParam("sasExpirationDuration", "-1"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));

    // expiration can't exceed 4 hours = 14400 seconds
    mockMvc
        .perform(
            addAuth(
                post(String.format(
                        CREATE_AZURE_SAS_TOKEN_PATH_FORMAT, workspaceId, storageContainerId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .queryParam("sasExpirationDuration", "14401"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  void createSASTokenCustomExpirationSuccess() throws Exception {
    azureConfiguration.setSasTokenStartTimeMinutesOffset(5L); // 5 minutes before
    azureConfiguration.setSasTokenExpiryTimeMinutesOffset(10L); // 10 minutes after
    azureConfiguration.setSasTokenExpiryTimeMaximumMinutesOffset(
        240L); // maximum of 240 minutes (4 hours)

    // Specify custom expiration duration of 2 hours.
    mockMvc
        .perform(
            addAuth(
                post(String.format(
                        CREATE_AZURE_SAS_TOKEN_PATH_FORMAT, workspaceId, storageContainerId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .queryParam("sasExpirationDuration", "7200"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_OK));

    // Use expiration time as set in configuration.
    mockMvc
        .perform(
            addAuth(
                post(String.format(
                        CREATE_AZURE_SAS_TOKEN_PATH_FORMAT, workspaceId, storageContainerId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_OK));

    ArgumentCaptor<OffsetDateTime> startTimeCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    ArgumentCaptor<OffsetDateTime> endTimeCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);

    Mockito.verify(azureStorageAccessService, times(2))
        .createAzureStorageContainerSasToken(
            eq(workspaceId),
            eq(containerResource),
            eq(accountResource),
            startTimeCaptor.capture(),
            endTimeCaptor.capture(),
            any(),
            any());

    // First call uses custom time of 2 hours (plus 5 minutes before) = 7500 seconds.
    assertEquals(
        7500,
        endTimeCaptor.getAllValues().get(0).toEpochSecond()
            - startTimeCaptor.getAllValues().get(0).toEpochSecond());

    // Second call based on configuration, difference should be 15 minutes = 900 seconds.
    assertEquals(
        900,
        endTimeCaptor.getAllValues().get(1).toEpochSecond()
            - startTimeCaptor.getAllValues().get(1).toEpochSecond());
  }

  @Test
  void createSASToken400BadIPRange() throws Exception {
    mockMvc
        .perform(
            addAuth(
                post(String.format(
                        CREATE_AZURE_SAS_TOKEN_PATH_FORMAT, workspaceId, storageContainerId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .queryParam("sasIpRange", "not_an_IP"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  void createSASTokenIpRangeSuccess() throws Exception {
    String ipRange = "168.1.5.60-168.1.5.70";
    mockMvc
        .perform(
            addAuth(
                post(String.format(
                        CREATE_AZURE_SAS_TOKEN_PATH_FORMAT, workspaceId, storageContainerId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .queryParam("sasIpRange", ipRange),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_OK));

    Mockito.verify(azureStorageAccessService)
        .createAzureStorageContainerSasToken(
            eq(workspaceId),
            eq(containerResource),
            eq(accountResource),
            any(),
            any(),
            any(),
            eq(ipRange));
  }
}
