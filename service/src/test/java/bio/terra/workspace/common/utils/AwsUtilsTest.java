package bio.terra.workspace.common.utils;

import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_SERVICE_EXCEPTION_1;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_SERVICE_EXCEPTION_2;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.WORKSPACE_ID;
import static bio.terra.workspace.common.utils.AwsTestUtils.AWS_REGION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.SamUser;
import bio.terra.workspace.common.BaseAwsSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.generated.model.ApiAwsCredentialAccessScope;
import bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder.ControlledAwsS3StorageFolderResource;
import bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook.ControlledAwsSageMakerNotebookResource;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.sagemaker.SageMakerClient;
import software.amazon.awssdk.services.sagemaker.model.CreateNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.DeleteNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.DescribeNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;
import software.amazon.awssdk.services.sagemaker.model.StartNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.model.StopNotebookInstanceRequest;
import software.amazon.awssdk.services.sagemaker.waiters.SageMakerWaiter;
import software.amazon.awssdk.services.sts.model.Tag;

public class AwsUtilsTest extends BaseAwsSpringBootUnitTest {

  @Mock private S3Client mockS3Client;
  @Mock private SageMakerClient mockSageMakerClient;
  @Mock private SageMakerWaiter mockSageMakerWaiter;
  private static MockedStatic<AwsUtils> mockAwsUtils;
  private static final Region awsRegion = Region.of(AWS_REGION);
  private static final Collection<Tag> tags = ControlledAwsResourceFixtures.makeTags();
  private static final ControlledAwsS3StorageFolderResource s3FolderResource =
      ControlledAwsResourceFixtures.makeDefaultAwsS3StorageFolderResource(WORKSPACE_ID);
  private static final ControlledAwsSageMakerNotebookResource notebookResource =
      ControlledAwsResourceFixtures.makeDefaultAwsSagemakerNotebookResource(WORKSPACE_ID);

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
    mockAwsUtils.when(() -> AwsUtils.getS3Client(any(), any())).thenReturn(mockS3Client);
    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerClient(any(), any()))
        .thenReturn(mockSageMakerClient);
    mockAwsUtils.when(() -> AwsUtils.getSageMakerWaiter(any())).thenReturn(mockSageMakerWaiter);
  }

  private void assertContainsTagByKey(Collection<Tag> tags, String key) {
    assertTrue(tags.stream().anyMatch(t -> key.equals(t.key())), "expected tag with key: " + key);
  }

  @Test
  void appendTagsTest() {
    Collection<Tag> tags = new HashSet<>();

    AwsUtils.appendUserTags(tags, null);
    assertTrue(tags.isEmpty());

    AwsUtils.appendUserTags(tags, WorkspaceFixtures.SAM_USER);
    assertEquals(1, tags.size());
    assertContainsTagByKey(tags, AwsUtils.TAG_KEY_USER_ID);

    // should replace existing tag, not add duplicate
    String newSubjectId = "appendTagsTest-subjectId";
    AwsUtils.appendUserTags(
        tags,
        new SamUser("appendTagsTest@email.com", newSubjectId, new BearerToken("appendTagsTest")));
    assertEquals(/* prevSize+0 */ 1, tags.size());
    assertEquals(newSubjectId, tags.iterator().next().value());

    AwsUtils.appendRoleTags(tags, ApiAwsCredentialAccessScope.WRITE_READ);
    assertEquals(/* prevSize+1 */ 2, tags.size());
    assertTrue(
        tags.stream()
            .anyMatch(
                t ->
                    t.key().equals(AwsUtils.TAG_KEY_WORKSPACE_ROLE) && t.value().equals("writer")));

    AwsUtils.appendRoleTags(tags, ApiAwsCredentialAccessScope.READ_ONLY);
    assertEquals(/* prevSize+0 */ 2, tags.size());
    assertTrue(
        tags.stream()
            .anyMatch(
                t ->
                    t.key().equals(AwsUtils.TAG_KEY_WORKSPACE_ROLE) && t.value().equals("reader")));

    AwsCloudContext cloudContext = AwsTestUtils.makeAwsCloudContext();
    AwsUtils.appendResourceTags(tags, cloudContext, null);
    assertEquals(/* prevSize+3 */ 5, tags.size());
    assertContainsTagByKey(tags, AwsUtils.TAG_KEY_VERSION);
    assertContainsTagByKey(tags, AwsUtils.TAG_KEY_TENANT);
    assertContainsTagByKey(tags, AwsUtils.TAG_KEY_ENVIRONMENT);

    ControlledAwsS3StorageFolderResource folderResource =
        ControlledAwsResourceFixtures.makeDefaultAwsS3StorageFolderResource(WORKSPACE_ID);
    AwsUtils.appendResourceTags(tags, cloudContext, folderResource);
    assertEquals(/* prevSize+1 */ 6, tags.size());
    assertContainsTagByKey(tags, AwsUtils.TAG_KEY_WORKSPACE_ID);

    AwsUtils.appendPrincipalTags(tags, cloudContext, folderResource);
    assertEquals(/* prevSize+2 */ 8, tags.size());
    assertContainsTagByKey(tags, AwsUtils.TAG_KEY_S3_BUCKET_ID);
    assertContainsTagByKey(tags, AwsUtils.TAG_KEY_TERRA_BUCKET_ID);

    ControlledAwsSageMakerNotebookResource notebookResource =
        ControlledAwsResourceFixtures.makeDefaultAwsSagemakerNotebookResource(WORKSPACE_ID);
    AwsUtils.appendPrincipalTags(tags, cloudContext, notebookResource);
    assertEquals(/* prevSize+0  */ 8, tags.size());
    // TODO(TERRA-550) Add sageMaker tags
  }

  // AWS S3 Storage Folder

  @Test
  void checkFolderExistsTest() {
    when(mockS3Client.listObjectsV2((ListObjectsV2Request) any()))
        .thenReturn(ControlledAwsResourceFixtures.listFolderResponse200_1_obj1)
        .thenReturn(ControlledAwsResourceFixtures.listFolderResponse200_0)
        .thenThrow(AWS_SERVICE_EXCEPTION_2);

    // exists
    assertTrue(
        AwsUtils.checkFolderExists(
            AWS_CREDENTIALS_PROVIDER,
            ControlledAwsResourceFixtures.makeAwsS3StorageFolderResourceBuilder(
                    WORKSPACE_ID, "resource", "bucket", ControlledAwsResourceFixtures.s3Obj1.key())
                .build()));

    // does not exist
    assertFalse(AwsUtils.checkFolderExists(AWS_CREDENTIALS_PROVIDER, s3FolderResource));

    // error / exception
    assertThrows(
        UnauthorizedException.class,
        () ->
            AwsUtils.checkFolderExists(
                AWS_CREDENTIALS_PROVIDER,
                ControlledAwsResourceFixtures.makeAwsS3StorageFolderResourceBuilder(
                        WORKSPACE_ID, "resource", "bucket", "exception")
                    .build()));

    verify(mockS3Client, times(3)).listObjectsV2((ListObjectsV2Request) any());
  }

  @Test
  void createStorageFolderTest() {
    when(mockS3Client.putObject((PutObjectRequest) any(), (RequestBody) any()))
        .thenReturn(ControlledAwsResourceFixtures.putFolderResponse200)
        .thenReturn(ControlledAwsResourceFixtures.putFolderResponse400)
        .thenThrow(AWS_SERVICE_EXCEPTION_2);

    // success
    assertDoesNotThrow(
        () ->
            AwsUtils.createStorageFolder(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, s3FolderResource, tags));

    // error from AWS
    assertThrows(
        ApiException.class,
        () ->
            AwsUtils.createStorageFolder(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, s3FolderResource, tags));

    // exception from AWS
    assertThrows(
        UnauthorizedException.class,
        () ->
            AwsUtils.createStorageFolder(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, s3FolderResource, tags));

    verify(mockS3Client, times(3)).putObject((PutObjectRequest) any(), (RequestBody) any());
  }

  @Test
  void deleteStorageFolderTest() {
    when(mockS3Client.listObjectsV2((ListObjectsV2Request) any()))
        .thenReturn(ControlledAwsResourceFixtures.listFolderResponse200_0)
        .thenReturn(ControlledAwsResourceFixtures.listFolderResponse200_2)
        .thenReturn(ControlledAwsResourceFixtures.listFolderResponse200_2)
        .thenThrow(AWS_SERVICE_EXCEPTION_1);

    when(mockS3Client.deleteObjects((DeleteObjectsRequest) any()))
        /* delete not called */
        .thenReturn(ControlledAwsResourceFixtures.deleteFolderResponse200)
        .thenThrow(AWS_SERVICE_EXCEPTION_2);

    // get keys success (empty list), delete not called
    assertDoesNotThrow(
        () ->
            AwsUtils.deleteStorageFolder(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, s3FolderResource));
    verify(mockS3Client, times(1)).listObjectsV2((ListObjectsV2Request) any());
    verify(mockS3Client, times(0)).deleteObjects((DeleteObjectsRequest) any());

    // get keys success, delete success
    assertDoesNotThrow(
        () ->
            AwsUtils.deleteStorageFolder(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, s3FolderResource));

    // get keys success, delete error
    assertThrows(
        UnauthorizedException.class,
        () ->
            AwsUtils.deleteStorageFolder(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, s3FolderResource));

    // get keys error
    assertThrows(
        NotFoundException.class,
        () ->
            AwsUtils.deleteStorageFolder(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, s3FolderResource));

    verify(mockS3Client, times(4)).listObjectsV2((ListObjectsV2Request) any());
    verify(mockS3Client, times(2)).deleteObjects((DeleteObjectsRequest) any());
  }

  @Test
  void putS3ObjectTest() {
    when(mockS3Client.putObject((PutObjectRequest) any(), (RequestBody) any()))
        .thenReturn(ControlledAwsResourceFixtures.putFolderResponse200)
        .thenReturn(ControlledAwsResourceFixtures.putFolderResponse400)
        .thenThrow(AWS_SERVICE_EXCEPTION_2);

    // success
    assertDoesNotThrow(
        () ->
            AwsUtils.putS3Object(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER,
                awsRegion,
                "bucket",
                "key1",
                "content",
                tags));

    // error from AWS
    assertThrows(
        ApiException.class,
        () ->
            AwsUtils.putS3Object(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER,
                awsRegion,
                "bucket",
                "key1",
                "content",
                tags));

    // exception from AWS
    assertThrows(
        UnauthorizedException.class,
        () ->
            AwsUtils.putS3Object(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER,
                awsRegion,
                "bucket",
                "prefix",
                "content",
                tags));

    verify(mockS3Client, times(3)).putObject((PutObjectRequest) any(), (RequestBody) any());
  }

  @Test
  void getS3ObjectKeysByPrefix_successTest() {
    // return all in a single response
    when(mockS3Client.listObjectsV2((ListObjectsV2Request) any()))
        .thenReturn(ControlledAwsResourceFixtures.listFolderResponse200_2);
    List<String> keys =
        AwsUtils.getS3ObjectKeysByPrefix(
            AWS_CREDENTIALS_PROVIDER, awsRegion, "bucket", "prefix", Integer.MAX_VALUE);
    assertEquals(2, keys.size());
    assertEquals(ControlledAwsResourceFixtures.s3Obj1.key(), keys.get(0));
    assertEquals(ControlledAwsResourceFixtures.s3Obj2.key(), keys.get(1));

    // return 1 object per response (truncated)
    when(mockS3Client.listObjectsV2((ListObjectsV2Request) any()))
        .thenReturn(ControlledAwsResourceFixtures.listFolderResponse200_1_obj1)
        .thenReturn(ControlledAwsResourceFixtures.listFolderResponse200_1_obj2);
    keys =
        AwsUtils.getS3ObjectKeysByPrefix(
            AWS_CREDENTIALS_PROVIDER, awsRegion, "bucket", "prefix", 0);
    assertEquals(2, keys.size());

    // return 0 objects (not found)
    when(mockS3Client.listObjectsV2((ListObjectsV2Request) any()))
        .thenReturn(ControlledAwsResourceFixtures.listFolderResponse200_0);
    keys =
        AwsUtils.getS3ObjectKeysByPrefix(
            AWS_CREDENTIALS_PROVIDER, awsRegion, "bucket", "notfound", -1);
    assertTrue(keys.isEmpty());

    verify(mockS3Client, times(4)).listObjectsV2((ListObjectsV2Request) any());
  }

  @Test
  void getS3ObjectKeysByPrefix_failureTest() {
    when(mockS3Client.listObjectsV2((ListObjectsV2Request) any()))
        .thenReturn(ControlledAwsResourceFixtures.listFolderResponse400)
        .thenThrow(AWS_SERVICE_EXCEPTION_2);

    // error from AWS
    assertThrows(
        ApiException.class,
        () ->
            AwsUtils.getS3ObjectKeysByPrefix(
                AWS_CREDENTIALS_PROVIDER, awsRegion, "bucket", "notfound", Integer.MAX_VALUE));

    // exception from AWS
    assertThrows(
        UnauthorizedException.class,
        () ->
            AwsUtils.getS3ObjectKeysByPrefix(
                AWS_CREDENTIALS_PROVIDER, awsRegion, "bucket", "notfound", Integer.MAX_VALUE));

    verify(mockS3Client, times(2)).listObjectsV2((ListObjectsV2Request) any());
  }

  @Test
  void deleteS3ObjectsTest() {
    List<String> keys =
        List.of(
            ControlledAwsResourceFixtures.s3Obj1.key(), ControlledAwsResourceFixtures.s3Obj2.key());

    Logger awsUtilsLogger = (Logger) LoggerFactory.getLogger(AwsUtils.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    awsUtilsLogger.addAppender(listAppender);

    when(mockS3Client.deleteObjects((DeleteObjectsRequest) any()))
        /* no op for empty list */
        .thenReturn(ControlledAwsResourceFixtures.deleteFolderResponse200)
        .thenReturn(ControlledAwsResourceFixtures.deleteFolderResponse200_1_obj1)
        .thenReturn(ControlledAwsResourceFixtures.deleteFolderResponse400)
        .thenThrow(AWS_SERVICE_EXCEPTION_2);

    // no-op on empty list
    assertDoesNotThrow(
        () ->
            AwsUtils.deleteS3Objects(
                AWS_CREDENTIALS_PROVIDER, awsRegion, "bucket", Collections.emptyList()));

    // delete success on all objects
    assertDoesNotThrow(
        () -> AwsUtils.deleteS3Objects(AWS_CREDENTIALS_PROVIDER, awsRegion, "bucket", keys));
    assertFalse(listAppender.list.stream().anyMatch(log -> log.getLevel() == Level.ERROR));

    // one object delete failure
    assertDoesNotThrow(
        () -> AwsUtils.deleteS3Objects(AWS_CREDENTIALS_PROVIDER, awsRegion, "bucket", keys));

    List<ILoggingEvent> logsList =
        listAppender.list.stream().filter(log -> log.getLevel() == Level.ERROR).toList();
    assertEquals(1, logsList.size());
    assertTrue(logsList.get(0).getMessage().contains("Failed to delete storage objects"));
    assertEquals(Level.ERROR, logsList.get(0).getLevel());

    // error from AWS
    assertThrows(
        ApiException.class,
        () -> AwsUtils.deleteS3Objects(AWS_CREDENTIALS_PROVIDER, awsRegion, "bucket", keys));

    // exception from AWS
    assertThrows(
        UnauthorizedException.class,
        () -> AwsUtils.deleteS3Objects(AWS_CREDENTIALS_PROVIDER, awsRegion, "bucket", keys));

    verify(mockS3Client, times(4)).deleteObjects((DeleteObjectsRequest) any());
    listAppender.stop();
  }

  // AWS SageMaker Notebook

  @Test
  void createSageMakerNotebookTest() {
    when(mockSageMakerClient.createNotebookInstance((CreateNotebookInstanceRequest) any()))
        .thenReturn(ControlledAwsResourceFixtures.createNotebookResponse200)
        .thenReturn(ControlledAwsResourceFixtures.createNotebookResponse400)
        .thenThrow(AWS_SERVICE_EXCEPTION_1);

    Arn userRoleArn = Arn.fromString(AwsTestUtils.AWS_ENVIRONMENT_NOTEBOOK_ROLE_ARN);
    Arn kmsKeyArn = Arn.fromString(AwsTestUtils.AWS_LANDING_ZONE_KMS_KEY_ARN);
    Arn notebookLifecycleConfigArn =
        Arn.fromString(AwsTestUtils.AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN);

    // success
    assertDoesNotThrow(
        () ->
            AwsUtils.createSageMakerNotebook(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER,
                notebookResource,
                userRoleArn,
                kmsKeyArn,
                notebookLifecycleConfigArn,
                tags));

    // error from AWS
    assertThrows(
        ApiException.class,
        () ->
            AwsUtils.createSageMakerNotebook(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER,
                notebookResource,
                userRoleArn,
                kmsKeyArn,
                notebookLifecycleConfigArn,
                tags));

    // exception from AWS
    assertThrows(
        NotFoundException.class,
        () ->
            AwsUtils.createSageMakerNotebook(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER,
                notebookResource,
                userRoleArn,
                kmsKeyArn,
                notebookLifecycleConfigArn,
                tags));

    verify(mockSageMakerClient, times(3))
        .createNotebookInstance((CreateNotebookInstanceRequest) any());
  }

  @Test
  void getSageMakerNotebookStatusTest() {
    when(mockSageMakerClient.describeNotebookInstance((DescribeNotebookInstanceRequest) any()))
        .thenReturn(ControlledAwsResourceFixtures.describeNotebookResponse200Stopped)
        .thenReturn(ControlledAwsResourceFixtures.describeNotebookResponse400)
        .thenThrow(AWS_SERVICE_EXCEPTION_1);

    // success
    assertEquals(
        ControlledAwsResourceFixtures.describeNotebookResponse200Stopped.notebookInstanceStatus(),
        AwsUtils.getSageMakerNotebookStatus(
            ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, notebookResource));

    // error from AWS
    assertThrows(
        ApiException.class,
        () ->
            AwsUtils.getSageMakerNotebookStatus(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, notebookResource));

    // exception from AWS
    assertThrows(
        NotFoundException.class,
        () ->
            AwsUtils.getSageMakerNotebookStatus(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, notebookResource));

    verify(mockSageMakerClient, times(3))
        .describeNotebookInstance((DescribeNotebookInstanceRequest) any());
  }

  @Test
  void startSageMakerNotebookTest() {
    when(mockSageMakerClient.startNotebookInstance((StartNotebookInstanceRequest) any()))
        .thenReturn(ControlledAwsResourceFixtures.startNotebookResponse200)
        .thenReturn(ControlledAwsResourceFixtures.startNotebookResponse400)
        .thenThrow(AWS_SERVICE_EXCEPTION_1);

    // success
    assertDoesNotThrow(
        () ->
            AwsUtils.startSageMakerNotebook(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, notebookResource));

    // error from AWS
    assertThrows(
        ApiException.class,
        () ->
            AwsUtils.startSageMakerNotebook(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, notebookResource));

    // exception from AWS
    assertThrows(
        NotFoundException.class,
        () ->
            AwsUtils.startSageMakerNotebook(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, notebookResource));

    verify(mockSageMakerClient, times(3))
        .startNotebookInstance((StartNotebookInstanceRequest) any());
  }

  @Test
  void stopSageMakerNotebookTest() {
    when(mockSageMakerClient.stopNotebookInstance((StopNotebookInstanceRequest) any()))
        .thenReturn(ControlledAwsResourceFixtures.stopNotebookResponse200)
        .thenReturn(ControlledAwsResourceFixtures.stopNotebookResponse400)
        .thenThrow(AWS_SERVICE_EXCEPTION_1);

    // success
    assertDoesNotThrow(
        () ->
            AwsUtils.stopSageMakerNotebook(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, notebookResource));

    // error from AWS
    assertThrows(
        ApiException.class,
        () ->
            AwsUtils.stopSageMakerNotebook(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, notebookResource));

    // exception from AWS
    assertThrows(
        NotFoundException.class,
        () ->
            AwsUtils.stopSageMakerNotebook(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, notebookResource));

    verify(mockSageMakerClient, times(3)).stopNotebookInstance((StopNotebookInstanceRequest) any());
  }

  @Test
  void deleteSageMakerNotebookTest() {
    when(mockSageMakerClient.deleteNotebookInstance((DeleteNotebookInstanceRequest) any()))
        .thenReturn(ControlledAwsResourceFixtures.deleteNotebookResponse200)
        .thenReturn(ControlledAwsResourceFixtures.deleteNotebookResponse400)
        .thenThrow(AWS_SERVICE_EXCEPTION_1)
        .thenThrow(AWS_SERVICE_EXCEPTION_2);

    // success
    assertDoesNotThrow(
        () ->
            AwsUtils.deleteSageMakerNotebook(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, notebookResource));

    // error from AWS
    assertThrows(
        ApiException.class,
        () ->
            AwsUtils.deleteSageMakerNotebook(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, notebookResource));

    // failure (notFound exception from AWS)
    assertDoesNotThrow(
        () ->
            AwsUtils.deleteSageMakerNotebook(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, notebookResource));

    // other exception from AWS
    assertThrows(
        UnauthorizedException.class,
        () ->
            AwsUtils.deleteSageMakerNotebook(
                ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER, notebookResource));

    verify(mockSageMakerClient, times(4))
        .deleteNotebookInstance((DeleteNotebookInstanceRequest) any());
  }

  @Test
  void waitForSageMakerNotebookStatusTest() {
    // error (unsupported desiredStatus)
    assertThrows(
        BadRequestException.class,
        () ->
            AwsUtils.waitForSageMakerNotebookStatus(
                AWS_CREDENTIALS_PROVIDER, notebookResource, NotebookInstanceStatus.PENDING));

    when(mockSageMakerWaiter.waitUntilNotebookInstanceInService(
            (DescribeNotebookInstanceRequest) any()))
        .thenReturn(ControlledAwsResourceFixtures.waiterNotebookResponse)
        .thenReturn(ControlledAwsResourceFixtures.waiterNotebookException_1);

    // success
    assertDoesNotThrow(
        () ->
            AwsUtils.waitForSageMakerNotebookStatus(
                AWS_CREDENTIALS_PROVIDER, notebookResource, NotebookInstanceStatus.IN_SERVICE));

    // error (waiter exception, not found)
    assertThrows(
        NotFoundException.class,
        () ->
            AwsUtils.waitForSageMakerNotebookStatus(
                AWS_CREDENTIALS_PROVIDER, notebookResource, NotebookInstanceStatus.IN_SERVICE));

    verify(mockSageMakerWaiter, times(2))
        .waitUntilNotebookInstanceInService((DescribeNotebookInstanceRequest) any());

    // error (client exception)
    when(mockSageMakerWaiter.waitUntilNotebookInstanceStopped(
            (DescribeNotebookInstanceRequest) any()))
        .thenThrow(AWS_SERVICE_EXCEPTION_1);
    assertThrows(
        NotFoundException.class,
        () ->
            AwsUtils.waitForSageMakerNotebookStatus(
                AWS_CREDENTIALS_PROVIDER, notebookResource, NotebookInstanceStatus.STOPPED));

    verify(mockSageMakerWaiter, times(1))
        .waitUntilNotebookInstanceStopped((DescribeNotebookInstanceRequest) any());

    // failure (waiter exception, not found)
    when(mockSageMakerWaiter.waitUntilNotebookInstanceDeleted(
            (DescribeNotebookInstanceRequest) any()))
        .thenReturn(ControlledAwsResourceFixtures.waiterNotebookException_1);
    assertDoesNotThrow(
        () ->
            AwsUtils.waitForSageMakerNotebookStatus(
                AWS_CREDENTIALS_PROVIDER, notebookResource, NotebookInstanceStatus.DELETING));

    verify(mockSageMakerWaiter, times(1))
        .waitUntilNotebookInstanceDeleted((DescribeNotebookInstanceRequest) any());
  }

  @Test
  void checkExceptionTest() {
    Exception ex =
        assertThrows(
            NotFoundException.class,
            () ->
                AwsUtils.checkException(
                    S3Exception.builder().message("a ResourceNotFoundException b").build(), "err"));
    assertThat(ex.getMessage(), equalTo("Resource deleted or no longer accessible"));

    // defaults ignoreNotFound=false
    SdkException notFound = S3Exception.builder().message("a RecordNotFound b").build();
    ex = assertThrows(NotFoundException.class, () -> AwsUtils.checkException(notFound, "err"));
    assertThat(ex.getMessage(), equalTo("Resource deleted or no longer accessible"));

    // set ignoreNotFound=true
    assertDoesNotThrow(() -> AwsUtils.checkException(notFound, "err", /* ignoreNotFound= */ true));

    ex =
        assertThrows(
            UnauthorizedException.class,
            () ->
                AwsUtils.checkException(
                    S3Exception.builder().message("a not authorized to perform b").build(), "err"));
    assertThat(
        ex.getMessage(),
        equalTo("Error performing resource operation, check the name / permissions and retry"));

    ex =
        assertThrows(
            BadRequestException.class,
            () ->
                AwsUtils.checkException(
                    S3Exception.builder().message("a Unable to transition to b").build(), "err"));
    assertThat(ex.getMessage(), equalTo("Unable to perform resource lifecycle operation"));

    // does not match expected errors
    ex =
        assertThrows(
            ApiException.class,
            () -> AwsUtils.checkException(S3Exception.builder().message("text").build(), "err"));
    assertThat(ex.getMessage(), equalTo("err"));
  }
}
