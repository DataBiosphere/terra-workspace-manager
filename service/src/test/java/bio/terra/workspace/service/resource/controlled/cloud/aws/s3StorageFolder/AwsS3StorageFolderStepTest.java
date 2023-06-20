package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.ConflictException;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
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
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

@TestInstance(Lifecycle.PER_CLASS)
public class AwsS3StorageFolderStepTest extends BaseAwsUnitTest {

  @Mock private FlightContext mockFlightContext;
  @Mock private AwsCloudContextService mockAwsCloudContextService;
  @Mock private SamService mockSamService;
  @Mock private S3Client mockS3Client;

  private MockedStatic<AwsUtils> mockAwsUtils;
  private ControlledAwsS3StorageFolderResource s3FolderResource;
  private static final AwsServiceException s3Exception =
      S3Exception.builder().message("not authorized to perform").build();

  @BeforeAll
  public void init() {
    mockAwsUtils = Mockito.mockStatic(AwsUtils.class);
    s3FolderResource =
        ControlledAwsResourceFixtures.makeDefaultAwsS3StorageFolderResource(
            ControlledResourceFixtures.WORKSPACE_ID);
  }

  @BeforeEach
  public void setup() {
    mockAwsUtils.clearInvocations();

    when(mockAwsCloudContextService.getAwsCloudContext(any()))
        .thenReturn(Optional.of(ControlledAwsResourceFixtures.makeAwsCloudContext()));
    mockAwsUtils
        .when(() -> AwsUtils.createWsmCredentialProvider(any(), any()))
        .thenReturn(ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER);
    when(mockSamService.getSamUser((AuthenticatedUserRequest) any()))
        .thenReturn(WorkspaceFixtures.SAM_USER);
    mockAwsUtils.when(() -> AwsUtils.getS3Client(any(), any())).thenReturn(mockS3Client);
    mockAwsUtils.when(() -> AwsUtils.checkException(any())).thenCallRealMethod();
  }

  @Test
  public void createS3FolderTest() throws InterruptedException {
    CreateAwsS3StorageFolderStep createS3FolderStep =
        new CreateAwsS3StorageFolderStep(
            s3FolderResource,
            mockAwsCloudContextService,
            MockMvcUtils.USER_REQUEST,
            mockSamService);

    mockAwsUtils
        .when(() -> AwsUtils.createStorageFolder(any(), any(), any()))
        .thenAnswer(invocation -> null)
        .thenThrow(WorkspaceFixtures.API_EXCEPTION);

    // do: success
    assertThat(
        createS3FolderStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // do: error
    assertThrows(ApiException.class, () -> createS3FolderStep.doStep(mockFlightContext));

    // undo: same as tests for DeleteAwsS3StorageFolderStep
  }

  @Test
  public void deleteS3FolderTest() throws InterruptedException {
    DeleteAwsS3StorageFolderStep delete3FolderStep =
        new DeleteAwsS3StorageFolderStep(s3FolderResource, mockAwsCloudContextService);

    mockAwsUtils
        .when(() -> AwsUtils.deleteStorageFolder(any(), any()))
        .thenAnswer(invocation -> null)
        .thenThrow(WorkspaceFixtures.API_EXCEPTION)
        .thenThrow(WorkspaceFixtures.NOT_FOUND_EXCEPTION);

    // do: success
    assertThat(
        delete3FolderStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // do: error
    StepResult stepResult = delete3FolderStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(ApiException.class, stepResult.getException().orElse(new Exception()).getClass());

    // do: not found (success)
    assertThat(
        delete3FolderStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // undo: always error
    assertEquals(
        InternalLogicException.class,
        delete3FolderStep
            .undoStep(mockFlightContext)
            .getException()
            .orElse(new Exception())
            .getClass());
  }

  @Test
  public void validateS3FolderCreateTest() throws InterruptedException {
    ValidateAwsS3StorageFolderCreateStep validateS3FolderCreateStep =
        new ValidateAwsS3StorageFolderCreateStep(s3FolderResource, mockAwsCloudContextService);

    mockAwsUtils
        .when(() -> AwsUtils.checkFolderExists(any(), any()))
        .thenReturn(false)
        .thenReturn(true)
        .thenThrow(WorkspaceFixtures.API_EXCEPTION);

    // do: success (not exists)
    assertThat(
        validateS3FolderCreateStep.doStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));

    // do: error (exists)
    StepResult stepResult = validateS3FolderCreateStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(
        ConflictException.class, stepResult.getException().orElse(new Exception()).getClass());

    // do: error
    assertThrows(ApiException.class, () -> validateS3FolderCreateStep.doStep(mockFlightContext));

    // undo: always success
    assertThat(
        validateS3FolderCreateStep.undoStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));
  }

  // Below are white-box tests until util functions are moved to CRL
  @Test
  public void createS3FolderTestFull() throws InterruptedException {
    CreateAwsS3StorageFolderStep createS3FolderStep =
        new CreateAwsS3StorageFolderStep(
            s3FolderResource,
            mockAwsCloudContextService,
            MockMvcUtils.USER_REQUEST,
            mockSamService);

    mockAwsUtils.when(() -> AwsUtils.createStorageFolder(any(), any(), any())).thenCallRealMethod();
    mockAwsUtils
        .when(() -> AwsUtils.putS3Object(any(), any(), any(), any(), any(), any()))
        .thenCallRealMethod();

    PutObjectResponse putResponse2xx =
        (PutObjectResponse)
            PutObjectResponse.builder()
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_2XX)
                .build();
    PutObjectResponse putResponse4xx =
        (PutObjectResponse)
            PutObjectResponse.builder()
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_4XX)
                .build();
    when(mockS3Client.putObject((PutObjectRequest) any(), (RequestBody) any()))
        .thenReturn(putResponse2xx)
        .thenReturn(putResponse4xx)
        .thenThrow(s3Exception);

