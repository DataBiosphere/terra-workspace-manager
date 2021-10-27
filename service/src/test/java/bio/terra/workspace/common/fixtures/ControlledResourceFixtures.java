package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.generated.model.ApiAzureDiskCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureIpCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceVmImage;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycle;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRule;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleAction;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleActionType;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleCondition;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketUpdateParameters;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.google.api.client.util.DateTime;
import com.google.api.services.bigquery.model.Dataset;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.google.cloud.storage.StorageClass;
import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** A series of static objects useful for testing controlled resources. */
public class ControlledResourceFixtures {

  public static final UUID WORKSPACE_ID = UUID.fromString("00000000-fcf0-4981-bb96-6b8dd634e7c0");
  public static final UUID RESOURCE_ID = UUID.fromString("11111111-fcf0-4981-bb96-6b8dd634e7c0");
  public static final UUID DATA_REFERENCE_ID =
      UUID.fromString("33333333-fcf0-4981-bb96-6b8dd634e7c0");
  public static final String OWNER_EMAIL = "jay@all-the-bits-thats-fit-to-blit.dev";
  public static final ApiGcpGcsBucketLifecycleRule LIFECYCLE_RULE_1 =
      new ApiGcpGcsBucketLifecycleRule()
          .action(
              new ApiGcpGcsBucketLifecycleRuleAction()
                  .type(
                      ApiGcpGcsBucketLifecycleRuleActionType
                          .DELETE)) // no storage class require for delete actions
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
  static final List<ApiGcpGcsBucketLifecycleRule> LIFECYCLE_RULES =
      new ArrayList<>(List.of(LIFECYCLE_RULE_1, LIFECYCLE_RULE_2));
  public static final String BUCKET_NAME_PREFIX = "my-bucket";
  public static final String RESOURCE_LOCATION = "US-CENTRAL1";
  public static final String AZURE_NAME_PREFIX = "azure";
  public static final String AZURE_IP_NAME_PREFIX = "ip";
  public static final String AZURE_DISK_NAME_PREFIX = "disk";
  public static final String AZURE_VM_NAME_PREFIX = "vm";

  public static final ApiGcpGcsBucketCreationParameters GOOGLE_BUCKET_CREATION_PARAMETERS_MINIMAL =
      new ApiGcpGcsBucketCreationParameters()
          .name(uniqueName(BUCKET_NAME_PREFIX))
          .location(RESOURCE_LOCATION);

  /** Construct a parameter object with a unique bucket name to avoid unintended clashes. */
  public static ApiGcpGcsBucketCreationParameters getGoogleBucketCreationParameters() {
    return new ApiGcpGcsBucketCreationParameters()
        .name(uniqueBucketName())
        .location(RESOURCE_LOCATION)
        .defaultStorageClass(ApiGcpGcsBucketDefaultStorageClass.STANDARD)
        .lifecycle(new ApiGcpGcsBucketLifecycle().rules(LIFECYCLE_RULES));
  }

  /** Construct a parameter object with a unique ip name to avoid unintended clashes. */
  public static ApiAzureIpCreationParameters getAzureIpCreationParameters() {
    return new ApiAzureIpCreationParameters()
        .name(uniqueAzureName(AZURE_IP_NAME_PREFIX))
        .region("eastus");
  }

  /** Construct a parameter object with a unique disk name to avoid unintended clashes. */
  public static ApiAzureDiskCreationParameters getAzureDiskCreationParameters() {
    return new ApiAzureDiskCreationParameters()
        .name(uniqueAzureName(AZURE_DISK_NAME_PREFIX))
        .region("eastus")
        .size(50);
  }

  /** Construct a parameter object with a unique vm name to avoid unintended clashes. */
  public static ApiAzureVmCreationParameters getAzureVmCreationParameters() {
    return new ApiAzureVmCreationParameters()
        .name(uniqueAzureName(AZURE_VM_NAME_PREFIX))
        .ipId(UUID.randomUUID())
        .networkId(UUID.randomUUID())
        .diskId(UUID.randomUUID())
        .region("eastus");
  }

