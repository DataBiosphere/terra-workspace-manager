package bio.terra.workspace.common.fixtures;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.DATE_TIME_1;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.DATE_TIME_2;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.DEFAULT_RESOURCE_REGION;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.OFFSET_DATE_TIME_1;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.OFFSET_DATE_TIME_2;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.RESOURCE_DESCRIPTION;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.RESOURCE_ID;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.RESOURCE_NAME;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.WORKSPACE_ID;

import bio.terra.stairway.ShortUUID;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceVmImage;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcpGceInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGceUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycle;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRule;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleAction;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleActionType;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleCondition;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketUpdateParameters;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstants;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance.ControlledGceInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.google.api.services.bigquery.model.Dataset;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.google.cloud.storage.StorageClass;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/** A series of static objects useful for testing controlled resources. */
public class ControlledGcpResourceFixtures {

  public static final String DEFAULT_RESOURCE_ZONE = "us-east1-b";

  public static final ControlledResourceFields DEFAULT_GCP_CONTROLLED_RESOURCE_FIELDS =
      ControlledResourceFields.builder()
          .workspaceUuid(WORKSPACE_ID)
          .resourceId(RESOURCE_ID)
          .name(RESOURCE_NAME)
          .description(RESOURCE_DESCRIPTION)
          .cloningInstructions(CloningInstructions.COPY_RESOURCE)
          .resourceLineage(null)
          .properties(Map.of())
          .createdByEmail(DEFAULT_USER_EMAIL)
          .createdDate(null)
          .lastUpdatedByEmail(null)
          .lastUpdatedDate(null)
          .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
          .assignedUser(null)
          .managedBy(ManagedByType.MANAGED_BY_USER)
          .privateResourceState(PrivateResourceState.NOT_APPLICABLE)
          .applicationId(null)
          .region(DEFAULT_RESOURCE_REGION)
          .build();

  // GCS Bucket

  public static final ApiGcpGcsBucketLifecycleRuleCondition WSM_LIFECYCLE_RULE_CONDITION_1 =
      new ApiGcpGcsBucketLifecycleRuleCondition()
          .age(31)
          .createdBefore(OFFSET_DATE_TIME_2) // need date part only
          .numNewerVersions(3)
          .live(true)
          .matchesStorageClass(
              ImmutableList.of(
                  ApiGcpGcsBucketDefaultStorageClass.ARCHIVE,
                  ApiGcpGcsBucketDefaultStorageClass.STANDARD));
  public static final ApiGcpGcsBucketLifecycleRuleCondition WSM_LIFECYCLE_RULE_CONDITION_2 =
      new ApiGcpGcsBucketLifecycleRuleCondition()
          .age(15)
          .createdBefore(OFFSET_DATE_TIME_1)
          .numNewerVersions(5)
          .live(true)
          .matchesStorageClass(
              Collections.singletonList(ApiGcpGcsBucketDefaultStorageClass.ARCHIVE));
  public static final ApiGcpGcsBucketLifecycleRuleCondition WSM_LIFECYCLE_RULE_CONDITION_3 =
      new ApiGcpGcsBucketLifecycleRuleCondition()
          .age(45)
          .createdBefore(OFFSET_DATE_TIME_2)
          .numNewerVersions(1)
          .live(true)
          .matchesStorageClass(
              Collections.singletonList(ApiGcpGcsBucketDefaultStorageClass.STANDARD));

  // leave a couple of things unspecified
  public static final LifecycleCondition GCS_LIFECYCLE_CONDITION_1 =
      LifecycleCondition.newBuilder()
          .setAge(42)
          .setIsLive(false)
          .setNumberOfNewerVersions(2)
          .setDaysSinceNoncurrentTime(5)
          .setNoncurrentTimeBefore(DATE_TIME_1)
          .setCustomTimeBefore(DATE_TIME_2)
          .setDaysSinceCustomTime(100)
          .build();
  public static final LifecycleCondition GCS_LIFECYCLE_CONDITION_2 =
      LifecycleCondition.newBuilder()
          .setAge(30)
          .setIsLive(true)
          .setCreatedBefore(DATE_TIME_2)
          .setMatchesStorageClass(ImmutableList.of(StorageClass.ARCHIVE, StorageClass.COLDLINE))
          .build();

