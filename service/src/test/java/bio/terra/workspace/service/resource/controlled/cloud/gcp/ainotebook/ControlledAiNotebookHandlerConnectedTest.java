package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.DEFAULT_AI_NOTEBOOK_MACHINE_TYPE_ALLOWING_ACCELERATOR_CONFIG;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceResource;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@Tag("connected")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ControlledAiNotebookHandlerConnectedTest extends BaseConnectedTest {
  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired JobService jobService;
  @Autowired CrlService crlService;

  private UUID workspaceId;
  private String projectId;
  private String notebookInstanceId;
  private String notebookLocation;
  private UUID resourceId;

  /** Create a notebook with machineType and acceleratorConfig specified to be used in all tests. */
  @BeforeAll
  public void setup() throws Exception {
    workspaceId =
        mockMvcUtils
            .createWorkspaceWithCloudContext(userAccessUtils.defaultUserAuthRequest())
            .getId();

    ApiWorkspaceDescription workspace =
        mockMvcUtils.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    projectId = workspace.getGcpContext().getProjectId();

    ApiGcpAiNotebookInstanceResource notebook =
        mockMvcUtils
            .createAiNotebookInstance(
                userAccessUtils.defaultUserAuthRequest(),
                workspaceId,
                /*location=*/ null,
                DEFAULT_AI_NOTEBOOK_MACHINE_TYPE_ALLOWING_ACCELERATOR_CONFIG,
                DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG)
            .getAiNotebookInstance();

    assertEquals(
        DEFAULT_AI_NOTEBOOK_MACHINE_TYPE_ALLOWING_ACCELERATOR_CONFIG,
        notebook.getAttributes().getMachineType());

    assertEquals(
        AcceleratorConfig.toApiAcceleratorConfig(DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG),
        notebook.getAttributes().getAcceleratorConfig());

    notebookInstanceId = notebook.getAttributes().getInstanceId();
    notebookLocation = notebook.getAttributes().getLocation();
    resourceId = notebook.getMetadata().getResourceId();
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
  public void makeResourceFromDb_populates_empty_machineType() {
    // Artificially set the machineType to be null, and see if the handler correctly populates the
    // empty field.
    ControlledAiNotebookInstanceAttributes attributes =
        new ControlledAiNotebookInstanceAttributes(
            notebookInstanceId,
            notebookLocation,
            projectId,
            /*machineType=*/ null,
            /*acceleratorConfig=*/ null);

    String attributesJson = DbSerDes.toJson(attributes);

    // Place the artificial attributes in the dbResource.
    DbResource dbResource =
        new DbResource()
            .workspaceUuid(workspaceId)
            .resourceId(resourceId)
            .name("notebook-to-be-populated-with-machine-type-and-accelerator-config")
            .cloudPlatform(CloudPlatform.GCP)
            .cloningInstructions(CloningInstructions.COPY_NOTHING)
            .stewardshipType(StewardshipType.CONTROLLED)
            .resourceType(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .createdByEmail("fake-email")
            .properties(new HashMap<>())
            .state(WsmResourceState.READY)
            .attributes(attributesJson);

    // The attributes (in dbResource) artificially have machineType set to null, so the handler
    // should populate machineType and acceleratorConfig with values from the cloud.
    WsmResource notebookInstance =
        ControlledAiNotebookHandler.getHandler().makeResourceFromDb(dbResource);

    ControlledAiNotebookInstanceAttributes instanceAttributesWithCloudRetrieval =
        DbSerDes.fromJson(
            notebookInstance.attributesToJson(), ControlledAiNotebookInstanceAttributes.class);

    assertEquals(
        DEFAULT_AI_NOTEBOOK_MACHINE_TYPE_ALLOWING_ACCELERATOR_CONFIG,
        instanceAttributesWithCloudRetrieval.getMachineType());
    assertEquals(
        DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG,
        instanceAttributesWithCloudRetrieval.getAcceleratorConfig());
  }
}
