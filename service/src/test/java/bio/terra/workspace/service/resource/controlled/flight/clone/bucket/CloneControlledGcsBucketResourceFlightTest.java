package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.connected.ResourceConnectedTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * ControlledGcpResourceApiControllerConnectedTest tests that cloning succeeds. This file just tests
 * undo.
 */
// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.
@TestInstance(Lifecycle.PER_CLASS)
public class CloneControlledGcsBucketResourceFlightTest extends BaseConnectedTest {
  /** How long to wait for a Stairway flight to complete before timing out the test. */
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(2);

  @Autowired UserAccessUtils userAccessUtils;
  @Autowired JobService jobService;
  @Autowired WorkspaceConnectedTestUtils workspaceUtils;
  @Autowired ResourceConnectedTestUtils resourceUtils;
  @Autowired ControlledResourceService controlledResourceService;

  private UUID workspaceId;
  private ControlledResource sourceResource;
  private UUID destResourceId;

  @BeforeAll
  public void setup() throws Exception {
    workspaceId =
        workspaceUtils
            .createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest())
            .getWorkspaceId();

    sourceResource =
        resourceUtils.createControlledBucket(userAccessUtils.defaultUserAuthRequest(), workspaceId);

    destResourceId = UUID.randomUUID();
  }

  @AfterAll
  public void cleanup() throws Exception {
    workspaceUtils.deleteWorkspaceAndGcpContext(
        userAccessUtils.defaultUserAuthRequest(), workspaceId);
  }

  @Test
  void cloneBucket_copyResource_undo() throws Exception {
    testCloneBucket_undo(CloningInstructions.COPY_RESOURCE);
  }

  /**
   *
   * <li>Runs CloneControlledGcsBucketResourceFlight, with last step set to fail
   * <li>Confirms destination bucket resource no longer exists
   */
  private void testCloneBucket_undo(CloningInstructions cloningInstructions) throws Exception {
    FlightMap inputs = new FlightMap();
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userAccessUtils.defaultUserAuthRequest());
    inputs.put(ResourceKeys.RESOURCE, sourceResource);
    inputs.put(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, workspaceId);
    inputs.put(ControlledResourceKeys.DESTINATION_RESOURCE_ID, destResourceId);
    // Set destination resource name
    inputs.put(ResourceKeys.RESOURCE_NAME, sourceResource.getName() + "-clone");
    inputs.put(
        ControlledResourceKeys.DESTINATION_BUCKET_NAME,
        sourceResource.getName() + "-clone-bucket-name");
    inputs.put(ControlledResourceKeys.CLONING_INSTRUCTIONS, cloningInstructions);

    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().lastStepFailure(true).build();
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CloneControlledGcsBucketResourceFlight.class,
            inputs,
            STAIRWAY_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());

    // Assert destination bucket resource doesn't exist
    ResourceNotFoundException exception =
        assertThrows(
            ResourceNotFoundException.class,
            () -> controlledResourceService.getControlledResource(workspaceId, destResourceId));
    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
  }
}