  public static final LifecycleAction GCS_DELETE_ACTION = LifecycleAction.newDeleteAction();
  public static final LifecycleAction GCS_SET_STORAGE_CLASS_ACTION =
      LifecycleAction.newSetStorageClassAction(StorageClass.STANDARD);

  public static final LifecycleRule GCS_LIFECYCLE_RULE_1 =
      new LifecycleRule(GCS_DELETE_ACTION, GCS_LIFECYCLE_CONDITION_1);
  public static final LifecycleRule GCS_LIFECYCLE_RULE_2 =
      new LifecycleRule(GCS_SET_STORAGE_CLASS_ACTION, GCS_LIFECYCLE_CONDITION_2);

  public static final BucketInfo GCS_BUCKET_INFO_1 =
      BucketInfo.newBuilder("my-bucket")
          .setStorageClass(StorageClass.STANDARD)
          .setLifecycleRules(ImmutableList.of(GCS_LIFECYCLE_RULE_1, GCS_LIFECYCLE_RULE_2))
          .build();

  public static final ApiGcpGcsBucketLifecycleRule WSM_LIFECYCLE_RULE_1 =
      new ApiGcpGcsBucketLifecycleRule()
          .action(
              new ApiGcpGcsBucketLifecycleRuleAction()
                  .type(ApiGcpGcsBucketLifecycleRuleActionType.DELETE))
          .condition(WSM_LIFECYCLE_RULE_CONDITION_1);
  public static final ApiGcpGcsBucketLifecycleRule WSM_LIFECYCLE_RULE_2 =
      new ApiGcpGcsBucketLifecycleRule()
          .action(
              new ApiGcpGcsBucketLifecycleRuleAction()
                  .type(ApiGcpGcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS)
                  .storageClass(ApiGcpGcsBucketDefaultStorageClass.NEARLINE))
          .condition(WSM_LIFECYCLE_RULE_CONDITION_2);
  public static final ApiGcpGcsBucketLifecycleRule WSM_LIFECYCLE_RULE_3 =
      new ApiGcpGcsBucketLifecycleRule()
          .action(
              new ApiGcpGcsBucketLifecycleRuleAction()
                  .type(ApiGcpGcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS)
                  .storageClass(ApiGcpGcsBucketDefaultStorageClass.COLDLINE))
          .condition(WSM_LIFECYCLE_RULE_CONDITION_3);

  public static final ApiGcpGcsBucketLifecycleRule LIFECYCLE_RULE_1 =
      new ApiGcpGcsBucketLifecycleRule()
          .action(
              new ApiGcpGcsBucketLifecycleRuleAction()
                  .type(
                      ApiGcpGcsBucketLifecycleRuleActionType
                          .DELETE)) // no storage class required for delete actions
          .condition(
              new ApiGcpGcsBucketLifecycleRuleCondition()
                  .age(64)
                  .live(true)
                  .addMatchesStorageClassItem(ApiGcpGcsBucketDefaultStorageClass.ARCHIVE)
                  .numNewerVersions(2));
  public static final ApiGcpGcsBucketLifecycleRule LIFECYCLE_RULE_2 =
      new ApiGcpGcsBucketLifecycleRule()
          .action(
              new ApiGcpGcsBucketLifecycleRuleAction()
                  .storageClass(ApiGcpGcsBucketDefaultStorageClass.NEARLINE)
                  .type(ApiGcpGcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS))
          .condition(
              new ApiGcpGcsBucketLifecycleRuleCondition()
                  .createdBefore(OffsetDateTime.parse("2007-01-03T00:00:00.00Z"))
                  .addMatchesStorageClassItem(ApiGcpGcsBucketDefaultStorageClass.STANDARD));
  // list must not be immutable if deserialization is to work
  public static final List<ApiGcpGcsBucketLifecycleRule> LIFECYCLE_RULES =
      List.of(LIFECYCLE_RULE_1, LIFECYCLE_RULE_2);

