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
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
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

  @MockBean private FlightContext mockFlightContext;
  @MockBean private AwsCloudContextService mockAwsCloudContextService;
  @Mock private S3Client mockS3Client;

  private MockedStatic<AwsUtils> mockAwsUtils;
  private ControlledAwsS3StorageFolderResource s3FolderResource;
  private static final AwsServiceException s3Exception1 =
      S3Exception.builder().message("not authorized to perform").build();

  @BeforeAll
  public void init() {
    mockAwsUtils = Mockito.mockStatic(AwsUtils.class);
    s3FolderResource =
        ControlledAwsResourceFixtures.makeDefaultAwsS3StorageFolderResource(
            ControlledResourceFixtures.WORKSPACE_ID);
  }

  @AfterAll
  public void terminate() {
    mockAwsUtils.close();
  }

  @BeforeEach
  public void setup() {
    when(mockFlightContext.getResult())
        .thenReturn(new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL));

    when(mockAwsCloudContextService.getAwsCloudContext(any()))
        .thenReturn(Optional.of(ControlledAwsResourceFixtures.makeAwsCloudContext()));

    when(mockSamService().getSamUser((AuthenticatedUserRequest) any()))
        .thenReturn(WorkspaceFixtures.SAM_USER);

    mockAwsUtils.clearInvocations();
    mockAwsUtils
        .when(() -> AwsUtils.createWsmCredentialProvider(any(), any()))
        .thenReturn(ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER);
    mockAwsUtils.when(() -> AwsUtils.getS3Client(any(), any())).thenReturn(mockS3Client);
    mockAwsUtils.when(() -> AwsUtils.checkException(any())).thenCallRealMethod();
  }

  @Test
  public void createS3Folder_doTest() throws InterruptedException {
    CreateAwsS3StorageFolderStep createS3FolderStep =
        new CreateAwsS3StorageFolderStep(
            s3FolderResource,
            mockAwsCloudContextService,
            MockMvcUtils.USER_REQUEST,
            mockSamService());

    mockAwsUtils
        .when(() -> AwsUtils.createStorageFolder(any(), any(), any()))
        .thenAnswer(invocation -> null) /* success */
        .thenThrow(WorkspaceFixtures.API_EXCEPTION);

    // success
    assertThat(
        createS3FolderStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // error
    assertThrows(
        WorkspaceFixtures.API_EXCEPTION.getClass(),
        () -> createS3FolderStep.doStep(mockFlightContext));
  }

  @Test
  public void createS3Folder_undoTest() throws InterruptedException {
    CreateAwsS3StorageFolderStep createS3FolderStep =
        new CreateAwsS3StorageFolderStep(
            s3FolderResource,
            mockAwsCloudContextService,
            MockMvcUtils.USER_REQUEST,
            mockSamService());

    // same as tests for DeleteAwsS3StorageFolderStep, verify that internal function
    // executeDeleteAwsS3StorageFolder is called
    try (MockedStatic<DeleteAwsS3StorageFolderStep> mockDeleteStep =
        Mockito.mockStatic(DeleteAwsS3StorageFolderStep.class)) {
      mockDeleteStep
          .when(() -> DeleteAwsS3StorageFolderStep.executeDeleteAwsS3StorageFolder(any(), any()))
          .thenReturn(StepResult.getStepResultSuccess());

      assertThat(
          createS3FolderStep.undoStep(mockFlightContext),
          equalTo(StepResult.getStepResultSuccess()));
      mockDeleteStep.verify(
          () -> DeleteAwsS3StorageFolderStep.executeDeleteAwsS3StorageFolder(any(), any()));
    }
  }

  @Test
  public void deleteS3FolderTest() throws InterruptedException {
    DeleteAwsS3StorageFolderStep delete3FolderStep =
        new DeleteAwsS3StorageFolderStep(s3FolderResource, mockAwsCloudContextService);

    mockAwsUtils
        .when(() -> AwsUtils.deleteStorageFolder(any(), any()))
        .thenAnswer(invocation -> null) /* success */
        .thenThrow(WorkspaceFixtures.API_EXCEPTION)
        .thenThrow(WorkspaceFixtures.NOT_FOUND_EXCEPTION);

    // success
    assertThat(
        delete3FolderStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // error
    StepResult stepResult = delete3FolderStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(
        WorkspaceFixtures.API_EXCEPTION.getClass(),
        stepResult.getException().orElse(new Exception()).getClass());

    // not found (success)
    assertThat(
        delete3FolderStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  public void deleteS3Folder_undoTest() throws InterruptedException {
    DeleteAwsS3StorageFolderStep delete3FolderStep =
        new DeleteAwsS3StorageFolderStep(s3FolderResource, mockAwsCloudContextService);

    // always error
    assertEquals(
        InternalLogicException.class,
        delete3FolderStep
            .undoStep(mockFlightContext)
            .getException()
            .orElse(new Exception())
            .getClass());
  }

  @Test
  public void validateS3FolderCreate_doTest() throws InterruptedException {
    ValidateAwsS3StorageFolderCreateStep validateS3FolderCreateStep =
        new ValidateAwsS3StorageFolderCreateStep(s3FolderResource, mockAwsCloudContextService);

    mockAwsUtils
        .when(() -> AwsUtils.checkFolderExists(any(), any()))
        .thenReturn(false) /* success */
        .thenReturn(true) /* failure */
        .thenThrow(WorkspaceFixtures.API_EXCEPTION);

    // success (not exists)
    assertThat(
        validateS3FolderCreateStep.doStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));

    // failure (exists)
    StepResult stepResult = validateS3FolderCreateStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(
        ConflictException.class, stepResult.getException().orElse(new Exception()).getClass());

    // error
    assertThrows(
        WorkspaceFixtures.API_EXCEPTION.getClass(),
        () -> validateS3FolderCreateStep.doStep(mockFlightContext));
  }

  @Test
  public void validateS3FolderCreate_undoTest() throws InterruptedException {
    ValidateAwsS3StorageFolderCreateStep validateS3FolderCreateStep =
        new ValidateAwsS3StorageFolderCreateStep(s3FolderResource, mockAwsCloudContextService);

    // always success
    assertThat(
        validateS3FolderCreateStep.undoStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));
  }

  // Below are white-box tests until util functions are moved to CRL

  @Test
  public void createS3Folder_doTestFull() throws InterruptedException {
    CreateAwsS3StorageFolderStep createS3FolderStep =
        new CreateAwsS3StorageFolderStep(
            s3FolderResource,
            mockAwsCloudContextService,
            MockMvcUtils.USER_REQUEST,
            mockSamService());

    mockAwsUtils.when(() -> AwsUtils.createStorageFolder(any(), any(), any())).thenCallRealMethod();
    mockAwsUtils
        .when(() -> AwsUtils.putS3Object(any(), any(), any(), any(), any(), any()))
        .thenCallRealMethod();

    PutObjectResponse putResponse200 =
        (PutObjectResponse)
            PutObjectResponse.builder()
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_200)
                .build();
    PutObjectResponse putResponse400 =
        (PutObjectResponse)
            PutObjectResponse.builder()
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_400)
                .build();
    when(mockS3Client.putObject((PutObjectRequest) any(), (RequestBody) any()))
        .thenReturn(putResponse200)
        .thenReturn(putResponse400)
        .thenThrow(s3Exception1);

    // success
    assertThat(
        createS3FolderStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // call again to mimic request error
    assertThrows(ApiException.class, () -> createS3FolderStep.doStep(mockFlightContext));

    // call again to mimic other AWS error
    assertThrows(UnauthorizedException.class, () -> createS3FolderStep.doStep(mockFlightContext));

    verify(mockS3Client, times(3)).putObject((PutObjectRequest) any(), (RequestBody) any());
  }

  @Test
  public void deleteS3Folder_doTestFull() throws InterruptedException {
    DeleteAwsS3StorageFolderStep delete3FolderStep =
        new DeleteAwsS3StorageFolderStep(s3FolderResource, mockAwsCloudContextService);

    mockAwsUtils.when(() -> AwsUtils.deleteStorageFolder(any(), any())).thenCallRealMethod();
    mockAwsUtils
        .when(() -> AwsUtils.getS3ObjectKeysByPrefix(any(), any(), any(), any(), anyInt()))
        .thenCallRealMethod();
    mockAwsUtils
        .when(() -> AwsUtils.deleteS3Objects(any(), any(), any(), any()))
        .thenCallRealMethod();

    ListObjectsV2Response listResponse200 =
        (ListObjectsV2Response)
            ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("k1").build())
                .isTruncated(false)
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_200)
                .build();
    when(mockS3Client.listObjectsV2((ListObjectsV2Request) any())).thenReturn(listResponse200);

    DeleteObjectsResponse deleteResponse200 =
        (DeleteObjectsResponse)
            DeleteObjectsResponse.builder()
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_200)
                .build();
    DeleteObjectsResponse deleteResponse400 =
        (DeleteObjectsResponse)
            DeleteObjectsResponse.builder()
                .errors(S3Error.builder().key("key1").message("message1").build())
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_400)
                .build();
    when(mockS3Client.deleteObjects((DeleteObjectsRequest) any()))
        .thenReturn(deleteResponse200)
        .thenReturn(deleteResponse400)
        .thenThrow(s3Exception1);

    // success
    assertThat(
        delete3FolderStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // call again to mimic request error
    StepResult stepResult = delete3FolderStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(ApiException.class, stepResult.getException().get().getClass());

    // call again to mimic other AWS error
    assertThrows(UnauthorizedException.class, () -> delete3FolderStep.doStep(mockFlightContext));

    verify(mockS3Client, times(3)).listObjectsV2((ListObjectsV2Request) any());
    verify(mockS3Client, times(3)).deleteObjects((DeleteObjectsRequest) any());
  }

  @Test
  public void validateS3FolderCreate_doTestFull() throws InterruptedException {
    ValidateAwsS3StorageFolderCreateStep validateS3FolderCreateStep =
        new ValidateAwsS3StorageFolderCreateStep(s3FolderResource, mockAwsCloudContextService);

    mockAwsUtils.when(() -> AwsUtils.checkFolderExists(any(), any())).thenCallRealMethod();
    mockAwsUtils
        .when(() -> AwsUtils.getS3ObjectKeysByPrefix(any(), any(), any(), any(), anyInt()))
        .thenCallRealMethod();

    ListObjectsV2Response listResponse200Empty =
        (ListObjectsV2Response)
            ListObjectsV2Response.builder()
                .isTruncated(false)
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_200)
                .build();
    ListObjectsV2Response listResponse200 =
        (ListObjectsV2Response)
            ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("k1").build())
                .isTruncated(false)
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_200)
                .build();
    ListObjectsV2Response listResponse400 =
        (ListObjectsV2Response)
            ListObjectsV2Response.builder()
                .sdkHttpResponse(ControlledAwsResourceFixtures.SDK_HTTP_RESPONSE_400)
                .build();
    when(mockS3Client.listObjectsV2((ListObjectsV2Request) any()))
        .thenReturn(listResponse200Empty)
        .thenReturn(listResponse200)
        .thenReturn(listResponse400)
        .thenThrow(s3Exception1);

    // success (folder does not exist)
    assertThat(
        validateS3FolderCreateStep.doStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));

    // failure (folder exists)
    StepResult stepResult = validateS3FolderCreateStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(ConflictException.class, stepResult.getException().get().getClass());

    // call again to mimic request error
    assertThrows(ApiException.class, () -> validateS3FolderCreateStep.doStep(mockFlightContext));

    // call again to mimic other AWS error
    assertThrows(
        UnauthorizedException.class, () -> validateS3FolderCreateStep.doStep(mockFlightContext));

    verify(mockS3Client, times(4)).listObjectsV2((ListObjectsV2Request) any());
  }
}
