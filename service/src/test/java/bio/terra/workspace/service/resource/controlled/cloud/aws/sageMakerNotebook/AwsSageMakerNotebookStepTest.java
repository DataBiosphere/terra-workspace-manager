package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_ENVIRONMENT_NOTEBOOK_ROLE_ARN;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_LANDING_ZONE_KMS_KEY_ARN;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_SERVICE_EXCEPTION_1;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.WORKSPACE_ID;
import static bio.terra.workspace.common.utils.TestUtils.assertStepResultFatal;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.CliConfiguration;
import bio.terra.workspace.common.BaseAwsUnitTest;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import software.amazon.awssdk.services.sagemaker.SageMakerClient;
import software.amazon.awssdk.services.sagemaker.model.CreateNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.DeleteNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.DescribeNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;
import software.amazon.awssdk.services.sagemaker.waiters.SageMakerWaiter;

public class AwsSageMakerNotebookStepTest extends BaseAwsUnitTest {

  @MockBean protected FlightContext mockFlightContext;
  @MockBean protected AwsCloudContextService mockAwsCloudContextService;
  @MockBean protected CliConfiguration mockCliConfiguration;
  @Mock protected SageMakerClient mockSageMakerClient;
  @Mock protected SageMakerWaiter mockSageMakerWaiter;
  protected static MockedStatic<AwsUtils> mockAwsUtils;

  protected final ControlledAwsSageMakerNotebookResource notebookResource =
      ControlledAwsResourceFixtures.makeDefaultAwsSagemakerNotebookResource(WORKSPACE_ID);

  @BeforeAll
  public static void init() {
    mockAwsUtils = mockStatic(AwsUtils.class, Mockito.CALLS_REAL_METHODS);
  }

  @AfterAll
  public static void terminate() {
    mockAwsUtils.close();
  }

  @BeforeEach
  public void setup() {
    when(mockFlightContext.getResult())
        .thenReturn(new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL));

    when(mockSamService().getSamUser((AuthenticatedUserRequest) any()))
        .thenReturn(WorkspaceFixtures.SAM_USER);

    when(mockCliConfiguration.getServerName()).thenReturn("serverName");

    when(mockAwsCloudContextService.getRequiredAwsCloudContext(any()))
        .thenReturn(ControlledAwsResourceFixtures.makeAwsCloudContext());

