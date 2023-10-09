package bio.terra.workspace.service.workspace.flight.aws;

import static bio.terra.workspace.service.features.FeatureService.AWS_APPLICATIONS_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.common.iam.SamUser;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseAwsConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.cloud.aws.CreateWorkspaceApplicationSecurityGroupsStep;
import bio.terra.workspace.service.workspace.flight.cloud.aws.DeleteWorkspaceApplicationSecurityGroupsStep;
import bio.terra.workspace.service.workspace.flight.cloud.aws.MakeAwsCloudContextStep;
import bio.terra.workspace.service.workspace.flight.cloud.aws.SetWorkspaceApplicationEgressIngressStep;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFinishStep;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFlight;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextFlight;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

@Tag("aws-connected")
public class CreateDeleteAwsContextFlightTest extends BaseAwsConnectedTest {

  /**
   * How long to wait for a Stairway flight to complete before timing out the test. This is set to
   * 20 minutes to allow tests to ride through service outages, cloud retries, and IAM propagation.
   */
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(20);

  @Autowired private WorkspaceService workspaceService;
  @Autowired private JobService jobService;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private AwsCloudContextService awsCloudContextService;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @MockBean private SamService mockSamService;

  @BeforeAll
  public void init() throws Exception {
    super.init();

    // Set up mock Sam to return user info

    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    SamUser mockSamUser = mock(SamUser.class);
    when(mockSamUser.getEmail()).thenReturn(userRequest.getEmail());
    when(mockSamService.getSamUser((AuthenticatedUserRequest) any())).thenReturn(mockSamUser);

    UserStatusInfo mockUserStatusInfo = mock(UserStatusInfo.class);
    when(mockUserStatusInfo.getUserEmail()).thenReturn(userRequest.getEmail());
    when(mockUserStatusInfo.getUserSubjectId()).thenReturn("1234");
    when(mockSamService.getUserStatusInfo(any())).thenReturn(mockUserStatusInfo);
  }

  @Test
  public void successCreateDeleteContextWithSecurityGroups() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    UUID workspaceUuid = createWorkspace();
    assertTrue(awsCloudContextService.getAwsCloudContext(workspaceUuid).isEmpty());

    // Retry create steps once to validate idempotency.
    FlightDebugInfo createDebugInfo =
        FlightDebugInfo.newBuilder().doStepFailures(getCreateStepNameToStepStatusMap()).build();

    FlightMap createInputs =
        WorkspaceFixtures.createCloudContextInputs(
            workspaceUuid, userRequest, CloudPlatform.AWS, spendUtils.defaultGcpSpendProfile());

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateCloudContextFlight.class,
            createInputs,
            STAIRWAY_FLIGHT_TIMEOUT,
            createDebugInfo);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    AwsCloudContext cloudContext = awsCloudContextService.getRequiredAwsCloudContext(workspaceUuid);
    assertNotNull(cloudContext.getContextFields().getApplicationSecurityGroups());

    // Retry delete steps once to validate idempotency.
    FlightDebugInfo deleteDebugInfo =
        FlightDebugInfo.newBuilder().doStepFailures(getDeleteStepNameToStepStatusMap()).build();

    FlightMap deleteInputs =
        WorkspaceFixtures.deleteCloudContextInputs(workspaceUuid, userRequest, CloudPlatform.AWS);

    flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteCloudContextFlight.class,
            deleteInputs,
            STAIRWAY_FLIGHT_TIMEOUT,
            deleteDebugInfo);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
    assertTrue(awsCloudContextService.getAwsCloudContext(workspaceUuid).isEmpty());
  }

  @Test
  public void successCreatesDeleteContextWithoutSecurityGroups() throws Exception {
    // Disable apps for this test to suppress security group creation
    disableFeature(AWS_APPLICATIONS_ENABLED);

    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    UUID workspaceUuid = createWorkspace();
    assertTrue(awsCloudContextService.getAwsCloudContext(workspaceUuid).isEmpty());

    FlightMap createInputs =
        WorkspaceFixtures.createCloudContextInputs(
            workspaceUuid, userRequest, CloudPlatform.AWS, spendUtils.defaultGcpSpendProfile());

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateCloudContextFlight.class,
            createInputs,
            STAIRWAY_FLIGHT_TIMEOUT,
            /*debugInfo*/ null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    AwsCloudContext cloudContext = awsCloudContextService.getRequiredAwsCloudContext(workspaceUuid);
    assertNull(cloudContext.getContextFields().getApplicationSecurityGroups());

    FlightMap deleteInputs =
        WorkspaceFixtures.deleteCloudContextInputs(workspaceUuid, userRequest, CloudPlatform.AWS);

    flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteCloudContextFlight.class,
            deleteInputs,
            STAIRWAY_FLIGHT_TIMEOUT,
            /*debugInfo*/ null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
    assertTrue(awsCloudContextService.getAwsCloudContext(workspaceUuid).isEmpty());
  }

  @Test
  public void undoCreateDeleteContextWithSecurityGroups() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    UUID workspaceUuid = createWorkspace();
    assertTrue(awsCloudContextService.getAwsCloudContext(workspaceUuid).isEmpty());

    // Cause a failure on the set egress/ingress step, as undo for this step is a no-op
    Map<String, StepStatus> doErrorStep =
        Map.of(
            SetWorkspaceApplicationEgressIngressStep.class.getName(),
            StepStatus.STEP_RESULT_FAILURE_FATAL);

    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(doErrorStep).build();

    FlightMap createInputs =
        WorkspaceFixtures.createCloudContextInputs(
            workspaceUuid, userRequest, CloudPlatform.AWS, spendUtils.defaultGcpSpendProfile());

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateCloudContextFlight.class,
            createInputs,
            STAIRWAY_FLIGHT_TIMEOUT,
            debugInfo);

    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());
    assertTrue(awsCloudContextService.getAwsCloudContext(workspaceUuid).isEmpty());
  }

  private SpendProfileId getSpendProfile() {
    return spendUtils.defaultSpendId();
  }

  private UUID createWorkspace() {
    Workspace request =
        WorkspaceFixtures.defaultWorkspaceBuilder(UUID.randomUUID())
            .spendProfileId(getSpendProfile())
            .build();
    return workspaceService.createWorkspace(
        request, null, null, null, userAccessUtils.defaultUserAuthRequest());
  }

  private Map<String, StepStatus> getCreateStepNameToStepStatusMap() {
    // Retry steps once to validate idempotency.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        CreateWorkspaceApplicationSecurityGroupsStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        SetWorkspaceApplicationEgressIngressStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(MakeAwsCloudContextStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        CreateCloudContextFinishStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    return retrySteps;
  }

  private Map<String, StepStatus> getDeleteStepNameToStepStatusMap() {
    // Retry steps once to validate idempotency.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        DeleteWorkspaceApplicationSecurityGroupsStep.class.getName(),
        StepStatus.STEP_RESULT_FAILURE_RETRY);
    return retrySteps;
  }
}
