package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_SERVICE_EXCEPTION_1;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.API_EXCEPTION;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.NOT_FOUND_EXCEPTION;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.UNAUTHORIZED_EXCEPTION;
import static bio.terra.workspace.common.mocks.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.AwsTestUtils.AWS_ENVIRONMENT_NOTEBOOK_ROLE_ARN;
import static bio.terra.workspace.common.utils.AwsTestUtils.AWS_LANDING_ZONE_KMS_KEY_ARN;
import static bio.terra.workspace.common.utils.AwsTestUtils.AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN;
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
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import software.amazon.awssdk.services.sagemaker.model.CreateNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.DeleteNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.DescribeNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;

public class CreateDeleteAwsSageMakerNotebookStepTest extends BaseAwsSageMakerNotebookStepTest {

  protected FlightMap setupFlightMapForCreateNotebookStep() {
    FlightMap flightMapForCreate = new FlightMap();
    flightMapForCreate.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), USER_REQUEST);
    flightMapForCreate.put(
        ControlledResourceKeys.AWS_ENVIRONMENT_NOTEBOOK_ROLE_ARN,
        AWS_ENVIRONMENT_NOTEBOOK_ROLE_ARN);
    flightMapForCreate.put(
        ControlledResourceKeys.AWS_LANDING_ZONE_KMS_KEY_ARN, AWS_LANDING_ZONE_KMS_KEY_ARN);
    flightMapForCreate.put(
        ControlledResourceKeys.AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN,
        AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN);
    flightMapForCreate.makeImmutable();
    return flightMapForCreate;
  }

  @Test
  void createNotebook_doTest() throws InterruptedException {
    CreateAwsSageMakerNotebookStep createNotebookStep =
        new CreateAwsSageMakerNotebookStep(
            notebookResource, mockAwsCloudContextService, mockSamService(), mockCliConfiguration);

    doReturn(setupFlightMapForCreateNotebookStep()).when(mockFlightContext).getInputParameters();

    mockAwsUtils
        .when(() -> AwsUtils.createSageMakerNotebook(any(), any(), any(), any(), any(), any()))
        .thenAnswer(invocation -> null) /* success */
        .thenAnswer(invocation -> null) /* success */
        .thenThrow(API_EXCEPTION);
    mockAwsUtils
        .when(() -> AwsUtils.waitForSageMakerNotebookStatus(any(), any(), any()))
        .thenAnswer(invocation -> null) /* success */
        .thenThrow(NOT_FOUND_EXCEPTION);

    // create success, wait success
    assertThat(
        createNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // create success, wait error
    assertStepResultFatal(createNotebookStep.doStep(mockFlightContext), NotFoundException.class);

    // create error
    assertStepResultFatal(createNotebookStep.doStep(mockFlightContext), ApiException.class);
  }

  @Test
  void createNotebook_undoTest() throws InterruptedException {
    CreateAwsSageMakerNotebookStep createNotebookStep =
        new CreateAwsSageMakerNotebookStep(
            notebookResource, mockAwsCloudContextService, mockSamService(), mockCliConfiguration);

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
        .thenThrow(NOT_FOUND_EXCEPTION)
        .thenReturn(new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL));
    mockDeleteStep
        .when(() -> DeleteAwsSageMakerNotebookStep.executeDeleteAwsSageMakerNotebook(any(), any()))
        .thenReturn(StepResult.getStepResultSuccess())
        .thenThrow(NOT_FOUND_EXCEPTION)
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
  void deleteNotebook_doTest() throws InterruptedException {
    DeleteAwsSageMakerNotebookStep deleteNotebookStep =
        new DeleteAwsSageMakerNotebookStep(
            notebookResource, mockAwsCloudContextService, mockSamService());

    mockAwsUtils
        .when(() -> AwsUtils.deleteSageMakerNotebook(any(), any()))
        .thenAnswer(invocation -> null) /* success */
        .thenAnswer(invocation -> null) /* success */
        .thenAnswer(invocation -> null) /* success */
        .thenThrow(NOT_FOUND_EXCEPTION)
        .thenThrow(UNAUTHORIZED_EXCEPTION);
    mockAwsUtils
        .when(() -> AwsUtils.waitForSageMakerNotebookStatus(any(), any(), any()))
        .thenAnswer(invocation -> null) /* success */
        .thenThrow(NOT_FOUND_EXCEPTION)
        .thenThrow(API_EXCEPTION);

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
  void deleteNotebook_undoTest() throws InterruptedException {
    DeleteAwsSageMakerNotebookStep deleteNotebookStep =
        new DeleteAwsSageMakerNotebookStep(
            notebookResource, mockAwsCloudContextService, mockSamService());

    // always error
    assertStepResultFatal(
        deleteNotebookStep.undoStep(mockFlightContext), InternalLogicException.class);
  }

  @Test
  void validateNotebookDelete_doTest() throws InterruptedException {
    ValidateAwsSageMakerNotebookDeleteStep validateNotebookDeleteStep =
        new ValidateAwsSageMakerNotebookDeleteStep(
            notebookResource, mockAwsCloudContextService, mockSamService());

    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any()))
        .thenReturn(NotebookInstanceStatus.STOPPED)
        .thenReturn(NotebookInstanceStatus.IN_SERVICE)
        .thenThrow(NOT_FOUND_EXCEPTION)
        .thenThrow(API_EXCEPTION);

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
  void validateNotebookDelete_undoTest() throws InterruptedException {
    ValidateAwsSageMakerNotebookDeleteStep validateNotebookDeleteStep =
        new ValidateAwsSageMakerNotebookDeleteStep(
            notebookResource, mockAwsCloudContextService, mockSamService());

    // always success
    assertThat(
        validateNotebookDeleteStep.undoStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));
  }

  // Below are white-box tests until util functions are moved to CRL

  @Test
  void createNotebook_doTestFull() throws InterruptedException {
    CreateAwsSageMakerNotebookStep createNotebookStep =
        new CreateAwsSageMakerNotebookStep(
            notebookResource, mockAwsCloudContextService, mockSamService(), mockCliConfiguration);

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
        .thenReturn(ControlledAwsResourceFixtures.waiterNotebookException_2);

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
  void deleteNotebook_doTestFull() throws InterruptedException {
    DeleteAwsSageMakerNotebookStep deleteNotebookStep =
        new DeleteAwsSageMakerNotebookStep(
            notebookResource, mockAwsCloudContextService, mockSamService());

    mockAwsUtils.when(() -> AwsUtils.deleteSageMakerNotebook(any(), any())).thenCallRealMethod();
    mockAwsUtils
        .when(() -> AwsUtils.waitForSageMakerNotebookStatus(any(), any(), any()))
        .thenCallRealMethod()
        .thenCallRealMethod();

    when(mockSageMakerClient.deleteNotebookInstance((DeleteNotebookInstanceRequest) any()))
        .thenReturn(ControlledAwsResourceFixtures.deleteNotebookResponse200)
        .thenReturn(ControlledAwsResourceFixtures.deleteNotebookResponse200)
        .thenReturn(ControlledAwsResourceFixtures.deleteNotebookResponse200)
        .thenReturn(ControlledAwsResourceFixtures.deleteNotebookResponse400);

    when(mockSageMakerWaiter.waitUntilNotebookInstanceDeleted(
            (DescribeNotebookInstanceRequest) any()))
        .thenReturn(ControlledAwsResourceFixtures.waiterNotebookResponse)
        .thenThrow(AWS_SERVICE_EXCEPTION_1)
        .thenReturn(ControlledAwsResourceFixtures.waiterNotebookException_2);

    // delete success, wait success
    assertThat(
        deleteNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // delete success, wait failure (not found)
    assertThat(
        deleteNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // delete success, wait error
    assertStepResultFatal(
        deleteNotebookStep.doStep(mockFlightContext), UnauthorizedException.class);

    // delete error
    assertStepResultFatal(deleteNotebookStep.doStep(mockFlightContext), ApiException.class);

    verify(mockSageMakerClient, times(4))
        .deleteNotebookInstance((DeleteNotebookInstanceRequest) any());
    verify(mockSageMakerWaiter, times(3))
        .waitUntilNotebookInstanceDeleted((DescribeNotebookInstanceRequest) any());
  }

  @Test
  void validateNotebookDelete_undoTestFull() throws InterruptedException {
    ValidateAwsSageMakerNotebookDeleteStep validateNotebookDeleteStep =
        new ValidateAwsSageMakerNotebookDeleteStep(
            notebookResource, mockAwsCloudContextService, mockSamService());

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
