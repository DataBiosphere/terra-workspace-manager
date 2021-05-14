package bio.terra.workspace.service.resource.controlled.flight;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.BUCKET_UPDATE_PARAMETERS_1;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.BUCKET_UPDATE_PARAMETERS_2;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.OFFSET_DATE_TIME_1;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.OFFSET_DATE_TIME_2;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.makeDefaultControlledGcsBucketResource;
import static bio.terra.workspace.service.resource.controlled.GcsApiConversions.toGoogleDateTime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateGcsBucketStep;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.DeleteLifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.google.cloud.storage.BucketInfo.LifecycleRule.SetStorageClassLifecycleAction;
import com.google.cloud.storage.StorageClass;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public class UpdateGcsBucketStepTest extends BaseUnitTest {
  private static final String PROJECT_ID = "my-gcp-project";

  private ControlledGcsBucketResource bucketResource;
  private UpdateGcsBucketStep updateGcsBucketStep;
  @Mock private FlightContext mockFlightContext;
  @Mock private CrlService mockCrlService;
  @Mock private StorageCow mockStorageCow;
  @Mock private AuthenticatedUserRequest mockUserRequest;
  @Mock private WorkspaceService mockWorkspaceService;
  @Mock private BucketCow mockBucketCow;
  @Mock private BucketCow mockBuiltBucketCow;
  @Mock private BucketCow.Builder mockBucketCowBuilder;
  @Captor private ArgumentCaptor<List<LifecycleRule>> lifecycleRulesCaptor;
  @Captor private ArgumentCaptor<StorageClass> storageClassCaptor;

  @BeforeEach
  public void setup() {
    final FlightMap inputParameters = new FlightMap();
    inputParameters.put(ControlledResourceKeys.UPDATE_PARAMETERS, BUCKET_UPDATE_PARAMETERS_1);
    doReturn(inputParameters).when(mockFlightContext).getInputParameters();

    final FlightMap workingMap = new FlightMap();
    workingMap.put(ControlledResourceKeys.PREVIOUS_UPDATE_PARAMETERS, BUCKET_UPDATE_PARAMETERS_2);
    doReturn(workingMap).when(mockFlightContext).getWorkingMap();

    doReturn(mockStorageCow).when(mockCrlService).createStorageCow(PROJECT_ID);
    doReturn(mockBucketCow).when(mockStorageCow).get(any(String.class));
    doReturn(mockBucketCowBuilder).when(mockBucketCow).toBuilder();
    doReturn(mockBucketCowBuilder)
        .when(mockBucketCowBuilder)
        .setLifecycleRules(lifecycleRulesCaptor.capture());
    doReturn(mockBucketCowBuilder)
        .when(mockBucketCowBuilder)
        .setStorageClass(storageClassCaptor.capture());

    doReturn(mockBuiltBucketCow).when(mockBucketCowBuilder).build();

    bucketResource = makeDefaultControlledGcsBucketResource().build();
    doReturn(PROJECT_ID)
        .when(mockWorkspaceService)
        .getRequiredGcpProject(bucketResource.getWorkspaceId());

    updateGcsBucketStep =
        new UpdateGcsBucketStep(bucketResource, mockCrlService, mockWorkspaceService);
  }

  @Test
  public void testDoStep() throws InterruptedException, RetryException {
    final StepResult result = updateGcsBucketStep.doStep(mockFlightContext);
    verify(mockBuiltBucketCow).update();

    assertEquals(StepResult.getStepResultSuccess(), result);
    final StorageClass storageClass = storageClassCaptor.getValue();
    assertEquals(StorageClass.STANDARD, storageClass);

    final List<LifecycleRule> rules = lifecycleRulesCaptor.getValue();
    assertThat(rules, hasSize(2));
    assertEquals(DeleteLifecycleAction.TYPE, rules.get(0).getAction().getActionType());

    final LifecycleCondition rule1condition = rules.get(0).getCondition();
    assertEquals(31, rule1condition.getAge());
    assertEquals(toGoogleDateTime(OFFSET_DATE_TIME_2), rule1condition.getCreatedBefore());
    assertEquals(3, rule1condition.getNumberOfNewerVersions());
    assertTrue(rule1condition.getIsLive());
    assertThat(rule1condition.getMatchesStorageClass(), hasSize(2));
    assertEquals(StorageClass.ARCHIVE, rule1condition.getMatchesStorageClass().get(0));
    assertEquals(StorageClass.STANDARD, rule1condition.getMatchesStorageClass().get(1));

    final LifecycleAction rule2action = rules.get(1).getAction();
    assertEquals(SetStorageClassLifecycleAction.TYPE, rule2action.getActionType());
    assertEquals(
        StorageClass.NEARLINE, ((SetStorageClassLifecycleAction) rule2action).getStorageClass());

    final LifecycleCondition rule2condition = rules.get(1).getCondition();
    assertEquals(15, rule2condition.getAge());
    assertEquals(toGoogleDateTime(OFFSET_DATE_TIME_1), rule2condition.getCreatedBefore());
    assertEquals(5, rule2condition.getNumberOfNewerVersions());
    assertTrue(rule2condition.getIsLive());
    assertThat(rule2condition.getMatchesStorageClass(), hasSize(1));
    assertEquals(StorageClass.ARCHIVE, rule2condition.getMatchesStorageClass().get(0));
  }
}
