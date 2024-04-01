package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.mocks.MockAzureApi.CREATE_CONTROLLED_AZURE_VM_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.mocks.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.mocks.MockMvcUtils.addJsonContentType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.BaseAzureSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.mocks.MockMvcUtils;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureVmRequestBody;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
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

public class ControlledAzureResourceApiControllerAzureVmTest extends BaseAzureSpringBootUnitTest {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired ControlledAzureResourceApiController controller;

  @BeforeEach
  void setUp() {
    when(mockSamService()
            .getUserEmailFromSamAndRethrowOnInterrupt(any(AuthenticatedUserRequest.class)))
        .thenReturn(DEFAULT_USER_EMAIL);
    when(mockSamService().getWsmServiceAccountToken()).thenReturn("fake");
  }

  @Test
  void createAzureVm403OnAuthzFailure() throws Exception {
    ApiCreateControlledAzureVmRequestBody vmRequest =
        new ApiCreateControlledAzureVmRequestBody()
            .common(ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi())
            .azureVm(ControlledAzureResourceFixtures.getAzureVmCreationParameters())
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));

    UUID workspaceId = UUID.randomUUID();
    when(mockWorkspaceService().validateMcWorkspaceAndAction(any(), any(UUID.class), anyString()))
        .thenThrow(new ForbiddenException("forbidden"));

    mockMvc
        .perform(
            MockMvcUtils.addAuth(
                post(String.format(CREATE_CONTROLLED_AZURE_VM_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(objectMapper.writeValueAsString(vmRequest)),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_FORBIDDEN));
  }

  @Test
  public void createAzureVm400WithNoParameters() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    mockMvc
        .perform(
            addAuth(
                post(String.format(CREATE_CONTROLLED_AZURE_VM_PATH_FORMAT, workspaceId))
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
    setupMockLandingZoneRegion(Region.US_SOUTH_CENTRAL);

    ApiControlledResourceCommonFields commonFields =
        ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi();

    ApiAzureVmCreationParameters creationParameters =
        ControlledAzureResourceFixtures.getAzureVmCreationParameters().diskId(null);
    ApiCreateControlledAzureVmRequestBody vmRequest =
        new ApiCreateControlledAzureVmRequestBody()
            .common(commonFields)
            .azureVm(creationParameters)
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));

    ControlledAzureVmResource resource =
        controller.buildControlledAzureVmResource(
            creationParameters,
            controller.toCommonFields(
                workspaceId,
                commonFields,
                Region.US_SOUTH_CENTRAL.name(),
                USER_REQUEST,
                WsmResourceType.CONTROLLED_AZURE_VM));

    when(getMockJobApiUtils().retrieveAsyncJobResult(any(), eq(ControlledAzureVmResource.class)))
        .thenReturn(
            new JobApiUtils.AsyncJobResult<ControlledAzureVmResource>()
                .result(resource)
                .jobReport(new ApiJobReport().status(ApiJobReport.StatusEnum.SUCCEEDED)));
    setupMockLandingZoneRegion(Region.GERMANY_CENTRAL);

    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(CREATE_CONTROLLED_AZURE_VM_PATH_FORMAT, workspaceId))
                        .content(objectMapper.writeValueAsString(vmRequest)),
                    USER_REQUEST)))
        .andExpect(status().is(HttpStatus.SC_OK));
  }
}