  public static String uniqueBucketName() {
    return uniqueName(BUCKET_NAME_PREFIX);
  }

  public static String uniqueAzureName(String resourcePrefix) {
    return uniqueName(AZURE_NAME_PREFIX + "-" + AZURE_NAME_PREFIX);
  }

  public static ApiGcpAiNotebookInstanceCreationParameters defaultNotebookCreationParameters() {
    return new ApiGcpAiNotebookInstanceCreationParameters()
        .instanceId("default-instance-id")
        .location("us-east1-b")
        .machineType("e2-standard-2")
        .vmImage(
            new ApiGcpAiNotebookInstanceVmImage()
                .projectId("deeplearning-platform-release")
                .imageFamily("r-latest-cpu-experimental"));
  }

  public static ApiGcpBigQueryDatasetCreationParameters defaultBigQueryDatasetCreationParameters() {
    return new ApiGcpBigQueryDatasetCreationParameters()
        .location(RESOURCE_LOCATION)
        .datasetId("test_dataset");
  }

  public static final String RESOURCE_NAME = "my_first_bucket";

  public static final String RESOURCE_DESCRIPTION =
      "A bucket that had beer in it, briefly. \uD83C\uDF7B";
  public static final CloningInstructions CLONING_INSTRUCTIONS = CloningInstructions.COPY_REFERENCE;

  public static ControlledGcsBucketResource getBucketResource(String bucketName) {
    return new ControlledGcsBucketResource(
        WORKSPACE_ID,
        RESOURCE_ID,
        RESOURCE_NAME,
        RESOURCE_DESCRIPTION,
        CLONING_INSTRUCTIONS,
        OWNER_EMAIL,
        AccessScopeType.ACCESS_SCOPE_PRIVATE,
        ManagedByType.MANAGED_BY_USER,
        bucketName);
  }

  public static ControlledAzureIpResource getAzureIp(String ipName, String region) {
    return new ControlledAzureIpResource(
        WORKSPACE_ID,
        RESOURCE_ID,
        RESOURCE_NAME,
        RESOURCE_DESCRIPTION,
        CLONING_INSTRUCTIONS,
        OWNER_EMAIL,
        // TODO: these should be changed when we group the resources
        AccessScopeType.ACCESS_SCOPE_PRIVATE,
        ManagedByType.MANAGED_BY_USER,
        ipName,
        region);
  }

  public static ControlledAzureDiskResource getAzureDisk(String diskName, String region, int size) {
    return new ControlledAzureDiskResource(
        WORKSPACE_ID,
        RESOURCE_ID,
        RESOURCE_NAME,
        RESOURCE_DESCRIPTION,
        CLONING_INSTRUCTIONS,
        OWNER_EMAIL,
        // TODO: these should be changed when we group the resources
        AccessScopeType.ACCESS_SCOPE_PRIVATE,
        ManagedByType.MANAGED_BY_USER,
        diskName,
        region,
        size);
  }

  public static ControlledAzureVmResource getAzureVm(
      ApiAzureVmCreationParameters creationParameters) {
    return new ControlledAzureVmResource(
        WORKSPACE_ID,
        RESOURCE_ID,
        RESOURCE_NAME,
        RESOURCE_DESCRIPTION,
        CLONING_INSTRUCTIONS,
        OWNER_EMAIL,
        // TODO: these should be changed when we group the resources
        AccessScopeType.ACCESS_SCOPE_PRIVATE,
        ManagedByType.MANAGED_BY_USER,
        creationParameters.getName(),
        creationParameters.getRegion(),
        creationParameters.getIpId(),
        creationParameters.getNetworkId(),
        creationParameters.getDiskId());
  }

  private ControlledResourceFixtures() {}

