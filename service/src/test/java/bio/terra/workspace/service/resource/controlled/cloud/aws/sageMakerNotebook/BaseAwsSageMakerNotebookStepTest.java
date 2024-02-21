package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.WORKSPACE_ID;
import static bio.terra.workspace.common.mocks.MockMvcUtils.USER_REQUEST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.CliConfiguration;
import bio.terra.workspace.common.BaseAwsSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.AwsTestUtils;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import software.amazon.awssdk.services.sagemaker.SageMakerClient;
import software.amazon.awssdk.services.sagemaker.waiters.SageMakerWaiter;

public abstract class BaseAwsSageMakerNotebookStepTest extends BaseAwsSpringBootUnitTest {

  @MockBean protected FlightContext mockFlightContext;
  @MockBean protected AwsCloudContextService mockAwsCloudContextService;
  @MockBean protected CliConfiguration mockCliConfiguration;
  @Mock protected SageMakerClient mockSageMakerClient;
  @Mock protected SageMakerWaiter mockSageMakerWaiter;
  protected static MockedStatic<AwsUtils> mockAwsUtils;

  protected final ControlledAwsSageMakerNotebookResource notebookResource =
      ControlledAwsResourceFixtures.makeDefaultAwsSagemakerNotebookResource(WORKSPACE_ID);
  protected final FlightMap flightMap = new FlightMap();

  @BeforeAll
  public void init() throws Exception {
    super.init();
    mockAwsUtils = mockStatic(AwsUtils.class, Mockito.CALLS_REAL_METHODS);

    flightMap.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), USER_REQUEST);
    flightMap.makeImmutable();
  }

  @AfterAll
  public void terminate() {
    mockAwsUtils.close();
  }

  @BeforeEach
  public void setup() {
    when(mockFlightContext.getInputParameters()).thenReturn(flightMap);
    when(mockFlightContext.getResult())
        .thenReturn(new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL));

    when(mockSamService().getSamUser((AuthenticatedUserRequest) any()))
        .thenReturn(WorkspaceFixtures.SAM_USER);

    when(mockCliConfiguration.getServerName()).thenReturn("serverName");

    when(mockAwsCloudContextService.getRequiredAwsCloudContext(any()))
        .thenReturn(AwsTestUtils.makeAwsCloudContext());

    mockAwsUtils
        .when(() -> AwsUtils.createWsmCredentialProvider(any(), any()))
        .thenReturn(AWS_CREDENTIALS_PROVIDER);
    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerClient(any(), any()))
        .thenReturn(mockSageMakerClient);
    mockAwsUtils.when(() -> AwsUtils.getSageMakerWaiter(any())).thenReturn(mockSageMakerWaiter);
  }
}
