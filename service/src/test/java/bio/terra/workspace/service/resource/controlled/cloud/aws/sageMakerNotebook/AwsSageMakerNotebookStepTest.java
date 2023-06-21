package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_ENVIRONMENT_NOTEBOOK_ROLE_ARN;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_LANDING_ZONE_KMS_KEY_ARN;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.CliConfiguration;
import bio.terra.workspace.common.BaseAwsUnitTest;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.internal.waiters.DefaultWaiterResponse;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.sagemaker.SageMakerClient;
import software.amazon.awssdk.services.sagemaker.model.CreateNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.CreateNotebookInstanceResponse;
import software.amazon.awssdk.services.sagemaker.model.DeleteNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.DeleteNotebookInstanceResponse;
import software.amazon.awssdk.services.sagemaker.model.DescribeNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.DescribeNotebookInstanceResponse;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;
import software.amazon.awssdk.services.sagemaker.model.SageMakerException;
import software.amazon.awssdk.services.sagemaker.waiters.SageMakerWaiter;

@TestInstance(Lifecycle.PER_CLASS)
public class AwsSageMakerNotebookStepTest extends BaseAwsUnitTest {

  @Mock protected FlightContext mockFlightContext;
  @Mock protected AwsCloudContextService mockAwsCloudContextService;
  @Mock protected SamService mockSamService;
  @Mock protected CliConfiguration mockCliConfiguration;
  @Mock protected SageMakerClient mockSageMakerClient;
  @Mock protected SageMakerWaiter mockSageMakerWaiter;
  protected MockedStatic<AwsUtils> mockAwsUtils;
  protected ControlledAwsSageMakerNotebookResource notebookResource;
  protected static final AwsServiceException sageMakerException1 =
      SageMakerException.builder().message("not authorized to perform").build();
  protected static final AwsServiceException sageMakerException2 =
      SageMakerException.builder().message("ResourceNotFoundException").build();
  protected static final AwsServiceException sageMakerException3 =
      SageMakerException.builder().message("Unable to transition to").build();

  protected static final WaiterResponse waiterResponse =
      DefaultWaiterResponse.builder()
          .attemptsExecuted(1)
          .response(DescribeNotebookInstanceResponse.builder().build())
          .build(); // wait successful
  protected static final WaiterResponse waiterException =
      DefaultWaiterResponse.builder()
          .attemptsExecuted(1)
          .exception(sageMakerException3)
          .build(); // wait failure

  @BeforeAll
  public void init() {
    mockAwsUtils = Mockito.mockStatic(AwsUtils.class);
    notebookResource =
        ControlledAwsResourceFixtures.makeDefaultAwsSagemakerNotebookResource(
            ControlledResourceFixtures.WORKSPACE_ID);
  }

  @BeforeEach
  public void setup() {
    when(mockFlightContext.getResult())
        .thenReturn(new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL));

    when(mockAwsCloudContextService.getAwsCloudContext(any()))
        .thenReturn(Optional.of(ControlledAwsResourceFixtures.makeAwsCloudContext()));

    when(mockSamService.getSamUser((AuthenticatedUserRequest) any()))
        .thenReturn(WorkspaceFixtures.SAM_USER);

    when(mockCliConfiguration.getServerName()).thenReturn("serverName");