    // do: success
    assertThat(
        createS3FolderStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // do:  call again to mimic request error
    assertThrows(ApiException.class, () -> createS3FolderStep.doStep(mockFlightContext));

    // do: call again to mimic other AWS error
    assertThrows(UnauthorizedException.class, () -> createS3FolderStep.doStep(mockFlightContext));

    verify(mockS3Client, times(3)).putObject((PutObjectRequest) any(), (RequestBody) any());

    // undo: same as tests for DeleteAwsS3StorageFolderStep
  }

  @Test
  public void deleteS3FolderTestFull() throws InterruptedException {
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
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_2XX)
                .build();
    when(mockS3Client.listObjectsV2((ListObjectsV2Request) any())).thenReturn(listResponse2xx);

    DeleteObjectsResponse deleteResponse2xx =
        (DeleteObjectsResponse)
            DeleteObjectsResponse.builder()
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_2XX)
                .build();
    DeleteObjectsResponse deleteResponse4xx =
        (DeleteObjectsResponse)
            DeleteObjectsResponse.builder()
                .errors(S3Error.builder().key("key1").message("message1").build())
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_4XX)
                .build();
    when(mockS3Client.deleteObjects((DeleteObjectsRequest) any()))
        .thenReturn(deleteResponse2xx)
        .thenReturn(deleteResponse4xx)
        .thenThrow(s3Exception);

    // do: success
    assertThat(
        delete3FolderStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // do: call again to mimic request error (eg. bad request)
    assertThrows(ApiException.class, () -> delete3FolderStep.doStep(mockFlightContext));

    // do: call again to mimic other AWS error
    assertThrows(UnauthorizedException.class, () -> delete3FolderStep.doStep(mockFlightContext));

    verify(mockS3Client, times(3)).listObjectsV2((ListObjectsV2Request) any());
    verify(mockS3Client, times(3)).deleteObjects((DeleteObjectsRequest) any());

    // undo: always error
    assertEquals(
        InternalLogicException.class,
        delete3FolderStep
            .undoStep(mockFlightContext)
            .getException()
            .orElse(new Exception())
            .getClass());
  }

  @Test
  public void validateS3FolderCreateTestFull() throws InterruptedException {
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
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_2XX)
                .build();
    ListObjectsV2Response listResponse2xx =
        (ListObjectsV2Response)
            ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("k1").build())
                .isTruncated(false)
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_2XX)
                .build();
    ListObjectsV2Response listResponse4xx =
        (ListObjectsV2Response)
            ListObjectsV2Response.builder()
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_4XX)
                .build();
    when(mockS3Client.listObjectsV2((ListObjectsV2Request) any()))
        .thenReturn(listResponse2xxEmpty)
        .thenReturn(listResponse2xx)
        .thenReturn(listResponse4xx)
        .thenThrow(s3Exception);

    // do: success (folder does not exist)
    assertThat(
        validateS3FolderCreateStep.doStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));

    // do: failure (folder exists)
    StepResult stepResult = validateS3FolderCreateStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(ConflictException.class, stepResult.getException().get().getClass());

    // do: call again to mimic request error (eg. bad request)
    assertThrows(ApiException.class, () -> validateS3FolderCreateStep.doStep(mockFlightContext));

    // do: call again to mimic other AWS error
    assertThrows(
        UnauthorizedException.class, () -> validateS3FolderCreateStep.doStep(mockFlightContext));

    verify(mockS3Client, times(4)).listObjectsV2((ListObjectsV2Request) any());

    // undo: always success
    assertThat(
        validateS3FolderCreateStep.undoStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));
  }
}
