package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.generated.model.ApiGcsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiGcsBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.ApiGcsBucketLifecycle;
import bio.terra.workspace.generated.model.ApiGcsBucketLifecycleRule;
import bio.terra.workspace.generated.model.ApiGcsBucketLifecycleRuleAction;
import bio.terra.workspace.generated.model.ApiGcsBucketLifecycleRuleActionType;
import bio.terra.workspace.generated.model.ApiGcsBucketLifecycleRuleCondition;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import java.time.LocalDate;
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
  public static final ApiGcsBucketLifecycleRule LIFECYCLE_RULE_1 =
      new ApiGcsBucketLifecycleRule()
          .action(
              new ApiGcsBucketLifecycleRuleAction()
                  .type(
                      ApiGcsBucketLifecycleRuleActionType
                          .DELETE)) // no storage class require for delete actions
          .condition(
              new ApiGcsBucketLifecycleRuleCondition()
                  .age(64)
                  .live(true)
                  .addMatchesStorageClassItem(ApiGcsBucketDefaultStorageClass.ARCHIVE)
                  .numNewerVersions(2));

  public static final ApiGcsBucketLifecycleRule LIFECYCLE_RULE_2 =
      new ApiGcsBucketLifecycleRule()
          .action(
              new ApiGcsBucketLifecycleRuleAction()
                  .storageClass(ApiGcsBucketDefaultStorageClass.NEARLINE)
                  .type(ApiGcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS))
          .condition(
              new ApiGcsBucketLifecycleRuleCondition()
                  .createdBefore(LocalDate.of(2017, 2, 18))
                  .addMatchesStorageClassItem(ApiGcsBucketDefaultStorageClass.STANDARD));
  // list must not be immutable if deserialization is to work
  static final List<ApiGcsBucketLifecycleRule> LIFECYCLE_RULES =
      new ArrayList<>(List.of(LIFECYCLE_RULE_1, LIFECYCLE_RULE_2));
  public static final String BUCKET_NAME = "my-bucket";
  public static final String BUCKET_LOCATION = "US-CENTRAL1";
  public static final ApiGcsBucketCreationParameters GOOGLE_BUCKET_CREATION_PARAMETERS =
      new ApiGcsBucketCreationParameters()
          .name(BUCKET_NAME)
          .location(BUCKET_LOCATION)
          .defaultStorageClass(ApiGcsBucketDefaultStorageClass.STANDARD)
          .lifecycle(new ApiGcsBucketLifecycle().rules(LIFECYCLE_RULES));

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

  public static ControlledGcsBucketResource makeControlledGcsBucketResource(UUID workspaceId) {
    UUID resourceId = UUID.randomUUID();
    return new ControlledGcsBucketResource(
        workspaceId,
        resourceId,
        "testgcs-" + resourceId,
        RESOURCE_DESCRIPTION,
        CLONING_INSTRUCTIONS,
        null,
        AccessScopeType.ACCESS_SCOPE_SHARED,
        ManagedByType.MANAGED_BY_USER,
        BUCKET_NAME);
  }
}
