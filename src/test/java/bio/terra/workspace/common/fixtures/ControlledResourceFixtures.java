package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycle;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRule;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleAction;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleActionType;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleCondition;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
  public static final String BUCKET_NAME = "my-bucket";
  public static final String BUCKET_LOCATION = "US-CENTRAL1";
  public static final ApiGcpGcsBucketCreationParameters GOOGLE_BUCKET_CREATION_PARAMETERS =
      new ApiGcpGcsBucketCreationParameters()
          .name(BUCKET_NAME)
          .location(BUCKET_LOCATION)
          .defaultStorageClass(ApiGcpGcsBucketDefaultStorageClass.STANDARD)
          .lifecycle(new ApiGcpGcsBucketLifecycle().rules(LIFECYCLE_RULES));

  public static final String RESOURCE_NAME = "my_first_bucket";

  public static final String RESOURCE_DESCRIPTION =
      "A bucket that had beer in it, briefly. \uD83C\uDF7B";
  public static final CloningInstructions CLONING_INSTRUCTIONS = CloningInstructions.COPY_REFERENCE;
  public static final ControlledGcsBucketResource BUCKET_RESOURCE =
      new ControlledGcsBucketResource(
          WORKSPACE_ID,
          RESOURCE_ID,
          RESOURCE_NAME,
          RESOURCE_DESCRIPTION,
          CLONING_INSTRUCTIONS,
          OWNER_EMAIL,
          AccessScopeType.ACCESS_SCOPE_PRIVATE,
          ManagedByType.MANAGED_BY_USER,
          BUCKET_NAME);

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
        .bucketName(BUCKET_NAME);
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
        .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
        .managedBy(ManagedByType.MANAGED_BY_USER)
        .instanceId("my-instance-id")
        .location("us-east1-b");
  }
}
