package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import static bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures.GOOGLE_BUCKET_CREATION_PARAMETERS_MINIMAL;
import static bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstants.DEFAULT_REGION;
import static bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.GcsApiConversions.toGoogleDateTime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.storage.Storage;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.google.cloud.storage.StorageClass;
import com.google.cloud.storage.StorageException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public class CreateGcsBucketStepTest extends BaseUnitTest {

  @Mock private FlightContext mockFlightContext;
  @Mock private CrlService mockCrlService;
  @Mock private StorageCow mockStorageCow;
  @Mock private GcpCloudContextService mockGcpCloudContextService;
  @Mock private BucketCow mockBucketCow;

  // Mocks for pretending the provided bucket does not exist.
  @Mock private Storage mockStorageClient;
  @Mock private Storage.Buckets mockBuckets;
  @Mock private Storage.Buckets.Get mockStorageBucketsGet;

  @Captor private ArgumentCaptor<BucketInfo> bucketInfoCaptor;

  private static final String FAKE_PROJECT_ID = "fakeprojectid";

  @BeforeEach
  public void setup() throws IOException {
    doReturn(mockStorageCow).when(mockCrlService).createStorageCow(any(String.class));
    doReturn(mockStorageClient).when(mockCrlService).createWsmSaNakedStorageClient();
    when(mockGcpCloudContextService.getRequiredGcpProject(any())).thenReturn(FAKE_PROJECT_ID);
    when(mockGcpCloudContextService.getRequiredReadyGcpProject(any())).thenReturn(FAKE_PROJECT_ID);
    when(mockStorageCow.create(bucketInfoCaptor.capture())).thenReturn(mockBucketCow);

    when(mockStorageClient.buckets()).thenReturn(mockBuckets);
    when(mockBuckets.get(any(String.class))).thenReturn(mockStorageBucketsGet);
    GoogleJsonResponseException fakeNotFoundError =
        new GoogleJsonResponseException(
            new HttpResponseException.Builder(403, "fake not found error", new HttpHeaders()),
            /* details= */ null);
    when(mockStorageBucketsGet.execute()).thenThrow(fakeNotFoundError);
  }

  @Test
  public void testCreatesBucket() throws RetryException, InterruptedException {
    ApiGcpGcsBucketCreationParameters creationParameters =
        ControlledGcpResourceFixtures.getGoogleBucketCreationParameters();

    CreateGcsBucketStep createGcsBucketStep =
        new CreateGcsBucketStep(
            mockCrlService,
            ControlledGcpResourceFixtures.getBucketResource(creationParameters.getName()),
            mockGcpCloudContextService);

    setupFlightContextMock(creationParameters);

    StepResult stepResult = createGcsBucketStep.doStep(mockFlightContext);
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    BucketInfo info = bucketInfoCaptor.getValue();
    assertThat(info.getName(), equalTo(creationParameters.getName()));
    assertThat(info.getLocation(), equalTo(DEFAULT_REGION));
    assertThat(info.getStorageClass(), equalTo(StorageClass.STANDARD));
    assertThat(info.getLifecycleRules(), hasSize(equalTo(2)));

    LifecycleRule expectedDeleteRule =
        new LifecycleRule(
            LifecycleAction.newDeleteAction(),
            LifecycleCondition.newBuilder()
                .setAge(64)
                .setCreatedBefore(null)
                .setNumberOfNewerVersions(2)
                .setIsLive(true)
                .setMatchesStorageClass(Collections.singletonList(StorageClass.ARCHIVE))
                .build());

    LifecycleRule deleteRule = info.getLifecycleRules().get(0);
    assertEquals(expectedDeleteRule, deleteRule);

    LifecycleRule expectedStorageClassRule =
        new LifecycleRule(
            LifecycleAction.newSetStorageClassAction(StorageClass.NEARLINE),
            LifecycleCondition.newBuilder()
                .setAge(null)
                .setCreatedBefore(
                    toGoogleDateTime(OffsetDateTime.of(2007, 1, 3, 0, 0, 0, 0, ZoneOffset.UTC)))
                .setNumberOfNewerVersions(null)
                .setIsLive(null)
                .setMatchesStorageClass(Collections.singletonList(StorageClass.STANDARD))
                .build());
    LifecycleRule storageClassRule = info.getLifecycleRules().get(1);
    assertEquals(expectedStorageClassRule, storageClassRule);
  }

  @Test
  public void testCreatesBucketWithoutAllParameters() throws RetryException, InterruptedException {
    String bucketName = TestUtils.appendRandomNumber("pedro");
    CreateGcsBucketStep createGcsBucketStep =
        new CreateGcsBucketStep(
            mockCrlService,
            ControlledGcpResourceFixtures.getBucketResource(bucketName),
            mockGcpCloudContextService);
    setupFlightContextMock(GOOGLE_BUCKET_CREATION_PARAMETERS_MINIMAL);

    StepResult stepResult = createGcsBucketStep.doStep(mockFlightContext);
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    BucketInfo info = bucketInfoCaptor.getValue();
    assertThat(info.getName(), equalTo(bucketName));
    assertThat(info.getLocation(), equalTo(DEFAULT_REGION));
    assertThat(info.getStorageClass(), is(nullValue()));
    assertThat(info.getLifecycleRules(), empty());
  }

  @Test
  public void createBucket_invalidBucketName_throwsBadRequestException() {
    doThrow(new StorageException(400, "bad request"))
        .when(mockStorageCow)
        .create(bucketInfoCaptor.capture());

    // A bad bucket name that fails to be caught by the WSM validation.
    String bucketName = TestUtils.appendRandomNumber("bad-bucket-name");
    CreateGcsBucketStep createGcsBucketStep =
        new CreateGcsBucketStep(
            mockCrlService,
            ControlledGcpResourceFixtures.getBucketResource(bucketName),
            mockGcpCloudContextService);
    setupFlightContextMock(GOOGLE_BUCKET_CREATION_PARAMETERS_MINIMAL);

    assertThrows(BadRequestException.class, () -> createGcsBucketStep.doStep(mockFlightContext));
  }

  private void setupFlightContextMock(ApiGcpGcsBucketCreationParameters creationParameters) {
    FlightMap inputFlightMap = new FlightMap();
    inputFlightMap.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS, creationParameters);
    inputFlightMap.makeImmutable();
    doReturn(inputFlightMap).when(mockFlightContext).getInputParameters();

    FlightMap workingMap = new FlightMap();
    doReturn(workingMap).when(mockFlightContext).getWorkingMap();
  }
}
