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
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.*;
import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import com.azure.core.management.Region;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

public class ControlledAzureResourceApiControllerAzureDiskTest extends BaseAzureUnitTest {
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

    when(getMockJobApiUtils().retrieveAsyncJobResult(any(), eq(ControlledAzureDiskResource.class)))
        .thenReturn(
            new JobApiUtils.AsyncJobResult<ControlledAzureDiskResource>()
                .jobReport(new ApiJobReport().status(ApiJobReport.StatusEnum.SUCCEEDED)));

    setupMockLandingZoneRegion(Region.GERMANY_CENTRAL);

    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(CREATE_CONTROLLED_AZURE_DISK_V2_PATH_FORMAT, workspaceId))
                        .content(objectMapper.writeValueAsString(diskRequestV2Body)),
                    USER_REQUEST)))
        .andExpect(status().is(HttpStatus.SC_OK));
  }
}