    mockAwsUtils.clearInvocations();
    mockAwsUtils
        .when(() -> AwsUtils.createWsmCredentialProvider(any(), any()))
        .thenReturn(ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER);
    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerClient(any(), any()))
        .thenReturn(mockSageMakerClient);
    mockAwsUtils.when(() -> AwsUtils.getSageMakerWaiter(any())).thenReturn(mockSageMakerWaiter);
    mockAwsUtils.when(() -> AwsUtils.checkException(any())).thenCallRealMethod();
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
  public void createNotebookTest() throws InterruptedException {
    CreateAwsSageMakerNotebookStep createNotebookStep =
        new CreateAwsSageMakerNotebookStep(
            notebookResource,
            mockAwsCloudContextService,
            MockMvcUtils.USER_REQUEST,
            mockSamService,
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

    // do: create success, wait success
    assertThat(
        createNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // do: create success, wait error
    assertThrows(
        WorkspaceFixtures.NOT_FOUND_EXCEPTION.getClass(),
        () -> createNotebookStep.doStep(mockFlightContext));

    // do: create error
    assertThrows(
        WorkspaceFixtures.API_EXCEPTION.getClass(),
        () -> createNotebookStep.doStep(mockFlightContext));

    // TODO(BENCH-717) undo create
  }

  @Test
  public void deleteNotebookTest() throws InterruptedException {
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

    // do: delete success, wait success
    assertThat(
        deleteNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // do: delete success, wait failure (not found)
    assertThat(
        deleteNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // do: delete success, wait error
    StepResult stepResult = deleteNotebookStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(
        WorkspaceFixtures.API_EXCEPTION.getClass(), stepResult.getException().get().getClass());

    // do: delete failure (not found)
    assertThat(
        deleteNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // do: delete error
    assertThrows(
        WorkspaceFixtures.UNAUTHORIZED_EXCEPTION.getClass(),
        () -> deleteNotebookStep.doStep(mockFlightContext));

    // undo: always error
    assertEquals(
        InternalLogicException.class,
        deleteNotebookStep
            .undoStep(mockFlightContext)
            .getException()
            .orElse(new Exception())
            .getClass());
  }

  @Test
  public void validateNotebookDeleteTest() throws InterruptedException {
    ValidateAwsSageMakerNotebookDeleteStep validateNotebookDeleteStep =
        new ValidateAwsSageMakerNotebookDeleteStep(notebookResource, mockAwsCloudContextService);

    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any()))
        .thenReturn(NotebookInstanceStatus.STOPPED)
        .thenReturn(NotebookInstanceStatus.IN_SERVICE)
        .thenThrow(WorkspaceFixtures.NOT_FOUND_EXCEPTION)
        .thenThrow(WorkspaceFixtures.API_EXCEPTION)
        .thenThrow(WorkspaceFixtures.UNAUTHORIZED_EXCEPTION);

    // do: success (Stopped)
    assertThat(
        validateNotebookDeleteStep.doStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));

    // do: failure (InService)
    assertEquals(
        StepStatus.STEP_RESULT_FAILURE_FATAL,
        validateNotebookDeleteStep.doStep(mockFlightContext).getStepStatus());

    // do: success (not found)
    assertThat(
        validateNotebookDeleteStep.doStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));

    // do: call again to mimic request error
    StepResult stepResult = validateNotebookDeleteStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(
        WorkspaceFixtures.API_EXCEPTION.getClass(), stepResult.getException().get().getClass());

    // do: call again to mimic other AWS error
    assertThrows(
        WorkspaceFixtures.UNAUTHORIZED_EXCEPTION.getClass(),
        () -> validateNotebookDeleteStep.doStep(mockFlightContext));

    // undo: always success
    assertThat(
        validateNotebookDeleteStep.undoStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));
  }

  // Below are white-box tests until util functions are moved to CRL

  @Test
  public void createNotebookTestFull() throws InterruptedException {
    CreateAwsSageMakerNotebookStep createNotebookStep =
        new CreateAwsSageMakerNotebookStep(
            notebookResource,
            mockAwsCloudContextService,
            MockMvcUtils.USER_REQUEST,
            mockSamService,
            mockCliConfiguration);

    doReturn(setupFlightMapForCreateNotebookStep()).when(mockFlightContext).getInputParameters();

    mockAwsUtils
        .when(() -> AwsUtils.createSageMakerNotebook(any(), any(), any(), any(), any(), any()))
        .thenCallRealMethod();
    mockAwsUtils
        .when(() -> AwsUtils.waitForSageMakerNotebookStatus(any(), any(), any()))
        .thenCallRealMethod()
        .thenCallRealMethod();

    CreateNotebookInstanceResponse createResponse200 =
        (CreateNotebookInstanceResponse)
            CreateNotebookInstanceResponse.builder()
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_200)
                .build();
    CreateNotebookInstanceResponse createResponse400 =
        (CreateNotebookInstanceResponse)
            CreateNotebookInstanceResponse.builder()
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_400)
                .build();
    when(mockSageMakerClient.createNotebookInstance((CreateNotebookInstanceRequest) any()))
        .thenReturn(createResponse200)
        .thenReturn(createResponse200)
        .thenReturn(createResponse400)
        .thenThrow(sageMakerException1);

    when(mockSageMakerWaiter.waitUntilNotebookInstanceInService(
            (DescribeNotebookInstanceRequest) any()))
        .thenReturn(waiterResponse)
        .thenReturn(waiterException);

    // do: create success, wait success
    assertThat(
        createNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // do: create success, wait failure
    assertThrows(BadRequestException.class, () -> createNotebookStep.doStep(mockFlightContext));

    // do: call again to mimic request error
    assertThrows(ApiException.class, () -> createNotebookStep.doStep(mockFlightContext));

    // do: call again to mimic other AWS error
    assertThrows(UnauthorizedException.class, () -> createNotebookStep.doStep(mockFlightContext));

    verify(mockSageMakerClient, times(4))
        .createNotebookInstance((CreateNotebookInstanceRequest) any());
    verify(mockSageMakerWaiter, times(2))
        .waitUntilNotebookInstanceInService((DescribeNotebookInstanceRequest) any());

    // TODO(BENCH-717) undo create
  }

  @Test
  public void deleteNotebookTestFull() throws InterruptedException {
    DeleteAwsSageMakerNotebookStep deleteNotebookStep =
        new DeleteAwsSageMakerNotebookStep(notebookResource, mockAwsCloudContextService);

    mockAwsUtils.when(() -> AwsUtils.deleteSageMakerNotebook(any(), any())).thenCallRealMethod();
    mockAwsUtils
        .when(() -> AwsUtils.waitForSageMakerNotebookStatus(any(), any(), any()))
        .thenCallRealMethod()
        .thenCallRealMethod();

    DeleteNotebookInstanceResponse deleteResponse200 =
        (DeleteNotebookInstanceResponse)
            DeleteNotebookInstanceResponse.builder()
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_200)
                .build();
    DeleteNotebookInstanceResponse deleteResponse400 =
        (DeleteNotebookInstanceResponse)
            DeleteNotebookInstanceResponse.builder()
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_400)
                .build();
    when(mockSageMakerClient.deleteNotebookInstance((DeleteNotebookInstanceRequest) any()))
        .thenReturn(deleteResponse200)
        .thenReturn(deleteResponse200)
        .thenReturn(deleteResponse200)
        .thenThrow(sageMakerException2)
        .thenReturn(deleteResponse400)
        .thenThrow(sageMakerException1);

    when(mockSageMakerWaiter.waitUntilNotebookInstanceDeleted(
            (DescribeNotebookInstanceRequest) any()))
        .thenReturn(waiterResponse)
        .thenThrow(sageMakerException2)
        .thenReturn(waiterException);

    // do: delete success, wait success
    assertThat(
        deleteNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // do: delete success, wait failure (not found)
    assertThat(
        deleteNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // do: delete success, wait error
    assertThrows(BadRequestException.class, () -> deleteNotebookStep.doStep(mockFlightContext));

    // do: delete failure (not found)
    assertThat(
        deleteNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // do: delete request error
    StepResult stepResult = deleteNotebookStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(
        WorkspaceFixtures.API_EXCEPTION.getClass(), stepResult.getException().get().getClass());

    // do: delete AWS error
    assertThrows(
        WorkspaceFixtures.UNAUTHORIZED_EXCEPTION.getClass(),
        () -> deleteNotebookStep.doStep(mockFlightContext));

    verify(mockSageMakerClient, times(6))
        .deleteNotebookInstance((DeleteNotebookInstanceRequest) any());
    verify(mockSageMakerWaiter, times(3))
        .waitUntilNotebookInstanceDeleted((DescribeNotebookInstanceRequest) any());

    // undo: always error
    assertEquals(
        InternalLogicException.class,
        deleteNotebookStep
            .undoStep(mockFlightContext)
            .getException()
            .orElse(new Exception())
            .getClass());
  }

  @Test
  public void validateNotebookDeleteTestFull() throws InterruptedException {
    ValidateAwsSageMakerNotebookDeleteStep validateNotebookDeleteStep =
        new ValidateAwsSageMakerNotebookDeleteStep(notebookResource, mockAwsCloudContextService);

    mockAwsUtils.when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any())).thenCallRealMethod();

    DescribeNotebookInstanceResponse describeResponse200Stopped =
        (DescribeNotebookInstanceResponse)
            DescribeNotebookInstanceResponse.builder()
                .notebookInstanceStatus(NotebookInstanceStatus.STOPPED)
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_200)
                .build();
    DescribeNotebookInstanceResponse describeResponse200InService =
        (DescribeNotebookInstanceResponse)
            DescribeNotebookInstanceResponse.builder()
                .notebookInstanceStatus(NotebookInstanceStatus.IN_SERVICE)
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_200)
                .build();
    DescribeNotebookInstanceResponse describeResponse400 =
        (DescribeNotebookInstanceResponse)
            DescribeNotebookInstanceResponse.builder()
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_400)
                .build();
    when(mockSageMakerClient.describeNotebookInstance((DescribeNotebookInstanceRequest) any()))
        .thenReturn(describeResponse200Stopped)
        .thenReturn(describeResponse200InService)
        .thenThrow(sageMakerException2)
        .thenReturn(describeResponse400)
        .thenThrow(sageMakerException1);

    // do: success (Stopped)
    assertThat(
        validateNotebookDeleteStep.doStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));

    // do: failure (InService)
    assertEquals(
        StepStatus.STEP_RESULT_FAILURE_FATAL,
        validateNotebookDeleteStep.doStep(mockFlightContext).getStepStatus());

    // do: success (not found)
    assertThat(
        validateNotebookDeleteStep.doStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));

    // do: call again to mimic request error
    StepResult stepResult = validateNotebookDeleteStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(ApiException.class, stepResult.getException().get().getClass());

    // do: call again to mimic other AWS error
    assertThrows(
        UnauthorizedException.class, () -> validateNotebookDeleteStep.doStep(mockFlightContext));

    verify(mockSageMakerClient, times(5))
        .describeNotebookInstance((DescribeNotebookInstanceRequest) any());

    // undo: always success
    assertThat(
        validateNotebookDeleteStep.undoStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));
  }
}
