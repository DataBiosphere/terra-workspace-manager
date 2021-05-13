package bio.terra.workspace.service.resource.controlled;

import static bio.terra.workspace.service.resource.controlled.GcsApiConversions.getStorageClass;
import static bio.terra.workspace.service.resource.controlled.GcsApiConversions.toDateTime;
import static bio.terra.workspace.service.resource.controlled.GcsApiConversions.toGcsApi;
import static bio.terra.workspace.service.resource.controlled.GcsApiConversions.toOffsetDateTime;
import static bio.terra.workspace.service.resource.controlled.GcsApiConversions.toUpdateParameters;
import static bio.terra.workspace.service.resource.controlled.GcsApiConversions.toWsmApi;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycle;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRule;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleAction;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleActionType;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleCondition;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketUpdateParameters;
import com.google.api.client.util.DateTime;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.DeleteLifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.google.cloud.storage.BucketInfo.LifecycleRule.SetStorageClassLifecycleAction;
import com.google.cloud.storage.StorageClass;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class GcsApiConversionsTest extends BaseUnitTest {
  private static final OffsetDateTime OFFSET_DATE_TIME_1 =
      OffsetDateTime.parse("2017-12-03T10:15:30+01:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  private static final OffsetDateTime OFFSET_DATE_TIME_2 =
      OffsetDateTime.parse("2017-12-03T10:15:30-05:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  private static final DateTime DATE_TIME_1 = DateTime.parseRfc3339("1985-04-12T23:20:50.52Z");
  private static final DateTime DATE_TIME_2 = DateTime.parseRfc3339("1996-12-19T16:39:57-08:00");

  private static final ApiGcpGcsBucketLifecycleRuleCondition WSM_LIFECYCLE_RULE_CONDITION_1 =
      new ApiGcpGcsBucketLifecycleRuleCondition()
          .age(31)
          .createdBefore(OFFSET_DATE_TIME_2)
          .numNewerVersions(3)
          .live(true)
          .matchesStorageClass(
              ImmutableList.of(
                  ApiGcpGcsBucketDefaultStorageClass.ARCHIVE,
                  ApiGcpGcsBucketDefaultStorageClass.STANDARD));
  // leave a couple of things unspecified
  private static final LifecycleCondition GCS_LIFECYCLE_CONDITION_1 =
      LifecycleCondition.newBuilder()
          .setAge(42)
          .setIsLive(false)
          .setNumberOfNewerVersions(2)
          .build();
  private static final LifecycleCondition GCS_LIFECYCLE_CONDITION_2 =
      LifecycleCondition.newBuilder()
          .setAge(30)
          .setIsLive(true)
          .setCreatedBefore(DATE_TIME_2)
          .setMatchesStorageClass(ImmutableList.of(StorageClass.ARCHIVE, StorageClass.COLDLINE))
          .build();
  private static final LifecycleAction GCS_DELETE_ACTION = LifecycleAction.newDeleteAction();
  private static final LifecycleAction GCS_SET_STORAGE_CLASS_ACTION =
      LifecycleAction.newSetStorageClassAction(StorageClass.STANDARD);

  private static final LifecycleRule GCS_LIFECYCLE_RULE_1 =
      new LifecycleRule(GCS_DELETE_ACTION, GCS_LIFECYCLE_CONDITION_1);
  private static final LifecycleRule GCS_LIFECYCLE_RULE_2 =
      new LifecycleRule(GCS_SET_STORAGE_CLASS_ACTION, GCS_LIFECYCLE_CONDITION_2);

  private static final BucketInfo GCS_BUCKET_INFO_1 =
      BucketInfo.newBuilder("my-bucket")
          .setStorageClass(StorageClass.STANDARD)
          .setLifecycleRules(ImmutableList.of(GCS_LIFECYCLE_RULE_1, GCS_LIFECYCLE_RULE_2))
          .build();

  @Test
  public void testToUpdateParameters() {
    final ApiGcpGcsBucketUpdateParameters updateParameters1 = toUpdateParameters(GCS_BUCKET_INFO_1);
    assertEquals(
        ApiGcpGcsBucketDefaultStorageClass.STANDARD, updateParameters1.getDefaultStorageClass());
    final ApiGcpGcsBucketLifecycle wsmLifecycle = updateParameters1.getLifecycle();
    assertThat(wsmLifecycle.getRules(), hasSize(2));

    final ApiGcpGcsBucketLifecycleRule rule1 = wsmLifecycle.getRules().get(0);
    assertEquals(ApiGcpGcsBucketLifecycleRuleActionType.DELETE, rule1.getAction().getType());
    assertNull(rule1.getAction().getStorageClass());

    final ApiGcpGcsBucketLifecycleRule rule2 = wsmLifecycle.getRules().get(1);
    assertEquals(
        ApiGcpGcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS, rule2.getAction().getType());
    assertEquals(ApiGcpGcsBucketDefaultStorageClass.STANDARD, rule2.getAction().getStorageClass());
  }

  @Test
  public void testLifecycleRuleConversions() {
    final ApiGcpGcsBucketLifecycleRule wsmRule1 = toWsmApi(GCS_LIFECYCLE_RULE_1);
    assertEquals(ApiGcpGcsBucketLifecycleRuleActionType.DELETE, wsmRule1.getAction().getType());
    assertNull(wsmRule1.getAction().getStorageClass());

    assertEquals(42, wsmRule1.getCondition().getAge());
    assertFalse(wsmRule1.getCondition().isLive());
    assertEquals(2, wsmRule1.getCondition().getNumNewerVersions());
    assertNull(wsmRule1.getCondition().getMatchesStorageClass()); // not empty list

    final LifecycleRule gcsRule1 = toGcsApi(wsmRule1);
    // Direct equality comparison with LifecycleAction and LifecycleCondition
    // types doesn't work for some reason. Hence, comparing fields.
    assertEquals(
        GCS_LIFECYCLE_RULE_1.getAction().getActionType(), gcsRule1.getAction().getActionType());
    assertEquals(GCS_LIFECYCLE_RULE_1.getCondition().getAge(), gcsRule1.getCondition().getAge());
    assertEquals(
        GCS_LIFECYCLE_RULE_1.getCondition().getCreatedBefore(),
        gcsRule1.getCondition().getCreatedBefore());
    assertEquals(
        GCS_LIFECYCLE_RULE_1.getCondition().getMatchesStorageClass(),
        gcsRule1.getCondition().getMatchesStorageClass());
    assertEquals(
        GCS_LIFECYCLE_RULE_1.getCondition().getNumberOfNewerVersions(),
        gcsRule1.getCondition().getNumberOfNewerVersions());
    assertEquals(
        GCS_LIFECYCLE_RULE_1.getCondition().getIsLive(), gcsRule1.getCondition().getIsLive());
  }

  @Test
  public void testLifecycleActionConversions() {
    final ApiGcpGcsBucketLifecycleRuleAction wsmDeleteAction = toWsmApi(GCS_DELETE_ACTION);
    assertEquals(ApiGcpGcsBucketLifecycleRuleActionType.DELETE, wsmDeleteAction.getType());
    assertNull(wsmDeleteAction.getStorageClass());

    final LifecycleAction gcsDeleteAction = toGcsApi(wsmDeleteAction);
    assertEquals(DeleteLifecycleAction.TYPE, gcsDeleteAction.getActionType());

    final ApiGcpGcsBucketLifecycleRuleAction wsmSetStorageClassAction =
        toWsmApi(GCS_SET_STORAGE_CLASS_ACTION);
    assertEquals(
        ApiGcpGcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS,
        wsmSetStorageClassAction.getType());
    assertEquals(
        ApiGcpGcsBucketDefaultStorageClass.STANDARD, wsmSetStorageClassAction.getStorageClass());
  }

  @Test
  public void testLifecycleRuleActionTypeConversions() {
    assertEquals(
        ApiGcpGcsBucketLifecycleRuleActionType.DELETE, toWsmApi(DeleteLifecycleAction.TYPE));
    assertEquals(
        ApiGcpGcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS,
        toWsmApi(SetStorageClassLifecycleAction.TYPE));
  }

  @Test
  public void testGetStorageClass() {
    final Optional<StorageClass> deleteStorageClass = getStorageClass(GCS_DELETE_ACTION);
    assertTrue(deleteStorageClass.isEmpty());

    final Optional<StorageClass> setStorageClassStorageClass =
        getStorageClass(GCS_SET_STORAGE_CLASS_ACTION);
    assertTrue(setStorageClassStorageClass.isPresent());
    assertEquals(StorageClass.STANDARD, setStorageClassStorageClass.get());
  }

  @Test
  public void testLifecycleConditionConversions() {
    LifecycleCondition lifecycleCondition1 = toGcsApi(WSM_LIFECYCLE_RULE_CONDITION_1);
    assertEquals(WSM_LIFECYCLE_RULE_CONDITION_1.getAge(), lifecycleCondition1.getAge());
    assertEquals(
        toDateTime(WSM_LIFECYCLE_RULE_CONDITION_1.getCreatedBefore()),
        lifecycleCondition1.getCreatedBefore());
    assertEquals(WSM_LIFECYCLE_RULE_CONDITION_1, toWsmApi(lifecycleCondition1));

    ApiGcpGcsBucketLifecycleRuleCondition wsmCondition = toWsmApi(GCS_LIFECYCLE_CONDITION_1);
    assertEquals(42, wsmCondition.getAge());
    assertNull(wsmCondition.getMatchesStorageClass()); // TODO: double check this
    assertFalse(wsmCondition.isLive());
    assertEquals(2, wsmCondition.getNumNewerVersions());
  }

  @Test
  public void testToDateTime() {
    assertNull(toDateTime(null));

    final DateTime dateTime1 = toDateTime(OFFSET_DATE_TIME_1);
    assertEquals(60, dateTime1.getTimeZoneShift());
    assertEquals(OFFSET_DATE_TIME_1.toInstant().toEpochMilli(), dateTime1.getValue());

    final DateTime dateTime2 = toDateTime(OFFSET_DATE_TIME_2);
    assertEquals(Duration.ofHours(-5).toMinutes(), dateTime2.getTimeZoneShift());
    assertEquals(OFFSET_DATE_TIME_2.toInstant().toEpochMilli(), dateTime2.getValue());
  }

  @Test
  public void testToOffsetDateTime() {
    assertNull(toOffsetDateTime(null));

    final OffsetDateTime offsetDateTime1 = toOffsetDateTime(DATE_TIME_1);
    assertEquals(DATE_TIME_1.getValue(), offsetDateTime1.toInstant().toEpochMilli());
    assertEquals(ZoneOffset.UTC, offsetDateTime1.getOffset());

    final OffsetDateTime offsetDateTime2 = toOffsetDateTime(DATE_TIME_2);
    assertEquals(DATE_TIME_2.getValue(), offsetDateTime2.toInstant().toEpochMilli());
    assertEquals(ZoneOffset.ofHours(-8), offsetDateTime2.getOffset());
  }

  @Test
  public void testRoundTrip() {
    final OffsetDateTime offsetDateTime1 = toOffsetDateTime(toDateTime(OFFSET_DATE_TIME_1));
    assertEquals(OFFSET_DATE_TIME_1, offsetDateTime1);

    final DateTime dateTime2 = toDateTime(toOffsetDateTime(DATE_TIME_2));
    assertEquals(DATE_TIME_2, dateTime2);
  }
}