  public static final ApiGcpGcsBucketUpdateParameters BUCKET_UPDATE_PARAMETERS_1 =
      new ApiGcpGcsBucketUpdateParameters()
          .defaultStorageClass(ApiGcpGcsBucketDefaultStorageClass.STANDARD)
          .lifecycle(
              new ApiGcpGcsBucketLifecycle()
                  .rules(ImmutableList.of(WSM_LIFECYCLE_RULE_1, WSM_LIFECYCLE_RULE_2)));
  public static final ApiGcpGcsBucketUpdateParameters BUCKET_UPDATE_PARAMETERS_2 =
      new ApiGcpGcsBucketUpdateParameters()
          .defaultStorageClass(ApiGcpGcsBucketDefaultStorageClass.NEARLINE)
          .lifecycle(new ApiGcpGcsBucketLifecycle().rules(ImmutableList.of(WSM_LIFECYCLE_RULE_3)));
  public static final ApiGcpGcsBucketUpdateParameters BUCKET_UPDATE_PARAMETERS_EMPTY =
      new ApiGcpGcsBucketUpdateParameters();

  public static final String BUCKET_NAME_PREFIX = "my-bucket";

  public static final ApiGcpGcsBucketCreationParameters GOOGLE_BUCKET_CREATION_PARAMETERS_MINIMAL =
      new ApiGcpGcsBucketCreationParameters()
          .name(TestUtils.appendRandomNumber(BUCKET_NAME_PREFIX))
          .location(GcpResourceConstants.DEFAULT_REGION);

  public static String uniqueBucketName() {
    return TestUtils.appendRandomNumber(BUCKET_NAME_PREFIX);
  }

  /** Returns a {@link ControlledGcsBucketResource.Builder} that is ready to be built. */
  public static ControlledGcsBucketResource.Builder makeDefaultControlledGcsBucketBuilder(
      @Nullable UUID workspaceUuid) {
    return new ControlledGcsBucketResource.Builder()
        .common(ControlledResourceFixtures.makeDefaultControlledResourceFields(workspaceUuid))
        .bucketName(uniqueBucketName());
  }

  /** Construct a parameter object with a unique bucket name to avoid unintended clashes. */
  public static ApiGcpGcsBucketCreationParameters getGoogleBucketCreationParameters() {
    return new ApiGcpGcsBucketCreationParameters()
        .name(uniqueBucketName())
        .defaultStorageClass(ApiGcpGcsBucketDefaultStorageClass.STANDARD)
        .location(GcpResourceConstants.DEFAULT_REGION)
        .lifecycle(new ApiGcpGcsBucketLifecycle().rules(LIFECYCLE_RULES));
  }

  public static ApiGcpGcsBucketCreationParameters defaultGcsBucketCreationParameters() {
    return new ApiGcpGcsBucketCreationParameters().name(uniqueBucketName());
  }

  public static ControlledGcsBucketResource getBucketResource(String bucketName) {
    return ControlledGcsBucketResource.builder()
        .common(DEFAULT_GCP_CONTROLLED_RESOURCE_FIELDS)
        .bucketName(bucketName)
        .build();
  }

  // Big Query

  public static final Long DEFAULT_CREATED_BIG_QUERY_TABLE_LIFETIME = 5900L;
  public static final Long DEFAULT_CREATED_BIG_QUERY_PARTITION_LIFETIME = 5901L;

  public static final ApiGcpBigQueryDatasetUpdateParameters BQ_DATASET_UPDATE_PARAMETERS_NEW =
      new ApiGcpBigQueryDatasetUpdateParameters()
          .defaultTableLifetime(3600L)
          .defaultPartitionLifetime(3601L);
  public static final ApiGcpBigQueryDatasetUpdateParameters BQ_DATASET_UPDATE_PARAMETERS_PREV =
      new ApiGcpBigQueryDatasetUpdateParameters()
          .defaultTableLifetime(4800L)
          .defaultPartitionLifetime(4801L);

