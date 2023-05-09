package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.utils.MockMvcUtils.CREATE_AZURE_BATCH_POOL_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceBatchPoolFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolUserAssignedIdentity;
import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureBatchPoolRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool.ControlledAzureBatchPoolResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.azure.core.management.Region;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

public class ControlledAzureResourceApiControllerBatchPoolTest extends BaseAzureUnitTest {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired ControlledAzureResourceApiController controller;

  @BeforeEach
  void setUp() throws InterruptedException {
    when(mockSamService()
            .getUserEmailFromSamAndRethrowOnInterrupt(any(AuthenticatedUserRequest.class)))
        .thenReturn(DEFAULT_USER_EMAIL);
  }

  @Test
  public void createBatchPool400WithNoParameters() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    mockMvc
        .perform(
            addAuth(
                post(String.format(CREATE_AZURE_BATCH_POOL_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content("{}"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void createBatchPool400WithInconsistentUAMI() throws Exception {
    final ApiControlledResourceCommonFields commonFields =
        ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi();

    var creationParameters =
        ControlledResourceBatchPoolFixtures.createBatchPoolWithRequiredParameters();
    // name and clientId are mutually exclusive
    creationParameters.userAssignedIdentities(
        List.of(
            new ApiAzureBatchPoolUserAssignedIdentity().clientId(UUID.randomUUID()).name("name")));

    final ApiCreateControlledAzureBatchPoolRequestBody batchPoolRequest =
        new ApiCreateControlledAzureBatchPoolRequestBody()
            .common(commonFields)
            .azureBatchPool(creationParameters);

    UUID workspaceId = UUID.randomUUID();
    ControlledAzureBatchPoolResource resource =
        ControlledResourceBatchPoolFixtures.createAzureBatchPoolResource(
            creationParameters,
            controller.toCommonFields(
                workspaceId,
                commonFields,
                null,
                USER_REQUEST,
                WsmResourceType.CONTROLLED_AZURE_BATCH_POOL));

    when(getMockControlledResourceService()
            .createControlledResourceSync(eq(resource), any(), any(), any()))
        .thenReturn(resource);
    setupMockLandingZoneRegion(Region.GERMANY_CENTRAL);

    mockMvc
        .perform(
            addAuth(
                post(String.format(CREATE_AZURE_BATCH_POOL_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(objectMapper.writeValueAsString(batchPoolRequest)),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void createBatchPoolWithRequiredParametersSuccess() throws Exception {
    final ApiControlledResourceCommonFields commonFields =
        ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi();

    var creationParameters =
        ControlledResourceBatchPoolFixtures.createBatchPoolWithRequiredParameters();

    final ApiCreateControlledAzureBatchPoolRequestBody batchPoolRequest =
        new ApiCreateControlledAzureBatchPoolRequestBody()
            .common(commonFields)
            .azureBatchPool(creationParameters);

    setupMockLandingZoneRegion(Region.GERMANY_CENTRAL);
    UUID workspaceId = UUID.randomUUID();
    ControlledAzureBatchPoolResource resource =
        ControlledResourceBatchPoolFixtures.createAzureBatchPoolResource(
            creationParameters,
            controller.toCommonFields(
                workspaceId,
                commonFields,
                null,
                USER_REQUEST,
                WsmResourceType.CONTROLLED_AZURE_BATCH_POOL));

    when(getMockControlledResourceService()
            .createControlledResourceSync(
                any(ControlledAzureBatchPoolResource.class), any(), any(), any()))
        .thenReturn(resource);

    mockMvc
        .perform(
            addAuth(
                post(String.format(CREATE_AZURE_BATCH_POOL_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(objectMapper.writeValueAsString(batchPoolRequest)),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_OK));
  }
}
