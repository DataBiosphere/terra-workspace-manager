package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.mocks.MockAzureApi.*;
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
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.mocks.MockMvcUtils;
import bio.terra.workspace.generated.model.*;
import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
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

public class ControlledAzureResourceApiControllerAzureDiskTest extends BaseAzureSpringBootUnitTest {
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
  void createAzureDisk403OnAuthzFailure() throws Exception {
    ApiCreateControlledAzureDiskRequestV2Body diskRequestV2Body =
        new ApiCreateControlledAzureDiskRequestV2Body()
            .common(ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi())
            .azureDisk(new ApiAzureDiskCreationParameters().name("disk1").size(100))
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));

    UUID workspaceId = UUID.randomUUID();
    when(mockWorkspaceService().validateMcWorkspaceAndAction(any(), any(UUID.class), anyString()))
        .thenThrow(new ForbiddenException("forbidden"));

    mockMvc
        .perform(
            MockMvcUtils.addAuth(
                post(String.format(CREATE_CONTROLLED_AZURE_DISK_V2_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(objectMapper.writeValueAsString(diskRequestV2Body)),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_FORBIDDEN));
  }

  @Test
  public void createAzureDisk400WithNoParameters() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    mockMvc
        .perform(
            addAuth(
                post(String.format(CREATE_CONTROLLED_AZURE_DISK_V2_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content("{}"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void createDisk() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    setupMockLandingZoneRegion(Region.US_SOUTH_CENTRAL);

    ApiControlledResourceCommonFields commonFields =
        ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi();

    ApiAzureDiskCreationParameters params =
        new ApiAzureDiskCreationParameters().name("disk1").size(100);

    ApiCreateControlledAzureDiskRequestV2Body diskRequestV2Body =
        new ApiCreateControlledAzureDiskRequestV2Body()
            .common(commonFields)
            .azureDisk(params)
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));

    ControlledAzureDiskResource resource =
        controller.buildControlledAzureDiskResource(
            diskRequestV2Body.getAzureDisk(),
            controller.toCommonFields(
                workspaceId,
                commonFields,
                Region.US_SOUTH_CENTRAL.name(),
                USER_REQUEST,
                WsmResourceType.CONTROLLED_AZURE_DISK));

    when(getMockJobApiUtils().retrieveAsyncJobResult(any(), eq(ControlledAzureDiskResource.class)))
        .thenReturn(
            new JobApiUtils.AsyncJobResult<ControlledAzureDiskResource>()
                .result(resource)
                .jobReport(new ApiJobReport().status(ApiJobReport.StatusEnum.SUCCEEDED)));

    setupMockLandingZoneRegion(Region.GERMANY_CENTRAL);

    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(CREATE_CONTROLLED_AZURE_DISK_V2_PATH_FORMAT, workspaceId))
                        .content(objectMapper.writeValueAsString(diskRequestV2Body)),
                    USER_REQUEST)))
        .andExpect(status().is(HttpStatus.SC_OK))
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport").exists());
  }
}
