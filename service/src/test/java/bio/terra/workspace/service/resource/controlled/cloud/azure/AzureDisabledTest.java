package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static bio.terra.workspace.common.mocks.MockAzureApi.CREATE_CONTROLLED_AZURE_DISK_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockAzureApi.CREATE_CONTROLLED_AZURE_VM_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockWorkspaceV1Api.CLOUD_CONTEXTS_V1_CREATE;
import static bio.terra.workspace.common.mocks.MockWorkspaceV1Api.CLOUD_CONTEXTS_V1_CREATE_RESULT;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
import bio.terra.workspace.generated.model.ApiCreateCloudContextRequest;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureDiskRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureVmRequestBody;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

// Test to make sure things properly do not work when Azure feature is not enabled
// We are modifying application context here. Need to clean up once tests are done.
@Disabled("Until we get the postgres connection leaks addressed")
@Tag("connected")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class AzureDisabledTest extends BaseConnectedTest {
  @Autowired private MockMvc mockMvc;

  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private FeatureConfiguration featureConfiguration;
  @Autowired private WorkspaceConnectedTestUtils connectedTestUtils;

  @BeforeEach
  public void setUp() {
    // explicitly disable Azure feature regardless of the configuration files
    featureConfiguration.setAzureEnabled(false);
  }

  @Test
  public void azureDisabledTest() throws Exception {
    Workspace workspace =
        connectedTestUtils.createWorkspace(userAccessUtils.defaultUserAuthRequest());
    UUID workspaceUuid = workspace.getWorkspaceId();

    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    String fakeJobId = "a pretend job ID";
    ApiCreateCloudContextRequest request =
        new ApiCreateCloudContextRequest()
            .cloudPlatform(ApiCloudPlatform.AZURE)
            .jobControl(new ApiJobControl().id(fakeJobId));
    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(CLOUD_CONTEXTS_V1_CREATE, workspaceUuid))
                        .content(objectMapper.writeValueAsString(request)),
                    userRequest)))
        .andExpect(status().is(HttpStatus.SC_NOT_IMPLEMENTED));
    // There should not be a job to create this context
    mockMvc
        .perform(
            (addAuth(
                get(String.format(CLOUD_CONTEXTS_V1_CREATE_RESULT, workspaceUuid, fakeJobId)),
                userRequest)))
        .andExpect(status().is(HttpStatus.SC_NOT_FOUND));

    // We're re-using these common fields for multiple requests. Normally this would be a problem
    // because the resource ID would be duplicated, but we expect all "create resource" calls
    // to fail, so this is fine.
    ApiControlledResourceCommonFields commonFields =
        ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi();

    // Create disk
    ApiCreateControlledAzureDiskRequestBody diskRequest =
        new ApiCreateControlledAzureDiskRequestBody()
            .common(commonFields)
            .azureDisk(ControlledAzureResourceFixtures.getAzureDiskCreationParameters());
    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(CREATE_CONTROLLED_AZURE_DISK_PATH_FORMAT, workspaceUuid))
                        .content(objectMapper.writeValueAsString(diskRequest)),
                    userRequest)))
        .andExpect(status().is(HttpStatus.SC_NOT_IMPLEMENTED));

    // Create VM
    ApiCreateControlledAzureVmRequestBody vmRequest =
        new ApiCreateControlledAzureVmRequestBody()
            .common(commonFields)
            .azureVm(ControlledAzureResourceFixtures.getAzureVmCreationParameters());
    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(CREATE_CONTROLLED_AZURE_VM_PATH_FORMAT, workspaceUuid))
                        .content(objectMapper.writeValueAsString(vmRequest)),
                    userRequest)))
        .andExpect(status().is(HttpStatus.SC_NOT_IMPLEMENTED));
  }
}
