package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import static bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures.BUCKET_UPDATE_PARAMETERS_1;
import static bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures.BUCKET_UPDATE_PARAMETERS_2;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.OFFSET_DATE_TIME_1;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.OFFSET_DATE_TIME_2;
import static bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.GcsApiConversions.toGoogleDateTime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
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

  private UpdateGcsBucketStep updateGcsBucketStep;

  @Mock private BucketCow mockExistingBucketCow;
  @Mock private BucketCow mockBuiltBucketCow;
  @Mock private BucketCow.Builder mockBucketCowBuilder;
  @Mock private CrlService mockCrlService;
  @Mock private FlightContext mockFlightContext;
  @Mock private StorageCow mockStorageCow;
  @Mock private GcpCloudContextService mockGcpCloudContextService;

  @Captor private ArgumentCaptor<List<LifecycleRule>> lifecycleRulesCaptor;
  @Captor private ArgumentCaptor<StorageClass> storageClassCaptor;

  @BeforeEach
  public void setup() {
    FlightMap inputParameters = new FlightMap();
    inputParameters.put(
        WorkspaceFlightMapKeys.ResourceKeys.UPDATE_PARAMETERS, BUCKET_UPDATE_PARAMETERS_1);
    doReturn(inputParameters).when(mockFlightContext).getInputParameters();

    FlightMap workingMap = new FlightMap();
    workingMap.put(ControlledResourceKeys.PREVIOUS_UPDATE_PARAMETERS, BUCKET_UPDATE_PARAMETERS_2);
    doReturn(workingMap).when(mockFlightContext).getWorkingMap();

    doReturn(mockStorageCow).when(mockCrlService).createStorageCow(PROJECT_ID);
    // bucket name is different each time
    doReturn(mockExistingBucketCow).when(mockStorageCow).get(any(String.class));
    doReturn(mockBucketCowBuilder).when(mockExistingBucketCow).toBuilder();
    doReturn(mockBucketCowBuilder)
        .when(mockBucketCowBuilder)
        .setLifecycleRules(lifecycleRulesCaptor.capture());
    doReturn(mockBucketCowBuilder)
        .when(mockBucketCowBuilder)
        .setStorageClass(storageClassCaptor.capture());

    doReturn(mockBuiltBucketCow).when(mockBucketCowBuilder).build();
    doReturn(mockBuiltBucketCow).when(mockBuiltBucketCow).update();
    doReturn(mockBucketCowBuilder).when(mockBuiltBucketCow).toBuilder();
    ControlledGcsBucketResource bucketResource =
        ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(null).build();
    doReturn(PROJECT_ID)
        .when(mockGcpCloudContextService)
        .getRequiredGcpProject(bucketResource.getWorkspaceId());
    doReturn(PROJECT_ID)
        .when(mockGcpCloudContextService)
        .getRequiredReadyGcpProject(bucketResource.getWorkspaceId());
    workingMap.put(WorkspaceFlightMapKeys.GCP_PROJECT_ID, PROJECT_ID);
    doReturn(workingMap).when(mockFlightContext).getWorkingMap();

    updateGcsBucketStep =
        new UpdateGcsBucketStep(bucketResource, mockCrlService, mockGcpCloudContextService);
  }

  @Test
  public void testDoStep() throws InterruptedException, RetryException {
    StepResult result = updateGcsBucketStep.doStep(mockFlightContext);
    verify(mockBuiltBucketCow, times(2)).update();

    assertEquals(StepResult.getStepResultSuccess(), result);
    StorageClass storageClass = storageClassCaptor.getValue();
    assertEquals(StorageClass.STANDARD, storageClass);

    List<LifecycleRule> rules = lifecycleRulesCaptor.getValue();
    assertThat(rules, hasSize(2));
    assertEquals(DeleteLifecycleAction.TYPE, rules.get(0).getAction().getActionType());

    LifecycleCondition rule1condition = rules.get(0).getCondition();
    assertEquals(31, rule1condition.getAge());

    // TODO: [PF-933] This (and all other date checks in this test) work around the fact that
    // Stairway FlightMap loses time zone data at serialization time by normalizing to UTC via call
    // to getValue().  Once PF-933 is addressed, these assertions should all revert to apply time
    // zone comparison by removing the getValue() call from both sides of the equality assertion.

    assertEquals(
        toGoogleDateTime(OFFSET_DATE_TIME_2).getValue(),
        rule1condition.getCreatedBefore().getValue());

    assertEquals(3, rule1condition.getNumberOfNewerVersions());
    assertTrue(rule1condition.getIsLive());
    assertThat(rule1condition.getMatchesStorageClass(), hasSize(2));
    assertEquals(StorageClass.ARCHIVE, rule1condition.getMatchesStorageClass().get(0));
    assertEquals(StorageClass.STANDARD, rule1condition.getMatchesStorageClass().get(1));

    LifecycleAction rule2action = rules.get(1).getAction();
    assertEquals(SetStorageClassLifecycleAction.TYPE, rule2action.getActionType());
    assertEquals(
        StorageClass.NEARLINE, ((SetStorageClassLifecycleAction) rule2action).getStorageClass());

    LifecycleCondition rule2condition = rules.get(1).getCondition();
    assertEquals(15, rule2condition.getAge());
    assertEquals(
        toGoogleDateTime(OFFSET_DATE_TIME_1).getValue(),
        rule2condition.getCreatedBefore().getValue());
    assertEquals(5, rule2condition.getNumberOfNewerVersions());
    assertTrue(rule2condition.getIsLive());
    assertThat(rule2condition.getMatchesStorageClass(), hasSize(1));
    assertEquals(StorageClass.ARCHIVE, rule2condition.getMatchesStorageClass().get(0));
  }

  @Test
  public void testUndoStep() throws InterruptedException, RetryException {
    StepResult result = updateGcsBucketStep.undoStep(mockFlightContext);
    verify(mockBuiltBucketCow, times(2)).update();
    assertEquals(StepResult.getStepResultSuccess(), result);

    StorageClass storageClass = storageClassCaptor.getValue();
    assertEquals(StorageClass.NEARLINE, storageClass);

    List<LifecycleRule> rules = lifecycleRulesCaptor.getValue();
    assertThat(rules, hasSize(1));

    LifecycleAction action = rules.get(0).getAction();
    assertEquals(SetStorageClassLifecycleAction.TYPE, action.getActionType());
    assertEquals(
        StorageClass.COLDLINE, ((SetStorageClassLifecycleAction) action).getStorageClass());

    LifecycleCondition condition = rules.get(0).getCondition();
    assertEquals(45, condition.getAge());
    assertEquals(
        toGoogleDateTime(OFFSET_DATE_TIME_2).getValue(), condition.getCreatedBefore().getValue());
    assertEquals(1, condition.getNumberOfNewerVersions());
    assertTrue(condition.getIsLive());
    assertThat(condition.getMatchesStorageClass(), hasSize(1));
    assertEquals(StorageClass.STANDARD, condition.getMatchesStorageClass().get(0));
  }
}
