package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.API_EXCEPTION;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.NOT_FOUND_EXCEPTION;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.SAM_USER;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.WORKSPACE_ID;
import static bio.terra.workspace.common.mocks.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.TestUtils.assertStepResultFatal;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.ConflictException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseAwsSpringBootUnitTest;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.utils.AwsTestUtils;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class AwsS3StorageFolderStepTest extends BaseAwsSpringBootUnitTest {

  @MockBean private FlightContext mockFlightContext;
  @MockBean private AwsCloudContextService mockAwsCloudContextService;
  @Mock private S3Client mockS3Client;
  private static MockedStatic<AwsUtils> mockAwsUtils;

  private final ControlledAwsS3StorageFolderResource folderResource =
      ControlledAwsResourceFixtures.makeDefaultAwsS3StorageFolderResource(WORKSPACE_ID);
  private final AwsServiceException s3Exception1 =
      S3Exception.builder().message("not authorized to perform").build();

  @BeforeAll
  public void init() throws Exception {
    super.init();
    mockAwsUtils = mockStatic(AwsUtils.class, Mockito.CALLS_REAL_METHODS);
  }

  @AfterAll
  public void terminate() {
    mockAwsUtils.close();
  }

  @BeforeEach
  public void setup() {
    FlightMap flightMap = new FlightMap();
    flightMap.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), USER_REQUEST);

    when(mockFlightContext.getInputParameters()).thenReturn(flightMap);
    when(mockFlightContext.getResult())
        .thenReturn(new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL));

    when(mockSamService().getSamUser((AuthenticatedUserRequest) any())).thenReturn(SAM_USER);

    when(mockAwsCloudContextService.getRequiredAwsCloudContext(any()))
        .thenReturn(AwsTestUtils.makeAwsCloudContext());

    mockAwsUtils
        .when(() -> AwsUtils.createWsmCredentialProvider(any(), any()))
        .thenReturn(AWS_CREDENTIALS_PROVIDER);
    mockAwsUtils.when(() -> AwsUtils.getS3Client(any(), any())).thenReturn(mockS3Client);
  }

  @Test
  void createS3Folder_doTest() throws InterruptedException {
    CreateAwsS3StorageFolderStep createS3FolderStep =
        new CreateAwsS3StorageFolderStep(
            folderResource, mockAwsCloudContextService, mockSamService());

    mockAwsUtils
        .when(() -> AwsUtils.createStorageFolder(any(), any(), any()))
        .thenAnswer(invocation -> null) /* success */
        .thenThrow(API_EXCEPTION);

    // success
    assertThat(
        createS3FolderStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // error
    assertStepResultFatal(createS3FolderStep.doStep(mockFlightContext), ApiException.class);
  }

  @Test
  void createS3Folder_undoTest() throws InterruptedException {
    CreateAwsS3StorageFolderStep createS3FolderStep =
        new CreateAwsS3StorageFolderStep(
            folderResource, mockAwsCloudContextService, mockSamService());

    // same as tests for DeleteAwsS3StorageFolderStep, verify that internal function
    // executeDeleteAwsS3StorageFolder is called
    try (MockedStatic<DeleteAwsS3StorageFolderStep> mockDeleteStep =
        mockStatic(DeleteAwsS3StorageFolderStep.class)) {
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
  void deleteS3Folder_doTest() throws InterruptedException {
    DeleteAwsS3StorageFolderStep delete3FolderStep =
        new DeleteAwsS3StorageFolderStep(
            folderResource, mockAwsCloudContextService, mockSamService());

    mockAwsUtils
        .when(() -> AwsUtils.deleteStorageFolder(any(), any()))
        .thenAnswer(invocation -> null) /* success */
        .thenThrow(NOT_FOUND_EXCEPTION)
        .thenThrow(API_EXCEPTION);

    // success
    assertThat(
        delete3FolderStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // failure (not found)
    assertThat(
        delete3FolderStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // error
    assertStepResultFatal(delete3FolderStep.doStep(mockFlightContext), ApiException.class);
  }

  @Test
  void deleteS3Folder_undoTest() throws InterruptedException {
    DeleteAwsS3StorageFolderStep delete3FolderStep =
        new DeleteAwsS3StorageFolderStep(
            folderResource, mockAwsCloudContextService, mockSamService());

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
  void validateS3FolderCreate_doTest() throws InterruptedException {
    ValidateAwsS3StorageFolderCreateStep validateS3FolderCreateStep =
        new ValidateAwsS3StorageFolderCreateStep(
            folderResource, mockAwsCloudContextService, mockSamService());

    mockAwsUtils
        .when(() -> AwsUtils.checkFolderExists(any(), any()))
        .thenReturn(false) /* success */
        .thenReturn(true) /* failure */
        .thenThrow(API_EXCEPTION);

    // success (not exists)
    assertThat(
        validateS3FolderCreateStep.doStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));

    // failure (exists)
    assertStepResultFatal(
        validateS3FolderCreateStep.doStep(mockFlightContext), ConflictException.class);

    // error
    assertStepResultFatal(validateS3FolderCreateStep.doStep(mockFlightContext), ApiException.class);
  }

  @Test
  void validateS3FolderCreate_undoTest() throws InterruptedException {
    ValidateAwsS3StorageFolderCreateStep validateS3FolderCreateStep =
        new ValidateAwsS3StorageFolderCreateStep(
            folderResource, mockAwsCloudContextService, mockSamService());

    // always success
    assertThat(
        validateS3FolderCreateStep.undoStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));
  }

  // Below are white-box tests until util functions are moved to CRL

  @Test
  void createS3Folder_doTestFull() throws InterruptedException {
    CreateAwsS3StorageFolderStep createS3FolderStep =
        new CreateAwsS3StorageFolderStep(
            folderResource, mockAwsCloudContextService, mockSamService());

    mockAwsUtils.when(() -> AwsUtils.createStorageFolder(any(), any(), any())).thenCallRealMethod();
    mockAwsUtils
        .when(() -> AwsUtils.putS3Object(any(), any(), any(), any(), any(), any()))
        .thenCallRealMethod();

    when(mockS3Client.putObject((PutObjectRequest) any(), (RequestBody) any()))
        .thenReturn(ControlledAwsResourceFixtures.putFolderResponse200)
        .thenReturn(ControlledAwsResourceFixtures.putFolderResponse400);

    // success
    assertThat(
        createS3FolderStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // error
    assertStepResultFatal(createS3FolderStep.doStep(mockFlightContext), ApiException.class);

    verify(mockS3Client, times(2)).putObject((PutObjectRequest) any(), (RequestBody) any());
  }

  @Test
  void deleteS3Folder_doTestFull() throws InterruptedException {
    DeleteAwsS3StorageFolderStep delete3FolderStep =
        new DeleteAwsS3StorageFolderStep(
            folderResource, mockAwsCloudContextService, mockSamService());

    mockAwsUtils.when(() -> AwsUtils.deleteStorageFolder(any(), any())).thenCallRealMethod();
    mockAwsUtils
        .when(() -> AwsUtils.getS3ObjectKeysByPrefix(any(), any(), any(), any(), anyInt()))
        .thenCallRealMethod();
    mockAwsUtils
        .when(() -> AwsUtils.deleteS3Objects(any(), any(), any(), any()))
        .thenCallRealMethod();

    when(mockS3Client.listObjectsV2((ListObjectsV2Request) any()))
        .thenReturn(ControlledAwsResourceFixtures.listFolderResponse200_1_obj2);
    when(mockS3Client.deleteObjects((DeleteObjectsRequest) any()))
        .thenReturn(ControlledAwsResourceFixtures.deleteFolderResponse200)
        .thenReturn(ControlledAwsResourceFixtures.deleteFolderResponse400)
        .thenThrow(s3Exception1);

    // success
    assertThat(
        delete3FolderStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // error
    assertStepResultFatal(delete3FolderStep.doStep(mockFlightContext), ApiException.class);

    verify(mockS3Client, times(2)).listObjectsV2((ListObjectsV2Request) any());
    verify(mockS3Client, times(2)).deleteObjects((DeleteObjectsRequest) any());
  }

  @Test
  void validateS3FolderCreate_doTestFull() throws InterruptedException {
    ValidateAwsS3StorageFolderCreateStep validateS3FolderCreateStep =
        new ValidateAwsS3StorageFolderCreateStep(
            folderResource, mockAwsCloudContextService, mockSamService());

    mockAwsUtils.when(() -> AwsUtils.checkFolderExists(any(), any())).thenCallRealMethod();
    mockAwsUtils
        .when(() -> AwsUtils.getS3ObjectKeysByPrefix(any(), any(), any(), any(), anyInt()))
        .thenCallRealMethod();

    when(mockS3Client.listObjectsV2((ListObjectsV2Request) any()))
        .thenReturn(ControlledAwsResourceFixtures.listFolderResponse200_0)
        .thenReturn(ControlledAwsResourceFixtures.listFolderResponse200_1_obj2)
        .thenReturn(ControlledAwsResourceFixtures.listFolderResponse400);

    // success (folder does not exist)
    assertThat(
        validateS3FolderCreateStep.doStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));

    // failure (folder exists)
    assertStepResultFatal(
        validateS3FolderCreateStep.doStep(mockFlightContext), ConflictException.class);

    // error
    assertStepResultFatal(validateS3FolderCreateStep.doStep(mockFlightContext), ApiException.class);

    verify(mockS3Client, times(3)).listObjectsV2((ListObjectsV2Request) any());
  }
}
