package bio.terra.workspace.service.resource.controlled;

import static bio.terra.workspace.service.resource.model.CloningInstructions.COPY_NOTHING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import bio.terra.workspace.service.resource.controlled.cloud.any.flight.update.UpdateControlledFlexibleResourceAttributesStep;
import bio.terra.workspace.service.resource.flight.UpdateStartStep;
import bio.terra.workspace.service.resource.model.CommonUpdateParameters;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.makeDefaultFlexResourceBuilder;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.defaultFlexResourceCreationParameters;


// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.
@Tag("connected")
@TestInstance(Lifecycle.PER_CLASS)
public class ControlledResourceServiceFlexTest extends BaseConnectedTest {
  // Store workspaceId instead of workspace so that for local development, one can easily use a
  // previously created workspace.
  private UUID workspaceId;
  private UserAccessUtils.TestUser user;

  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private GcpCloudContextService gcpCloudContextService;
  @Autowired private JobService jobService;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private WorkspaceConnectedTestUtils workspaceUtils;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private WsmResourceService wsmResourceService;

  @BeforeAll
  public void setup() {
    user = userAccessUtils.defaultUser();
    workspaceId =
        workspaceUtils
            .createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest())
            .getWorkspaceId();
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    jobService.setFlightDebugInfoForTest(null);
    StairwayTestUtils.enumerateJobsDump(jobService, workspaceId, user.getAuthenticatedRequest());
  }

  /** After running all tests, delete the shared workspace. */
  @AfterAll
  public void cleanUp() {
    user = userAccessUtils.defaultUser();
    Workspace workspace = workspaceService.getWorkspace(workspaceId);
    workspaceService.deleteWorkspace(workspace, user.getAuthenticatedRequest());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void updateFlexResourceUndo() {
    ControlledFlexibleResource originalFlex = makeDefaultFlexResourceBuilder(workspaceId).build();
    ControlledFlexibleResource createdFlex =
        controlledResourceService
            .createControlledResourceSync(
                originalFlex,
                null,
                user.getAuthenticatedRequest(), defaultFlexResourceCreationParameters())
            .castByEnum(WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE);
    assertTrue(originalFlex.partialEqual(createdFlex));

    Map<String, StepStatus> doErrorStep = new HashMap<>();
    doErrorStep.put(
        UpdateControlledFlexibleResourceAttributesStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_FATAL);

    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(UpdateStartStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        UpdateControlledFlexibleResourceAttributesStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);

    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder()
            .doStepFailures(doErrorStep)
            .undoStepFailures(retrySteps)
            .build());

    // Update the flex resource
    String newName = "new-resource-name";
    String newDescription = "new resource description";
    // Service methods which wait for a flight to complete will throw an
    // InvalidResultStateException when that flight fails without a cause, which occurs when a
    // flight fails via debugInfo.
    assertThrows(
        InvalidResultStateException.class,
        () ->
            wsmResourceService.updateResource(
                userAccessUtils.defaultUser().getAuthenticatedRequest(),
                createdFlex,
                new CommonUpdateParameters()
                    .setName(newName)
                    .setDescription(newDescription)
                    .setCloningInstructions(StewardshipType.CONTROLLED, COPY_NOTHING),
                null));

    // check the properties stored in WSM were not updated
    ControlledFlexibleResource fetchedResource =
        controlledResourceService
            .getControlledResource(workspaceId, createdFlex.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE);
    assertEquals(createdFlex.getName(), fetchedResource.getName());
    assertEquals(createdFlex.getDescription(), fetchedResource.getDescription());
    assertEquals(createdFlex.attributesToJson(), fetchedResource.attributesToJson());
  }
}