    mockAwsUtils
        .when(() -> AwsUtils.createWsmCredentialProvider(any(), any()))
        .thenReturn(AWS_CREDENTIALS_PROVIDER);
    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerClient(any(), any()))
        .thenReturn(mockSageMakerClient);
    mockAwsUtils.when(() -> AwsUtils.getSageMakerWaiter(any())).thenReturn(mockSageMakerWaiter);
  }

  protected FlightMap setupFlightMapForCreateNotebookStep() {
    FlightMap inputFlightMap = new FlightMap();
    inputFlightMap.put(
        ControlledResourceKeys.AWS_ENVIRONMENT_NOTEBOOK_ROLE_ARN,
        AWS_ENVIRONMENT_NOTEBOOK_ROLE_ARN);
    inputFlightMap.put(
        ControlledResourceKeys.AWS_LANDING_ZONE_KMS_KEY_ARN, AWS_LANDING_ZONE_KMS_KEY_ARN);
    inputFlightMap.put(
        ControlledResourceKeys.AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN,
        AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN);
    inputFlightMap.makeImmutable();
    return inputFlightMap;
  }

  @Test
  public void createNotebook_doTest() throws InterruptedException {
    CreateAwsSageMakerNotebookStep createNotebookStep =
        new CreateAwsSageMakerNotebookStep(
            notebookResource,
            mockAwsCloudContextService,
            MockMvcUtils.USER_REQUEST,
            mockSamService(),
            mockCliConfiguration);

    doReturn(setupFlightMapForCreateNotebookStep()).when(mockFlightContext).getInputParameters();

    mockAwsUtils
        .when(() -> AwsUtils.createSageMakerNotebook(any(), any(), any(), any(), any(), any()))
        .thenAnswer(invocation -> null) /* success */
        .thenAnswer(invocation -> null) /* success */
        .thenThrow(WorkspaceFixtures.API_EXCEPTION);
    mockAwsUtils
        .when(() -> AwsUtils.waitForSageMakerNotebookStatus(any(), any(), any()))
        .thenAnswer(invocation -> null) /* success */
        .thenThrow(WorkspaceFixtures.NOT_FOUND_EXCEPTION);

    // create success, wait success
    assertThat(
        createNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // create success, wait error
    assertStepResultFatal(createNotebookStep.doStep(mockFlightContext), NotFoundException.class);

    // create error
    assertStepResultFatal(createNotebookStep.doStep(mockFlightContext), ApiException.class);
  }

  @Test
  public void createNotebook_undoTest() throws InterruptedException {
    CreateAwsSageMakerNotebookStep createNotebookStep =
        new CreateAwsSageMakerNotebookStep(
            notebookResource,
            mockAwsCloudContextService,
            MockMvcUtils.USER_REQUEST,
            mockSamService(),
            mockCliConfiguration);

    // undo: same as tests for StopAwsSageMakerNotebookStep & DeleteAwsSageMakerNotebookStep,
    // verify that internal functions executeStopAwsSageMakerNotebook &
    // executeDeleteAwsSageMakerNotebook are called
    MockedStatic<StopAwsSageMakerNotebookStep> mockStopStep =
        mockStatic(StopAwsSageMakerNotebookStep.class);
    MockedStatic<DeleteAwsSageMakerNotebookStep> mockDeleteStep =
        mockStatic(DeleteAwsSageMakerNotebookStep.class);

    mockStopStep
        .when(() -> StopAwsSageMakerNotebookStep.executeStopAwsSageMakerNotebook(any(), any()))
        .thenReturn(StepResult.getStepResultSuccess())
        .thenReturn(StepResult.getStepResultSuccess())
        .thenReturn(StepResult.getStepResultSuccess())
        .thenThrow(WorkspaceFixtures.NOT_FOUND_EXCEPTION)
        .thenReturn(new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL));
    mockDeleteStep
        .when(() -> DeleteAwsSageMakerNotebookStep.executeDeleteAwsSageMakerNotebook(any(), any()))
        .thenReturn(StepResult.getStepResultSuccess())
        .thenThrow(WorkspaceFixtures.NOT_FOUND_EXCEPTION)
        .thenReturn(new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL));

    // stop success, delete success
    assertThat(
        createNotebookStep.undoStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // stop success, delete failure (not found)
    assertThat(
        createNotebookStep.undoStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // stop success, delete error
    assertEquals(
        StepStatus.STEP_RESULT_FAILURE_FATAL,
        createNotebookStep.undoStep(mockFlightContext).getStepStatus());

    // stop failure (not found)
    assertThat(
        createNotebookStep.undoStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // stop error
    assertEquals(
        StepStatus.STEP_RESULT_FAILURE_FATAL,
        createNotebookStep.undoStep(mockFlightContext).getStepStatus());

    mockStopStep.verify(
        () -> StopAwsSageMakerNotebookStep.executeStopAwsSageMakerNotebook(any(), any()), times(5));
    mockDeleteStep.verify(
        () -> DeleteAwsSageMakerNotebookStep.executeDeleteAwsSageMakerNotebook(any(), any()),
        times(3));

    mockStopStep.close();
    mockDeleteStep.close();
  }

  @Test
  public void deleteNotebook_doTest() throws InterruptedException {
    DeleteAwsSageMakerNotebookStep deleteNotebookStep =
        new DeleteAwsSageMakerNotebookStep(notebookResource, mockAwsCloudContextService);

    mockAwsUtils
        .when(() -> AwsUtils.deleteSageMakerNotebook(any(), any()))
        .thenAnswer(invocation -> null) /* success */
        .thenAnswer(invocation -> null) /* success */
        .thenAnswer(invocation -> null) /* success */
        .thenThrow(WorkspaceFixtures.NOT_FOUND_EXCEPTION)
        .thenThrow(WorkspaceFixtures.UNAUTHORIZED_EXCEPTION);
    mockAwsUtils
        .when(() -> AwsUtils.waitForSageMakerNotebookStatus(any(), any(), any()))
        .thenAnswer(invocation -> null) /* success */
        .thenThrow(WorkspaceFixtures.NOT_FOUND_EXCEPTION)
        .thenThrow(WorkspaceFixtures.API_EXCEPTION);

    // delete success, wait success
    assertThat(
        deleteNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // delete success, wait failure (not found)
    assertThat(
        deleteNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // delete success, wait error
    assertStepResultFatal(deleteNotebookStep.doStep(mockFlightContext), ApiException.class);

    // delete failure (not found)
    assertThat(
        deleteNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // delete error
    assertStepResultFatal(
        deleteNotebookStep.doStep(mockFlightContext), UnauthorizedException.class);
  }

  @Test
  public void deleteNotebook_undoTest() throws InterruptedException {
    DeleteAwsSageMakerNotebookStep deleteNotebookStep =
        new DeleteAwsSageMakerNotebookStep(notebookResource, mockAwsCloudContextService);

    // always error
    assertStepResultFatal(
        deleteNotebookStep.undoStep(mockFlightContext), InternalLogicException.class);
  }

  @Test
  public void validateNotebookDelete_doTest() throws InterruptedException {
    ValidateAwsSageMakerNotebookDeleteStep validateNotebookDeleteStep =
        new ValidateAwsSageMakerNotebookDeleteStep(notebookResource, mockAwsCloudContextService);

    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any()))
        .thenReturn(NotebookInstanceStatus.STOPPED)
        .thenReturn(NotebookInstanceStatus.IN_SERVICE)
        .thenThrow(WorkspaceFixtures.NOT_FOUND_EXCEPTION)
        .thenThrow(WorkspaceFixtures.API_EXCEPTION);

    // success (Stopped)
    assertThat(
        validateNotebookDeleteStep.doStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));

    // failure (InService)
    assertEquals(
        StepStatus.STEP_RESULT_FAILURE_FATAL,
        validateNotebookDeleteStep.doStep(mockFlightContext).getStepStatus());

    // success (not found)
    assertThat(
        validateNotebookDeleteStep.doStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));

    // error
    assertStepResultFatal(validateNotebookDeleteStep.doStep(mockFlightContext), ApiException.class);
  }

  @Test
  public void validateNotebookDelete_undoTest() throws InterruptedException {
    ValidateAwsSageMakerNotebookDeleteStep validateNotebookDeleteStep =
        new ValidateAwsSageMakerNotebookDeleteStep(notebookResource, mockAwsCloudContextService);

    // always success
    assertThat(
        validateNotebookDeleteStep.undoStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));
  }

  // Below are white-box tests until util functions are moved to CRL

  @Test
  public void createNotebook_doTestFull() throws InterruptedException {
    CreateAwsSageMakerNotebookStep createNotebookStep =
        new CreateAwsSageMakerNotebookStep(
            notebookResource,
            mockAwsCloudContextService,
            MockMvcUtils.USER_REQUEST,
            mockSamService(),
            mockCliConfiguration);

    doReturn(setupFlightMapForCreateNotebookStep()).when(mockFlightContext).getInputParameters();

    mockAwsUtils
        .when(() -> AwsUtils.createSageMakerNotebook(any(), any(), any(), any(), any(), any()))
        .thenCallRealMethod();
    mockAwsUtils
        .when(() -> AwsUtils.waitForSageMakerNotebookStatus(any(), any(), any()))
        .thenCallRealMethod()
        .thenCallRealMethod();

    when(mockSageMakerClient.createNotebookInstance((CreateNotebookInstanceRequest) any()))
        .thenReturn(ControlledAwsResourceFixtures.createNotebookResponse200)
        .thenReturn(ControlledAwsResourceFixtures.createNotebookResponse200)
        .thenReturn(ControlledAwsResourceFixtures.createNotebookResponse400);

    when(mockSageMakerWaiter.waitUntilNotebookInstanceInService(
            (DescribeNotebookInstanceRequest) any()))
        .thenReturn(ControlledAwsResourceFixtures.waiterNotebookResponse)
        .thenReturn(ControlledAwsResourceFixtures.waiterNotebookException);

    // create success, wait success
    assertThat(
        createNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // create success, wait error
    assertStepResultFatal(
        createNotebookStep.doStep(mockFlightContext), UnauthorizedException.class);

    // create error
    assertStepResultFatal(createNotebookStep.doStep(mockFlightContext), ApiException.class);

    verify(mockSageMakerClient, times(3))
        .createNotebookInstance((CreateNotebookInstanceRequest) any());
    verify(mockSageMakerWaiter, times(2))
        .waitUntilNotebookInstanceInService((DescribeNotebookInstanceRequest) any());
  }

  @Test
  public void deleteNotebook_doTestFull() throws InterruptedException {
    DeleteAwsSageMakerNotebookStep deleteNotebookStep =
        new DeleteAwsSageMakerNotebookStep(notebookResource, mockAwsCloudContextService);

    mockAwsUtils.when(() -> AwsUtils.deleteSageMakerNotebook(any(), any())).thenCallRealMethod();
    mockAwsUtils
        .when(() -> AwsUtils.waitForSageMakerNotebookStatus(any(), any(), any()))
        .thenCallRealMethod()
        .thenCallRealMethod();

    when(mockSageMakerClient.deleteNotebookInstance((DeleteNotebookInstanceRequest) any()))
        .thenReturn(ControlledAwsResourceFixtures.deleteNotebookResponse200)
        .thenReturn(ControlledAwsResourceFixtures.deleteNotebookResponse200)
        .thenReturn(ControlledAwsResourceFixtures.deleteNotebookResponse200)
        .thenThrow(AWS_SERVICE_EXCEPTION_1)
        .thenReturn(ControlledAwsResourceFixtures.deleteNotebookResponse400);

    when(mockSageMakerWaiter.waitUntilNotebookInstanceDeleted(
            (DescribeNotebookInstanceRequest) any()))
        .thenReturn(ControlledAwsResourceFixtures.waiterNotebookResponse)
        .thenThrow(AWS_SERVICE_EXCEPTION_1)
        .thenReturn(ControlledAwsResourceFixtures.waiterNotebookException);

    // delete success, wait success
    assertThat(
        deleteNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // delete success, wait failure (not found)
    assertThat(
        deleteNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // delete success, wait error
    assertStepResultFatal(
        deleteNotebookStep.doStep(mockFlightContext), UnauthorizedException.class);

    // delete failure (not found)
    assertThat(
        deleteNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // delete error
    assertStepResultFatal(deleteNotebookStep.doStep(mockFlightContext), ApiException.class);

    verify(mockSageMakerClient, times(5))
        .deleteNotebookInstance((DeleteNotebookInstanceRequest) any());
    verify(mockSageMakerWaiter, times(3))
        .waitUntilNotebookInstanceDeleted((DescribeNotebookInstanceRequest) any());
  }

  @Test
  public void validateNotebookDelete_undoTestFull() throws InterruptedException {
    ValidateAwsSageMakerNotebookDeleteStep validateNotebookDeleteStep =
        new ValidateAwsSageMakerNotebookDeleteStep(notebookResource, mockAwsCloudContextService);

    mockAwsUtils.when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any())).thenCallRealMethod();

    when(mockSageMakerClient.describeNotebookInstance((DescribeNotebookInstanceRequest) any()))
        .thenReturn(ControlledAwsResourceFixtures.describeNotebookResponse200Stopped)
        .thenReturn(ControlledAwsResourceFixtures.describeNotebookResponse200InService)
        .thenThrow(AWS_SERVICE_EXCEPTION_1)
        .thenReturn(ControlledAwsResourceFixtures.describeNotebookResponse400);

    // success (Stopped)
    assertThat(
        validateNotebookDeleteStep.doStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));

    // failure (InService)
    assertEquals(
        StepStatus.STEP_RESULT_FAILURE_FATAL,
        validateNotebookDeleteStep.doStep(mockFlightContext).getStepStatus());

    // success (not found)
    assertThat(
        validateNotebookDeleteStep.doStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));

    // error
    assertStepResultFatal(validateNotebookDeleteStep.doStep(mockFlightContext), ApiException.class);

    verify(mockSageMakerClient, times(4))
        .describeNotebookInstance((DescribeNotebookInstanceRequest) any());
  }
}
