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
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.common.ClonedAzureResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
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

public class ControlledAzureResourceApiControllerAzureStorageContainerTest
    extends BaseAzureSpringBootUnitTest {
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
  void createAzureStorageContainer403OnAuthzFailure() throws Exception {
    ApiCreateControlledAzureStorageContainerRequestBody containerRequest =
        new ApiCreateControlledAzureStorageContainerRequestBody()
            .common(ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi());

    UUID workspaceId = UUID.randomUUID();
    when(mockWorkspaceService().validateMcWorkspaceAndAction(any(), any(UUID.class), anyString()))
        .thenThrow(new ForbiddenException("forbidden"));

    mockMvc
        .perform(
            MockMvcUtils.addAuth(
                post(String.format(
                        CREATE_CONTROLLED_AZURE_STORAGE_CONTAINER_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(objectMapper.writeValueAsString(containerRequest)),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_FORBIDDEN));
  }

  @Test
  public void createAzureStorageContainer400WithNoParameters() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    mockMvc
        .perform(
            addAuth(
                post(String.format(
                        CREATE_CONTROLLED_AZURE_STORAGE_CONTAINER_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(""),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void createAzureStorageContainer() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    setupMockLandingZoneRegion(Region.US_SOUTH_CENTRAL);

    ApiControlledResourceCommonFields commonFields =
        ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi();

    ApiAzureStorageContainerCreationParameters params =
        new ApiAzureStorageContainerCreationParameters().storageContainerName("container1");

    ApiCreateControlledAzureStorageContainerRequestBody storageContainerRequestBody =
        new ApiCreateControlledAzureStorageContainerRequestBody()
            .common(commonFields)
            .azureStorageContainer(params);

    ControlledAzureStorageContainerResource resource =
        controller.buildControlledAzureStorageContainerResource(
            storageContainerRequestBody.getAzureStorageContainer(),
            controller.toCommonFields(
                workspaceId,
                commonFields,
                Region.US_SOUTH_CENTRAL.name(),
                USER_REQUEST,
                WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER));

    when(getMockControlledResourceService()
            .createControlledResourceSync(eq(resource), any(), any(), any()))
        .thenReturn(resource);

    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(
                            CREATE_CONTROLLED_AZURE_STORAGE_CONTAINER_PATH_FORMAT, workspaceId))
                        .content(objectMapper.writeValueAsString(storageContainerRequestBody)),
                    USER_REQUEST)))
        .andExpect(status().is(HttpStatus.SC_OK));
  }

  @Test
  public void cloneAzureStorageContainer() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    UUID workspaceId2 = UUID.randomUUID();

    setupMockLandingZoneRegion(Region.US_SOUTH_CENTRAL);

    ApiControlledResourceCommonFields commonFields =
        ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi();

    ApiAzureStorageContainerCreationParameters params =
        new ApiAzureStorageContainerCreationParameters().storageContainerName("container1");

    ApiCreateControlledAzureStorageContainerRequestBody storageContainerRequestBody =
        new ApiCreateControlledAzureStorageContainerRequestBody()
            .common(commonFields)
            .azureStorageContainer(params);

    ControlledAzureStorageContainerResource resource =
        controller.buildControlledAzureStorageContainerResource(
            storageContainerRequestBody.getAzureStorageContainer(),
            controller.toCommonFields(
                workspaceId,
                commonFields,
                Region.US_SOUTH_CENTRAL.name(),
                USER_REQUEST,
                WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER));

    ApiCloneControlledAzureStorageContainerRequest cloneStorageContainerRequestBody =
        new ApiCloneControlledAzureStorageContainerRequest()
            .destinationWorkspaceId(workspaceId2)
            .cloningInstructions(ApiCloningInstructionsEnum.NOTHING)
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));

    ClonedAzureResource clonedResource =
        new ClonedAzureResource(
            CloningInstructions.COPY_NOTHING, workspaceId2, workspaceId, resource);

    when(getMockControlledResourceService()
            .createControlledResourceSync(eq(resource), any(), any(), any()))
        .thenReturn(resource);
    when(mockControlledResourceMetadataManager().validateCloneAction(any(), any(), any(), any()))
        .thenReturn(resource);
    when(getMockJobApiUtils().retrieveAsyncJobResult(any(), eq(ClonedAzureResource.class)))
        .thenReturn(
            new JobApiUtils.AsyncJobResult<ClonedAzureResource>()
                .result(clonedResource)
                .jobReport(new ApiJobReport().status(ApiJobReport.StatusEnum.SUCCEEDED)));

    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(
                            CLONE_CONTROLLED_AZURE_STORAGE_CONTAINER_PATH_FORMAT,
                            workspaceId,
                            workspaceId2))
                        .content(objectMapper.writeValueAsString(cloneStorageContainerRequestBody)),
                    USER_REQUEST)))
        .andExpect(status().is(HttpStatus.SC_OK))
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport").exists());
  }

  @Test
  public void deleteAzureStorageContainer() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    UUID resourceId = UUID.randomUUID();
    setupMockLandingZoneRegion(Region.US_SOUTH_CENTRAL);

    ApiDeleteControlledAzureResourceRequest deleteRequestBody =
        new ApiDeleteControlledAzureResourceRequest()
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));

    when(getMockJobApiUtils().retrieveAsyncJobResult(any(), eq(Void.class)))
        .thenReturn(
            new JobApiUtils.AsyncJobResult<Void>()
                .jobReport(new ApiJobReport().status(ApiJobReport.StatusEnum.SUCCEEDED)));

    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(
                            CONTROLLED_AZURE_STORAGE_CONTAINER_PATH_FORMAT,
                            workspaceId,
                            resourceId))
                        .content(objectMapper.writeValueAsString(deleteRequestBody)),
                    USER_REQUEST)))
        .andExpect(status().is(HttpStatus.SC_OK))
        .andExpect(MockMvcResultMatchers.jsonPath("$.jobReport").exists());
  }
}
