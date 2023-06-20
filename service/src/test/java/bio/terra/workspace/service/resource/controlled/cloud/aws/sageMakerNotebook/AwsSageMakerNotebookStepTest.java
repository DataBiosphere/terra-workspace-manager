package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_ENVIRONMENT_NOTEBOOK_ROLE_ARN;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_LANDING_ZONE_KMS_KEY_ARN;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.CliConfiguration;
import bio.terra.workspace.common.BaseAwsUnitTest;
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
import software.amazon.awssdk.services.sagemaker.model.DescribeNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.DescribeNotebookInstanceResponse;
import software.amazon.awssdk.services.sagemaker.model.SageMakerException;
import software.amazon.awssdk.services.sagemaker.waiters.SageMakerWaiter;

@TestInstance(Lifecycle.PER_CLASS)
public class AwsSageMakerNotebookStepTest extends BaseAwsUnitTest {

  @Mock private FlightContext mockFlightContext;
  @Mock private AwsCloudContextService mockAwsCloudContextService;
  @Mock private SamService mockSamService;
  @Mock private CliConfiguration mockCliConfiguration; // TODO-Dex
  @Mock private SageMakerClient mockSageMakerClient;
  @Mock private SageMakerWaiter mockSageMakerWaiter;
  private MockedStatic<AwsUtils> mockAwsUtils;
  private ControlledAwsSageMakerNotebookResource notebookResource;
  private static final AwsServiceException sageMakerException =
      SageMakerException.builder().message("not authorized to perform").build();
  private static final WaiterResponse waiterResponse =
      DefaultWaiterResponse.builder()
          .attemptsExecuted(1)
          .response(DescribeNotebookInstanceResponse.builder().build())
          .build(); // wait successful
  private static final WaiterResponse waiterException =
      DefaultWaiterResponse.builder()
          .attemptsExecuted(1)
          .exception(SageMakerException.builder().message("ResourceNotFoundException").build())
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
    when(mockAwsCloudContextService.getAwsCloudContext(any()))
        .thenReturn(Optional.of(ControlledAwsResourceFixtures.makeAwsCloudContext()));
    mockAwsUtils
        .when(() -> AwsUtils.createWsmCredentialProvider(any(), any()))
        .thenReturn(ControlledAwsResourceFixtures.credentialProvider);
    when(mockSamService.getSamUser((AuthenticatedUserRequest) any()))
        .thenReturn(WorkspaceFixtures.SAM_USER);
    when(mockCliConfiguration.getServerName()).thenReturn("serverName");
    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerClient(any(), any()))
        .thenReturn(mockSageMakerClient);
    mockAwsUtils.when(() -> AwsUtils.getSageMakerWaiter(any())).thenReturn(mockSageMakerWaiter);
    mockAwsUtils.when(() -> AwsUtils.checkException(any())).thenCallRealMethod();
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
    doReturn(inputFlightMap).when(mockFlightContext).getInputParameters();

    mockAwsUtils
        .when(() -> AwsUtils.createSageMakerNotebook(any(), any(), any(), any(), any(), any()))
        .thenCallRealMethod();
    mockAwsUtils
        .when(() -> AwsUtils.waitForSageMakerNotebookStatus(any(), any(), any()))
        .thenCallRealMethod()
        .thenCallRealMethod();

    CreateNotebookInstanceResponse createResponse2xx =
        (CreateNotebookInstanceResponse)
            CreateNotebookInstanceResponse.builder()
                .sdkHttpResponse(ControlledAwsResourceFixtures.sdkHttpResponse2xx)
                .build();
    CreateNotebookInstanceResponse createResponse4xx =
        (CreateNotebookInstanceResponse)
            CreateNotebookInstanceResponse.builder()
                .sdkHttpResponse(ControlledAwsResourceFixtures.sdkHttpResponse4xx)
                .build();
    when(mockSageMakerClient.createNotebookInstance((CreateNotebookInstanceRequest) any()))
        .thenReturn(createResponse2xx)
        .thenReturn(createResponse2xx)
        .thenReturn(createResponse4xx)
        .thenThrow(sageMakerException);

    when(mockSageMakerWaiter.waitUntilNotebookInstanceInService(
            (DescribeNotebookInstanceRequest) any()))
        .thenReturn(waiterResponse)
        .thenReturn(waiterException);

    // create success, wait success
    StepResult stepResult = createNotebookStep.doStep(mockFlightContext);
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // create success, wait failure
    assertThrows(NotFoundException.class, () -> createNotebookStep.doStep(mockFlightContext));

    // call again to mimic request error
    assertThrows(ApiException.class, () -> createNotebookStep.doStep(mockFlightContext));

    // call again to mimic other AWS error
    assertThrows(UnauthorizedException.class, () -> createNotebookStep.doStep(mockFlightContext));

    verify(mockSageMakerClient, times(4))
        .createNotebookInstance((CreateNotebookInstanceRequest) any());
    verify(mockSageMakerWaiter, times(2))
        .waitUntilNotebookInstanceInService((DescribeNotebookInstanceRequest) any());
  }

  /*
  @Test
  public void deleteS3FolderTest() throws InterruptedException {
    DeleteAwsS3StorageFolderStep delete3FolderStep =
        new DeleteAwsS3StorageFolderStep(s3FolderResource, mockAwsCloudContextService);

    mockAwsUtils.when(() -> AwsUtils.deleteStorageFolder(any(), any())).thenCallRealMethod();
    mockAwsUtils
        .when(() -> AwsUtils.getS3ObjectKeysByPrefix(any(), any(), any(), any(), anyInt()))
        .thenCallRealMethod();
    mockAwsUtils
        .when(() -> AwsUtils.deleteS3Objects(any(), any(), any(), any()))
        .thenCallRealMethod();

    ListObjectsV2Response listResponse2xx =
        (ListObjectsV2Response)
            ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("k1").build())
                .isTruncated(false)
                .sdkHttpResponse(ControlledAwsResourceFixtures.sdkHttpResponse2xx)
                .build();
    when(mockS3Client.listObjectsV2((ListObjectsV2Request) any())).thenReturn(listResponse2xx);

    DeleteObjectsResponse deleteResponse2xx =
        (DeleteObjectsResponse)
            DeleteObjectsResponse.builder()
                .sdkHttpResponse(ControlledAwsResourceFixtures.sdkHttpResponse2xx)
                .build();
    DeleteObjectsResponse deleteResponse4xx =
        (DeleteObjectsResponse)
            DeleteObjectsResponse.builder()
                .errors(S3Error.builder().key("key1").message("message1").build())
                .sdkHttpResponse(ControlledAwsResourceFixtures.sdkHttpResponse4xx)
                .build();
    when(mockS3Client.deleteObjects((DeleteObjectsRequest) any()))
        .thenReturn(deleteResponse2xx)
        .thenReturn(deleteResponse4xx)
        .thenThrow(S3Exception.builder().message("error").build());

    // success
    StepResult stepResult = delete3FolderStep.doStep(mockFlightContext);
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // call again to mimic request error (eg. bad request)
    assertThrows(ApiException.class, () -> delete3FolderStep.doStep(mockFlightContext));

    // call again to mimic other AWS error
    assertThrows(ApiException.class, () -> delete3FolderStep.doStep(mockFlightContext));

    verify(mockS3Client, times(3)).listObjectsV2((ListObjectsV2Request) any());
    verify(mockS3Client, times(3)).deleteObjects((DeleteObjectsRequest) any());
  }

  @Test
  public void validateS3FolderCreateTest() throws InterruptedException {
    ValidateAwsS3StorageFolderCreateStep validateS3FolderCreateStep =
        new ValidateAwsS3StorageFolderCreateStep(s3FolderResource, mockAwsCloudContextService);

    mockAwsUtils.when(() -> AwsUtils.checkFolderExists(any(), any())).thenCallRealMethod();
    mockAwsUtils
        .when(() -> AwsUtils.getS3ObjectKeysByPrefix(any(), any(), any(), any(), anyInt()))
        .thenCallRealMethod();

    ListObjectsV2Response listResponse2xxEmpty =
        (ListObjectsV2Response)
            ListObjectsV2Response.builder()
                .isTruncated(false)
                .sdkHttpResponse(ControlledAwsResourceFixtures.sdkHttpResponse2xx)
                .build();
    ListObjectsV2Response listResponse2xx =
        (ListObjectsV2Response)
            ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("k1").build())
                .isTruncated(false)
                .sdkHttpResponse(ControlledAwsResourceFixtures.sdkHttpResponse2xx)
                .build();
    ListObjectsV2Response listResponse4xx =
        (ListObjectsV2Response)
            ListObjectsV2Response.builder()
                .sdkHttpResponse(ControlledAwsResourceFixtures.sdkHttpResponse4xx)
                .build();
    when(mockS3Client.listObjectsV2((ListObjectsV2Request) any()))
        .thenReturn(listResponse2xxEmpty)
        .thenReturn(listResponse2xx)
        .thenReturn(listResponse4xx)
        .thenThrow(S3Exception.builder().message("error").build());

    // success (folder does not exist)
    StepResult stepResult = validateS3FolderCreateStep.doStep(mockFlightContext);
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // failure (folder exists)
    stepResult = validateS3FolderCreateStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(ConflictException.class, stepResult.getException().get().getClass());

    // call again to mimic request error (eg. bad request)
    assertThrows(ApiException.class, () -> validateS3FolderCreateStep.doStep(mockFlightContext));

    // call again to mimic other AWS error
    assertThrows(ApiException.class, () -> validateS3FolderCreateStep.doStep(mockFlightContext));

    verify(mockS3Client, times(4)).listObjectsV2((ListObjectsV2Request) any());
  }
   */
}
