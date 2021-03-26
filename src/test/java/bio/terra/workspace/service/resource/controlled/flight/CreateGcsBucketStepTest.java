package bio.terra.workspace.service.resource.controlled.flight;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateGcsBucketStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.google.cloud.storage.StorageClass;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public class CreateGcsBucketStepTest extends BaseUnitTest {

  @Mock private FlightContext mockFlightContext;
  @Mock private CrlService mockCrlService;
  @Mock private StorageCow mockStorageCow;
  @Mock private WorkspaceDao mockWorkspaceDao;
  @Mock private AuthenticatedUserRequest mockUserRequest;

  @Captor private ArgumentCaptor<BucketInfo> bucketInfoCaptor;

  private CreateGcsBucketStep createGcsBucketStep;

  @BeforeEach
  public void setup() {
    createGcsBucketStep =
        new CreateGcsBucketStep(
            mockCrlService,
            ControlledResourceFixtures.BUCKET_RESOURCE,
            mockWorkspaceDao,
            mockUserRequest);
    GcpCloudContext fakeContext = new GcpCloudContext("fake-project-id");
    doReturn(Optional.of(fakeContext)).when(mockWorkspaceDao).getGcpCloudContext(any());
    doReturn(mockStorageCow).when(mockCrlService).createStorageCow(any(), eq(mockUserRequest));
  }

  @Test
  public void testCreatesBucket() throws RetryException, InterruptedException {
    final FlightMap inputFlightMap = new FlightMap();
    inputFlightMap.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS,
        ControlledResourceFixtures.GOOGLE_BUCKET_CREATION_PARAMETERS);
    inputFlightMap.makeImmutable();
    doReturn(inputFlightMap).when(mockFlightContext).getInputParameters();

    final StepResult stepResult = createGcsBucketStep.doStep(mockFlightContext);
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
    verify(mockStorageCow).create(bucketInfoCaptor.capture());

    final BucketInfo info = bucketInfoCaptor.getValue();
    assertThat(info.getName(), equalTo(ControlledResourceFixtures.BUCKET_NAME));
    assertThat(info.getLocation(), equalTo(ControlledResourceFixtures.BUCKET_LOCATION));
    assertThat(info.getStorageClass(), equalTo(StorageClass.STANDARD));

    assertThat(
        info.getLifecycleRules(),
        containsInAnyOrder(
            new LifecycleRule(
                LifecycleAction.newDeleteAction(),
                LifecycleCondition.newBuilder()
                    .setAge(64)
                    .setIsLive(true)
                    .setMatchesStorageClass(List.of(StorageClass.ARCHIVE))
                    .setNumberOfNewerVersions(2)
                    .build()),
            new LifecycleRule(
                LifecycleAction.newSetStorageClassAction(StorageClass.NEARLINE),
                LifecycleCondition.newBuilder()
                    .setCreatedBefore(
                        new com.google.api.client.util.DateTime("2017-02-18T00:00:00Z"))
                    .setMatchesStorageClass(List.of(StorageClass.STANDARD))
                    .build())));
  }
}