  /**
   * Returns a {@link ControlledGcsBucketResource.Builder} that is ready to be built.
   *
   * <p>Tests should not rely on any particular value for the fields returned by this function and
   * instead override the values that they care about.
   */
  public static ControlledGcsBucketResource.Builder makeDefaultControlledGcsBucketResource() {
    UUID resourceId = UUID.randomUUID();
    return new ControlledGcsBucketResource.Builder()
        .workspaceId(UUID.randomUUID())
        .resourceId(resourceId)
        .name("testgcs-" + resourceId)
        .description(RESOURCE_DESCRIPTION)
        .cloningInstructions(CLONING_INSTRUCTIONS)
        .assignedUser(null)
        .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
        .managedBy(ManagedByType.MANAGED_BY_USER)
        .bucketName(uniqueBucketName());
  }

  /**
   * Returns a {@link ControlledBigQueryDatasetResource.Builder} that is ready to be built.
   *
   * <p>Tests should not rely on any particular value for the fields returned by this function and
   * instead override the values that they care about.
   */
  public static ControlledBigQueryDatasetResource.Builder
      makeDefaultControlledBigQueryDatasetResource() {
    UUID resourceId = UUID.randomUUID();
    return new ControlledBigQueryDatasetResource.Builder()
        .workspaceId(UUID.randomUUID())
        .resourceId(resourceId)
        .name(uniqueName("test_dataset").replace("-", "_"))
        .description("how much data could a dataset set if a dataset could set data?")
        .cloningInstructions(CLONING_INSTRUCTIONS)
        .assignedUser(null)
        .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
        .managedBy(ManagedByType.MANAGED_BY_USER)
        .datasetName("test_dataset");
  }

  public static final ApiGcpBigQueryDatasetUpdateParameters BQ_DATASET_UPDATE_PARAMETERS_NEW =
      new ApiGcpBigQueryDatasetUpdateParameters()
          .defaultTableLifetime(3600)
          .defaultPartitionLifetime(3601);
  public static final ApiGcpBigQueryDatasetUpdateParameters BQ_DATASET_UPDATE_PARAMETERS_PREV =
      new ApiGcpBigQueryDatasetUpdateParameters()
          .defaultTableLifetime(4800)
          .defaultPartitionLifetime(4801);
  public static final Dataset BQ_DATASET_WITH_EXPIRATION =
      new Dataset()
          .setDefaultTableExpirationMs(Long.valueOf(5900000))
          .setDefaultPartitionExpirationMs(Long.valueOf(5901000));
  public static final Dataset BQ_DATASET_WITHOUT_EXPIRATION = new Dataset();

  public static String uniqueName(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString();
  }

  /**
   * Returns a {@link ControlledAiNotebookInstanceResource.Builder} that is ready to be built.
   *
   * <p>Tests should not rely on any particular value for the fields returned by this function and
   * instead override the values that they care about.
   */
  public static ControlledAiNotebookInstanceResource.Builder makeDefaultAiNotebookInstance() {
    return ControlledAiNotebookInstanceResource.builder()
        .workspaceId(UUID.randomUUID())
        .resourceId(UUID.randomUUID())
        .name("my-notebook")
        .description("my notebook description")
        .cloningInstructions(CloningInstructions.COPY_NOTHING)
        .assignedUser(null)
        .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
        .managedBy(ManagedByType.MANAGED_BY_USER)
        .instanceId("my-instance-id")
        .location("us-east1-b");
  }

  public static final OffsetDateTime OFFSET_DATE_TIME_1 =
      OffsetDateTime.parse("2017-12-03T10:15:30+01:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  public static final OffsetDateTime OFFSET_DATE_TIME_2 =
      OffsetDateTime.parse("2017-12-03T10:15:30-05:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  public static final DateTime DATE_TIME_1 = DateTime.parseRfc3339("1985-04-12T23:20:50.52Z");
  public static final DateTime DATE_TIME_2 = DateTime.parseRfc3339("1996-12-19T16:39:57-08:00");

  public static final ApiGcpGcsBucketLifecycleRuleCondition WSM_LIFECYCLE_RULE_CONDITION_1 =
      new ApiGcpGcsBucketLifecycleRuleCondition()
          .age(31)
          .createdBefore(OFFSET_DATE_TIME_2)
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
}
