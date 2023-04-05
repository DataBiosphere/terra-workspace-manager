package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.DEFAULT_AI_NOTEBOOK_MACHINE_TYPE_ALLOWING_ACCELERATOR_CONFIG;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.DEFAULT_CREATED_AI_NOTEBOOK_MACHINE_TYPE;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertControlledResourceMetadata;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertResourceMetadata;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.RetryUtils;
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
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.AcceleratorConfig;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants;
import com.google.cloud.notebooks.v1.Instance;
import java.util.List;
import java.util.UUID;
import org.apache.http.HttpStatus;
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

@Tag("connected")
@TestInstance(Lifecycle.PER_CLASS)
public class ControlledGcpResourceApiControllerAiNotebookTest extends BaseConnectedTest {
  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired JobService jobService;
  @Autowired CrlService crlService;

  private UUID workspaceId;

  @BeforeAll
  public void setup() throws Exception {
    workspaceId =
        mockMvcUtils
            .createWorkspaceWithCloudContext(userAccessUtils.defaultUserAuthRequest())
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
    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId);
  }

  @Test
  public void createAiNotebookInstance_correctZone() throws Exception {
    mockMvcUtils.updateWorkspaceProperties(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        List.of(
            new ApiProperty()
                .key(WorkspaceConstants.Properties.DEFAULT_RESOURCE_LOCATION)
                .value("asia-east1")));

    ApiGcpAiNotebookInstanceResource notebook =
        mockMvcUtils
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
        mockMvcUtils
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

    mockMvcUtils.deleteWorkspaceProperties(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        List.of(WorkspaceConstants.Properties.DEFAULT_RESOURCE_LOCATION));
  }

  @Test
  public void createAiNotebookInstance_populates_machineType_inGetResponse() throws Exception {
    ApiGcpAiNotebookInstanceResource notebook =
        mockMvcUtils
            .createAiNotebookInstance(userAccessUtils.defaultUserAuthRequest(), workspaceId, null)
            .getAiNotebookInstance();
    assertEquals(
        DEFAULT_CREATED_AI_NOTEBOOK_MACHINE_TYPE, notebook.getAttributes().getMachineType());
  }

  @Test
  public void updateAiNotebookInstance_machineTypeAndAcceleratorConfig() throws Exception {
    ApiGcpAiNotebookInstanceResource notebook =
        mockMvcUtils
            .createAiNotebookInstance(userAccessUtils.defaultUserAuthRequest(), workspaceId, null)
            .getAiNotebookInstance();

    InstanceName instanceName =
        InstanceName.builder()
            .projectId(notebook.getAttributes().getProjectId())
            .location(notebook.getAttributes().getLocation())
            .instanceId(notebook.getAttributes().getInstanceId())
            .build();

    // Stop the notebook so the CPU and GPU can be updated.
    crlService.getAIPlatformNotebooksCow().instances().stop(instanceName).execute();

    // Wait until the notebook is stopped (or stopping).
    RetryUtils.getWithRetry(
        instance ->
            instance
                    .getState()
                    .equals(com.google.cloud.notebooks.v1.Instance.State.STOPPED.toString())
                || instance.getState().equals(Instance.State.STOPPING.toString()),
        () -> crlService.getAIPlatformNotebooksCow().instances().get(instanceName).execute());

    var updatedNotebook =
        mockMvcUtils.updateAiNotebookInstance(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            notebook.getMetadata().getResourceId(),
            DEFAULT_AI_NOTEBOOK_MACHINE_TYPE_ALLOWING_ACCELERATOR_CONFIG,
            AcceleratorConfig.toApiAcceleratorConfig(DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG));

    assertEquals(
        DEFAULT_AI_NOTEBOOK_MACHINE_TYPE_ALLOWING_ACCELERATOR_CONFIG,
        updatedNotebook.getAttributes().getMachineType());
    assertEquals(
        DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG.type(),
        updatedNotebook.getAttributes().getAcceleratorConfig().getType());
    assertEquals(
        DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG.coreCount(),
        updatedNotebook.getAttributes().getAcceleratorConfig().getCoreCount());
  }

  @Test
  public void updateAiNotebookInstance_notStopped_throws() throws Exception {
    ApiGcpAiNotebookInstanceResource notebook =
        mockMvcUtils
            .createAiNotebookInstance(userAccessUtils.defaultUserAuthRequest(), workspaceId, null)
            .getAiNotebookInstance();

    // By default, the notebook will be running (i.e., not stopping or stopped).
    mockMvcUtils.updateAiNotebookInstanceAndExpect(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        notebook.getMetadata().getResourceId(),
        DEFAULT_AI_NOTEBOOK_MACHINE_TYPE_ALLOWING_ACCELERATOR_CONFIG,
        AcceleratorConfig.toApiAcceleratorConfig(DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG),
        HttpStatus.SC_CONFLICT);
  }

  @Test
  public void createAiNotebookInstance_duplicateInstanceId() throws Exception {
    var duplicateName = "not-unique-name";
    mockMvcUtils
        .createAiNotebookInstanceAndWait(
            userAccessUtils.defaultUserAuthRequest(), workspaceId, duplicateName, null)
        .getAiNotebookInstance();

    ApiErrorReport errorReport =
        mockMvcUtils
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
        /*expectedResourceLineage=*/ new ApiResourceLineage(),
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
