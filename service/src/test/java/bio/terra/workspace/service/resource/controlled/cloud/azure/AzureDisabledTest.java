package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static bio.terra.workspace.common.utils.MockMvcUtils.CREATE_AZURE_DISK_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.CREATE_AZURE_IP_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.CREATE_AZURE_NETWORK_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.CREATE_AZURE_VM_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.CREATE_CLOUD_CONTEXT_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.GET_CLOUD_CONTEXT_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiAzureContext;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
import bio.terra.workspace.generated.model.ApiCreateCloudContextRequest;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureDiskRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureIpRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureNetworkRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureVmRequestBody;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

// Test to make sure things properly do not work when Azure feature is not enabled
@AutoConfigureMockMvc
public class AzureDisabledTest extends BaseConnectedTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private FeatureConfiguration featureConfiguration;

  private boolean originalAzureEnabled;

  @BeforeEach
  public void setUp() {
    // explicitly disable Azure feature regardless of the configuration files
    originalAzureEnabled = featureConfiguration.isAzureEnabled();
    featureConfiguration.setAzureEnabled(false);
  }

  @AfterEach
  public void reset() {
    featureConfiguration.setAzureEnabled(originalAzureEnabled);
  }

  @Test
  public void azureDisabledTest() throws Exception {
    Workspace workspace =
        Workspace.builder()
            .workspaceId(UUID.randomUUID())
            .userFacingId("a" + UUID.randomUUID())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    UUID workspaceUuid =
        workspaceService.createWorkspace(workspace, null, userAccessUtils.defaultUserAuthRequest());

    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    String fakeJobId = "a pretend job ID";
    ApiCreateCloudContextRequest request =
        new ApiCreateCloudContextRequest()
            .cloudPlatform(ApiCloudPlatform.AZURE)
            .azureContext(
                new ApiAzureContext()
                    .resourceGroupId("fake")
                    .subscriptionId("also fake")
                    .tenantId("still fake"))
            .jobControl(new ApiJobControl().id(fakeJobId));
    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(CREATE_CLOUD_CONTEXT_PATH_FORMAT, workspaceUuid))
                        .content(objectMapper.writeValueAsString(request)),
                    userRequest)))
        .andExpect(status().is(HttpStatus.SC_NOT_IMPLEMENTED));
    // There should not be a job to create this context
    mockMvc
        .perform(
            (addAuth(
                get(String.format(GET_CLOUD_CONTEXT_PATH_FORMAT, workspaceUuid, fakeJobId)),
                userRequest)))
        .andExpect(status().is(HttpStatus.SC_NOT_FOUND));

    // We're re-using these common fields for multiple requests. Normally this would be a problem
    // because the resource ID would be duplicated, but we expect all "create resource" calls
    // to fail, so this is fine.
    final ApiControlledResourceCommonFields commonFields =
        ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi();

    // Create IP
    final ApiCreateControlledAzureIpRequestBody ipRequest =
        new ApiCreateControlledAzureIpRequestBody()
            .common(commonFields)
            .azureIp(ControlledResourceFixtures.getAzureIpCreationParameters());
    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(CREATE_AZURE_IP_PATH_FORMAT, workspaceUuid))
                        .content(objectMapper.writeValueAsString(ipRequest)),
                    userRequest)))
        .andExpect(status().is(HttpStatus.SC_NOT_IMPLEMENTED));

    // Create disk
    final ApiCreateControlledAzureDiskRequestBody diskRequest =
        new ApiCreateControlledAzureDiskRequestBody()
            .common(commonFields)
            .azureDisk(ControlledResourceFixtures.getAzureDiskCreationParameters());
    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(CREATE_AZURE_DISK_PATH_FORMAT, workspaceUuid))
                        .content(objectMapper.writeValueAsString(diskRequest)),
                    userRequest)))
        .andExpect(status().is(HttpStatus.SC_NOT_IMPLEMENTED));

    // Create network
    final ApiCreateControlledAzureNetworkRequestBody networkRequest =
        new ApiCreateControlledAzureNetworkRequestBody()
            .common(commonFields)
            .azureNetwork(ControlledResourceFixtures.getAzureNetworkCreationParameters());
    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(CREATE_AZURE_NETWORK_PATH_FORMAT, workspaceUuid))
                        .content(objectMapper.writeValueAsString(networkRequest)),
                    userRequest)))
        .andExpect(status().is(HttpStatus.SC_NOT_IMPLEMENTED));

    // Create VM
    final ApiCreateControlledAzureVmRequestBody vmRequest =
        new ApiCreateControlledAzureVmRequestBody()
            .common(commonFields)
            .azureVm(ControlledResourceFixtures.getAzureVmCreationParameters());
    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(CREATE_AZURE_VM_PATH_FORMAT, workspaceUuid))
                        .content(objectMapper.writeValueAsString(vmRequest)),
                    userRequest)))
        .andExpect(status().is(HttpStatus.SC_NOT_IMPLEMENTED));
  }
}
