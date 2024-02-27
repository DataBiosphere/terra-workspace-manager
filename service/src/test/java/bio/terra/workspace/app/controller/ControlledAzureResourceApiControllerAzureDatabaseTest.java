package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.mocks.MockAzureApi.*;
import static bio.terra.workspace.common.mocks.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.mocks.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.mocks.MockMvcUtils.addJsonContentType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.BaseAzureSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.*;
import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.azure.core.management.Region;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

public class ControlledAzureResourceApiControllerAzureDatabaseTest
    extends BaseAzureSpringBootUnitTest {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired ControlledAzureResourceApiController controller;

  @BeforeEach
  void setUp() {
    when(mockSamService()
            .getUserEmailFromSamAndRethrowOnInterrupt(any(AuthenticatedUserRequest.class)))
        .thenReturn(DEFAULT_USER_EMAIL);
  }

  @Test
  public void createAzureDatabaseWithNoParameters() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    mockMvc
        .perform(
            addAuth(
                post(String.format(CREATE_CONTROLLED_AZURE_DATABASE_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content("{}"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void createDatabase() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    setupMockLandingZoneRegion(Region.US_SOUTH_CENTRAL);

    ApiControlledResourceCommonFields commonFields =
        ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi();

    ApiAzureDatabaseCreationParameters params =
        new ApiAzureDatabaseCreationParameters()
            .name("database1")
            .owner("owner")
            .allowAccessForAllWorkspaceUsers(true);

    ApiCreateControlledAzureDatabaseRequestBody databaseRequestBody =
        new ApiCreateControlledAzureDatabaseRequestBody()
            .common(commonFields)
            .azureDatabase(params)
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));

    ControlledAzureDatabaseResource resource =
        controller.buildControlledAzureDatabaseResource(
            databaseRequestBody.getAzureDatabase(),
            controller.toCommonFields(
                workspaceId,
                commonFields,
                Region.US_SOUTH_CENTRAL.name(),
                USER_REQUEST,
                WsmResourceType.CONTROLLED_AZURE_VM));

    when(getMockJobApiUtils()
            .retrieveAsyncJobResult(any(), eq(ControlledAzureDatabaseResource.class)))
        .thenReturn(
            new JobApiUtils.AsyncJobResult<ControlledAzureDatabaseResource>()
                .result(resource)
                .jobReport(new ApiJobReport().status(ApiJobReport.StatusEnum.SUCCEEDED)));

    setupMockLandingZoneRegion(Region.GERMANY_CENTRAL);

    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(CREATE_CONTROLLED_AZURE_DATABASE_PATH_FORMAT, workspaceId))
                        .content(objectMapper.writeValueAsString(databaseRequestBody)),
                    USER_REQUEST)))
        .andExpect(status().is(HttpStatus.SC_OK))
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport").exists());
  }

  @Test
  public void deleteDatabase() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    UUID resourceId = UUID.randomUUID();
    setupMockLandingZoneRegion(Region.US_SOUTH_CENTRAL);

    ApiDeleteControlledAzureResourceRequest deleteRequestBody =
        new ApiDeleteControlledAzureResourceRequest()
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));

    when(getMockJobApiUtils()
            .retrieveAsyncJobResult(any(), eq(ApiDeleteControlledAzureResourceResult.class)))
        .thenReturn(
            new JobApiUtils.AsyncJobResult<ApiDeleteControlledAzureResourceResult>()
                .jobReport(new ApiJobReport().status(ApiJobReport.StatusEnum.SUCCEEDED)));

    setupMockLandingZoneRegion(Region.GERMANY_CENTRAL);

    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(
                            CONTROLLED_AZURE_DATABASE_PATH_FORMAT, workspaceId, resourceId))
                        .content(objectMapper.writeValueAsString(deleteRequestBody)),
                    USER_REQUEST)))
        .andExpect(status().is(HttpStatus.SC_OK))
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport").exists());
  }
}
