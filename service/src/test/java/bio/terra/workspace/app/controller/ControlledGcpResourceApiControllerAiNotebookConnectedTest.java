package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.mocks.MockMvcUtils.assertControlledResourceMetadata;
import static bio.terra.workspace.common.mocks.MockMvcUtils.assertResourceMetadata;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.mocks.MockGcpApi;
import bio.terra.workspace.common.mocks.MockMvcUtils;
import bio.terra.workspace.common.mocks.MockWorkspaceV1Api;
import bio.terra.workspace.common.mocks.MockWorkspaceV2Api;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceResource;
import bio.terra.workspace.generated.model.ApiJobReport.StatusEnum;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.generated.model.ApiPrivateResourceState;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiResourceLineage;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Connected tests for controlled AI notebooks. */
// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.

@Tag("connectedPlus")
@TestInstance(Lifecycle.PER_CLASS)
public class ControlledGcpResourceApiControllerAiNotebookConnectedTest extends BaseConnectedTest {
  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired MockWorkspaceV1Api mockWorkspaceV1Api;
  @Autowired MockWorkspaceV2Api mockWorkspaceV2Api;
  @Autowired MockGcpApi mockGcpApi;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired JobService jobService;

  private UUID workspaceId;

  @BeforeAll
  public void setup() throws Exception {
    workspaceId =
        mockWorkspaceV1Api
            .createWorkspaceWithCloudContext(
                userAccessUtils.defaultUserAuthRequest(), apiCloudPlatform)
            .getId();
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    StairwayTestUtils.enumerateJobsDump(
        jobService, workspaceId, userAccessUtils.defaultUserAuthRequest());
    jobService.setFlightDebugInfoForTest(null);
  }

  @AfterAll
  public void cleanup() throws Exception {
    mockWorkspaceV2Api.deleteWorkspaceAndWait(
        userAccessUtils.defaultUserAuthRequest(), workspaceId);
  }

  @Test
  public void createAiNotebookInstance_correctZone() throws Exception {
    mockWorkspaceV1Api.updateWorkspaceProperties(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        List.of(
            new ApiProperty()
                .key(WorkspaceConstants.Properties.DEFAULT_RESOURCE_LOCATION)
                .value("asia-east1")));

    ApiGcpAiNotebookInstanceResource notebook =
        mockGcpApi
            .createAiNotebookInstance(userAccessUtils.defaultUserAuthRequest(), workspaceId, null)
            .getAiNotebookInstance();

    assertEquals("asia-east1-a", notebook.getAttributes().getLocation());
    assertAiNotebook(
        notebook,
        workspaceId,
        "asia-east1-a",
        "asia-east1",
        userAccessUtils.getDefaultUserEmail(),
        userAccessUtils.getDefaultUserEmail());

    notebook =
        mockGcpApi
            .createAiNotebookInstance(
                userAccessUtils.defaultUserAuthRequest(), workspaceId, "europe-west1-b")
            .getAiNotebookInstance();

    assertAiNotebook(
        notebook,
        workspaceId,
        "europe-west1-b",
        "europe-west1",
        userAccessUtils.getDefaultUserEmail(),
        userAccessUtils.getDefaultUserEmail());

    mockWorkspaceV1Api.deleteWorkspaceProperties(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        List.of(WorkspaceConstants.Properties.DEFAULT_RESOURCE_LOCATION));
  }

  @Test
  public void createAiNotebookInstance_duplicateInstanceId() throws Exception {
    String duplicateName = "not-unique-name";
    ApiGcpAiNotebookInstanceResource unused =
        mockGcpApi
            .createAiNotebookInstanceAndWait(
                userAccessUtils.defaultUserAuthRequest(), workspaceId, duplicateName, null)
            .getAiNotebookInstance();

    ApiErrorReport errorReport =
        mockGcpApi
            .createAiNotebookInstanceAndExpect(
                userAccessUtils.defaultUserAuthRequest(),
                workspaceId,
                duplicateName,
                null,
                StatusEnum.FAILED)
            .getErrorReport();

    assertEquals("A resource with matching attributes already exists", errorReport.getMessage());
  }

  private void assertAiNotebook(
      ApiGcpAiNotebookInstanceResource actualResource,
      UUID expectedWorkspaceId,
      String expectedLocation,
      String expectedRegion,
      String expectedCreatedBy,
      String expectedLastUpdatedBy) {
    assertResourceMetadata(
        actualResource.getMetadata(),
        ApiCloudPlatform.GCP,
        ApiResourceType.AI_NOTEBOOK,
        ApiStewardshipType.CONTROLLED,
        actualResource.getMetadata().getCloningInstructions(),
        expectedWorkspaceId,
        actualResource.getMetadata().getName(),
        actualResource.getMetadata().getDescription(),
        /* expectedResourceLineage= */ new ApiResourceLineage(),
        expectedCreatedBy,
        expectedLastUpdatedBy);

    assertEquals(expectedLocation, actualResource.getAttributes().getLocation());

    assertControlledResourceMetadata(
        actualResource.getMetadata().getControlledResourceMetadata(),
        ApiAccessScope.PRIVATE_ACCESS,
        ApiManagedBy.USER,
        new ApiPrivateResourceUser().userName(expectedCreatedBy),
        ApiPrivateResourceState.ACTIVE,
        expectedRegion);
  }
}
