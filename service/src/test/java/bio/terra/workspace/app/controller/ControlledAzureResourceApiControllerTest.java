package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.utils.MockMvcUtils.CREATE_AZURE_VM_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureVmRequestBody;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

public class ControlledAzureResourceApiControllerTest extends BaseAzureUnitTest {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired ControlledAzureResourceApiController controller;

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

    when(mockJobService().retrieveAsyncJobResult(any(), eq(ControlledAzureVmResource.class)))
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