  public static final Dataset BQ_DATASET_WITH_EXPIRATION =
      new Dataset()
          .setDefaultTableExpirationMs(DEFAULT_CREATED_BIG_QUERY_TABLE_LIFETIME * 1000)
          .setDefaultPartitionExpirationMs(DEFAULT_CREATED_BIG_QUERY_PARTITION_LIFETIME * 1000);
  public static final Dataset BQ_DATASET_WITHOUT_EXPIRATION = new Dataset();

  public static String uniqueDatasetId() {
    return "my_test_dataset_" + ShortUUID.get().replace("-", "_");
  }

  /** Construct a creation parameter object with a unique data set name. */
  public static ApiGcpBigQueryDatasetCreationParameters getGcpBigQueryDatasetCreationParameters() {
    return new ApiGcpBigQueryDatasetCreationParameters()
        .datasetId(uniqueDatasetId())
        .defaultTableLifetime(DEFAULT_CREATED_BIG_QUERY_TABLE_LIFETIME)
        .defaultPartitionLifetime(DEFAULT_CREATED_BIG_QUERY_PARTITION_LIFETIME)
        .location(DEFAULT_RESOURCE_REGION);
  }

  public static ApiGcpBigQueryDatasetCreationParameters defaultBigQueryDatasetCreationParameters() {
    return new ApiGcpBigQueryDatasetCreationParameters().datasetId(uniqueDatasetId());
  }

  /**
   * Make a bigquery builder with defaults filled in NOTE: when using this in a connected test, you
   * MUST overwrite the project id. "my_project" won't work.
   *
   * @return resource builder
   */
  public static ControlledBigQueryDatasetResource.Builder makeDefaultControlledBqDatasetBuilder(
      @Nullable UUID workspaceUuid) {
    return new ControlledBigQueryDatasetResource.Builder()
        .common(ControlledResourceFixtures.makeDefaultControlledResourceFields(workspaceUuid))
        .projectId("my_project")
        .datasetName(uniqueDatasetId())
        .defaultTableLifetime(DEFAULT_CREATED_BIG_QUERY_TABLE_LIFETIME)
        .defaultPartitionLifetime(DEFAULT_CREATED_BIG_QUERY_PARTITION_LIFETIME);
  }

  // AI Notebook

  public static final ApiGcpAiNotebookUpdateParameters AI_NOTEBOOK_PREV_PARAMETERS =
      new ApiGcpAiNotebookUpdateParameters()
          .metadata(ImmutableMap.of("sky", "blue", "rose", "red", "foo", "bar2", "count", "0"));
  public static final ApiGcpAiNotebookUpdateParameters AI_NOTEBOOK_UPDATE_PARAMETERS =
      new ApiGcpAiNotebookUpdateParameters().metadata(ImmutableMap.of("foo", "bar", "count", "3"));

  public static ApiGcpAiNotebookInstanceCreationParameters defaultNotebookCreationParameters() {
    return new ApiGcpAiNotebookInstanceCreationParameters()
        .instanceId(TestUtils.appendRandomNumber("default-instance-id"))
        .location(DEFAULT_RESOURCE_ZONE)
        .machineType("e2-standard-2")
        .vmImage(
            new ApiGcpAiNotebookInstanceVmImage()
                .projectId("deeplearning-platform-release")
                .imageFamily("r-latest-cpu-experimental"));
  }

