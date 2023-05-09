package bio.terra.workspace.service.workspace.flight.azure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.policy.model.TpsPolicyInput;
import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.policy.model.TpsPolicyPair;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.PolicyFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.exception.RegionNotAllowedException;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFlight;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.azure.core.management.Region;
import java.time.Duration;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

@Tag("azureConnected")
class CreateAzureContextFlightTest extends BaseAzureConnectedTest {
  /** How long to wait for a Stairway flight to complete before timing out the test. */
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(10);

  @Autowired private WorkspaceService workspaceService;
  @Autowired private AzureCloudContextService azureCloudContextService;
  @Autowired private JobService jobService;
  @Autowired private UserAccessUtils userAccessUtils;

  @MockBean private LandingZoneApiDispatch landingZoneApiDispatchMock;
  private final Region landingZoneRegion = Region.FRANCE_CENTRAL;

  @BeforeEach
  void setUp() {
    initSpendProfileMock();
    initLandingZoneApiDispatchMock();
  }

  private void initLandingZoneApiDispatchMock() {
    var landingZoneId = UUID.randomUUID();
    Mockito.when(landingZoneApiDispatchMock.getLandingZoneId(Mockito.any(), Mockito.any()))
        .thenReturn(landingZoneId);
    Mockito.when(
            landingZoneApiDispatchMock.getAzureLandingZoneRegion(
                Mockito.any(), Mockito.eq(landingZoneId)))
        .thenReturn(landingZoneRegion.name());
  }

  @Test
  void successCreatesContext() throws Exception {
    Workspace workspace = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // There should be no cloud context initially.
    assertTrue(azureCloudContextService.getAzureCloudContext(workspace.getWorkspaceId()).isEmpty());

    String jobId = UUID.randomUUID().toString();
    workspaceService.createCloudContext(
        workspace,
        CloudPlatform.AZURE,
        azureTestUtils.getSpendProfile(),
        jobId,
        userRequest,
        /* resultPath */ null);

    // Wait for the job to complete
    FlightState flightState =
        StairwayTestUtils.pollUntilComplete(
            jobId, jobService.getStairway(), Duration.ofSeconds(1), STAIRWAY_FLIGHT_TIMEOUT);
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // Flight should have created a cloud context.
    assertTrue(
        azureCloudContextService.getAzureCloudContext(workspace.getWorkspaceId()).isPresent());
    AzureCloudContext azureCloudContext =
        azureCloudContextService.getAzureCloudContext(workspace.getWorkspaceId()).get();
    assertEquals(azureCloudContext, azureTestUtils.getAzureCloudContext());
  }

  @Test
  void regionPolicyConflict() throws Exception {
    Workspace workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(null)
            .spendProfileId(azureTestUtils.getSpendProfileId())
            .build();
    FlightState flightState =
        createWorkspaceForRegionTest(workspace, Region.SOUTHAFRICA_NORTH.name());
    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());
    assertTrue(flightState.getException().isPresent());
    assertTrue(
        flightState.getException().stream().allMatch(e -> e instanceof RegionNotAllowedException));
    // There should be no cloud context created.
    assertTrue(azureCloudContextService.getAzureCloudContext(workspace.getWorkspaceId()).isEmpty());
  }

  @Test
  void regionPolicyNoConflict() throws Exception {
    Workspace workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(null)
            .spendProfileId(azureTestUtils.getSpendProfileId())
            .build();
    FlightState flightState = createWorkspaceForRegionTest(workspace, landingZoneRegion.name());
    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
  }

  @NotNull
  private FlightState createWorkspaceForRegionTest(Workspace workspace, String policyRegion)
      throws InterruptedException {
    var policies =
        new TpsPolicyInputs()
            .addInputsItem(
                new TpsPolicyInput()
                    .name(PolicyFixtures.REGION_CONSTRAINT)
                    .namespace(PolicyFixtures.NAMESPACE)
                    .addAdditionalDataItem(
                        new TpsPolicyPair()
                            .key(PolicyFixtures.REGION)
                            .value("azure." + policyRegion)));

    workspaceService.createWorkspace(
        workspace, policies, null, userAccessUtils.defaultUserAuthRequest());

    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    String jobId = UUID.randomUUID().toString();
    workspaceService.createCloudContext(
        workspace,
        CloudPlatform.AZURE,
        azureTestUtils.getSpendProfile(),
        jobId,
        userRequest,
        /* resultPath */ null);

    // Wait for the job to complete
    return StairwayTestUtils.pollUntilComplete(
        jobId, jobService.getStairway(), Duration.ofSeconds(1), STAIRWAY_FLIGHT_TIMEOUT);
  }

  @Test
  void errorRevertsChanges() throws Exception {
    Workspace workspace = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // There should be no cloud context initially.
    assertTrue(azureCloudContextService.getAzureCloudContext(workspace.getWorkspaceId()).isEmpty());

    // Submit a flight class that always errors.
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().lastStepFailure(true).build();
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateCloudContextFlight.class,
            azureTestUtils.createAzureContextInputParameters(
                workspace.getWorkspaceId(), userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            debugInfo);
    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());

    // There should be no cloud context created.
    assertTrue(azureCloudContextService.getAzureCloudContext(workspace.getWorkspaceId()).isEmpty());
  }
}
