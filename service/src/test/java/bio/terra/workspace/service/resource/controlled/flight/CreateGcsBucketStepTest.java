package bio.terra.workspace.service.resource.controlled.flight;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.getGoogleBucketCreationParameters;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.uniqueName;
import static bio.terra.workspace.service.resource.controlled.GcsApiConversions.toGoogleDateTime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateGcsBucketStep;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.google.cloud.storage.StorageClass;
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
  @Mock private WorkspaceService mockWorkspaceService;
  @Mock private BucketCow mockBucketCow;

  @Captor private ArgumentCaptor<BucketInfo> bucketInfoCaptor;

  private static final String FAKE_PROJECT_ID = "fakeprojectid";

  @BeforeEach
  public void setup() {
    doReturn(mockStorageCow).when(mockCrlService).createStorageCow(any(String.class));
    when(mockWorkspaceService.getRequiredGcpProject(any())).thenReturn(FAKE_PROJECT_ID);
    when(mockStorageCow.create(bucketInfoCaptor.capture())).thenReturn(mockBucketCow);
  }

  @Test
  public void testCreatesBucket() throws RetryException, InterruptedException {
    final ApiGcpGcsBucketCreationParameters creationParameters =
        getGoogleBucketCreationParameters();

    CreateGcsBucketStep createGcsBucketStep =
        new CreateGcsBucketStep(
            mockCrlService,
            ControlledResourceFixtures.getBucketResource(creationParameters.getName()),
            mockWorkspaceService);

    final FlightMap inputFlightMap = new FlightMap();
    inputFlightMap.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS, creationParameters);
    inputFlightMap.makeImmutable();
    doReturn(inputFlightMap).when(mockFlightContext).getInputParameters();

    final StepResult stepResult = createGcsBucketStep.doStep(mockFlightContext);
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    final BucketInfo info = bucketInfoCaptor.getValue();
    assertThat(info.getName(), equalTo(creationParameters.getName()));
    assertThat(info.getLocation(), equalTo(ControlledResourceFixtures.RESOURCE_LOCATION));
    assertThat(info.getStorageClass(), equalTo(StorageClass.STANDARD));
    assertThat(info.getLifecycleRules(), hasSize(equalTo(2)));

    final LifecycleRule expectedDeleteRule =
        new LifecycleRule(
            LifecycleAction.newDeleteAction(),
            LifecycleCondition.newBuilder()
                .setAge(64)
                .setCreatedBefore(null)
                .setNumberOfNewerVersions(2)
                .setIsLive(true)
                .setMatchesStorageClass(Collections.singletonList(StorageClass.ARCHIVE))
                .build());

    final LifecycleRule deleteRule = info.getLifecycleRules().get(0);
    assertEquals(expectedDeleteRule, deleteRule);

    final LifecycleRule expectedStorageClassRule =
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
    final LifecycleRule storageClassRule = info.getLifecycleRules().get(1);
    assertEquals(expectedStorageClassRule, storageClassRule);
  }

  @Test
  public void testCreatesBucketWithoutAllParameters() throws RetryException, InterruptedException {
    final String bucketName = uniqueName("pedro");
    final CreateGcsBucketStep createGcsBucketStep =
        new CreateGcsBucketStep(
            mockCrlService,
            ControlledResourceFixtures.getBucketResource(bucketName),
            mockWorkspaceService);

    final FlightMap inputFlightMap = new FlightMap();
    inputFlightMap.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS,
        ControlledResourceFixtures.GOOGLE_BUCKET_CREATION_PARAMETERS_MINIMAL);
    inputFlightMap.makeImmutable();
    doReturn(inputFlightMap).when(mockFlightContext).getInputParameters();

    final StepResult stepResult = createGcsBucketStep.doStep(mockFlightContext);
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    final BucketInfo info = bucketInfoCaptor.getValue();
    assertThat(info.getName(), equalTo(bucketName));
    assertThat(info.getLocation(), equalTo(ControlledResourceFixtures.RESOURCE_LOCATION));
    assertThat(info.getStorageClass(), is(nullValue()));
    assertThat(info.getLifecycleRules(), empty());
  }
}