  /**
   * Returns a {@link ControlledAiNotebookInstanceResource.Builder} that is ready to be built.
   *
   * <p>Tests should not rely on any particular value for the fields returned by this function and
   * instead override the values that they care about.
   */
  public static ControlledResourceFields.Builder makeNotebookCommonFieldsBuilder() {
    return ControlledResourceFields.builder()
        .workspaceUuid(UUID.randomUUID())
        .resourceId(UUID.randomUUID())
        .name(TestUtils.appendRandomNumber("my-instance"))
        .description("my notebook description")
        .cloningInstructions(CloningInstructions.COPY_NOTHING)
        .assignedUser("myusername@mydomain.mine")
        .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
        .managedBy(ManagedByType.MANAGED_BY_USER)
        .createdByEmail(DEFAULT_USER_EMAIL)
        .region(DEFAULT_RESOURCE_REGION);
  }

  public static ControlledAiNotebookInstanceResource.Builder
      makeDefaultAiNotebookInstanceBuilder() {
    return ControlledAiNotebookInstanceResource.builder()
        .common(makeNotebookCommonFieldsBuilder().build())
        .instanceId(TestUtils.appendRandomNumber("my-cloud-id"))
        .location(DEFAULT_RESOURCE_ZONE)
        .projectId("my-project-id");
  }

  public static ControlledAiNotebookInstanceResource.Builder makeDefaultAiNotebookInstanceBuilder(
      UUID workspaceId) {
    return ControlledAiNotebookInstanceResource.builder()
        .common(makeNotebookCommonFieldsBuilder().workspaceUuid(workspaceId).build())
        .instanceId(TestUtils.appendRandomNumber("my-cloud-id"))
        .location(DEFAULT_RESOURCE_ZONE)
        .projectId("my-project-id");
  }

  // GCE instance

  public static final ApiGcpGceUpdateParameters GCE_INSTANCE_PREV_PARAMETERS =
      new ApiGcpGceUpdateParameters()
          .metadata(ImmutableMap.of("sky", "blue", "rose", "red", "foo", "bar2", "count", "0"));

  public static final ApiGcpGceUpdateParameters GCE_INSTANCE_UPDATE_PARAMETERS =
      new ApiGcpGceUpdateParameters().metadata(ImmutableMap.of("foo", "bar", "count", "3"));

  public static ApiGcpGceInstanceCreationParameters defaultGceInstanceCreationParameters() {
    return new ApiGcpGceInstanceCreationParameters()
        .instanceId(TestUtils.appendRandomNumber("default-instance-id"))
        .zone(DEFAULT_RESOURCE_ZONE)
        .machineType("n1-standard-1")
        .vmImage("projects/debian-cloud/global/images/family/debian-11");
  }

  /**
   * Returns a {@link ControlledGceInstanceResource.Builder} that is ready to be built.
   *
   * <p>Tests should not rely on any particular value for the fields returned by this function and
   * instead override the values that they care about.
   */
  public static ControlledResourceFields.Builder makeGceInstanceCommonFieldsBuilder() {
    return ControlledResourceFields.builder()
        .workspaceUuid(UUID.randomUUID())
        .resourceId(UUID.randomUUID())
        .name(TestUtils.appendRandomNumber("my-instance"))
        .description("my description")
        .cloningInstructions(CloningInstructions.COPY_NOTHING)
        .assignedUser("myusername@mydomain.mine")
        .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
        .managedBy(ManagedByType.MANAGED_BY_USER)
        .createdByEmail(DEFAULT_USER_EMAIL)
        .region(DEFAULT_RESOURCE_REGION);
  }

  public static ControlledGceInstanceResource.Builder makeDefaultGceInstance() {
    return ControlledGceInstanceResource.builder()
        .common(makeGceInstanceCommonFieldsBuilder().build())
        .instanceId(TestUtils.appendRandomNumber("my-cloud-id"))
        .zone(DEFAULT_RESOURCE_ZONE)
        .projectId("my-project-id");
  }

  public static ControlledGceInstanceResource.Builder makeDefaultGceInstance(UUID workspaceId) {
    return ControlledGceInstanceResource.builder()
        .common(makeGceInstanceCommonFieldsBuilder().workspaceUuid(workspaceId).build())
        .instanceId(TestUtils.appendRandomNumber("my-cloud-id"))
        .zone(DEFAULT_RESOURCE_ZONE)
        .projectId("my-project-id");
  }
}
