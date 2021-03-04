package bio.terra.workspace.common.fixtures;

import static bio.terra.workspace.service.resource.controlled.ControlledAccessType.USER_PRIVATE;
import static bio.terra.workspace.service.resource.controlled.ControlledAccessType.USER_SHARED;

import bio.terra.workspace.generated.model.GoogleBucketCreationParameters;
import bio.terra.workspace.generated.model.GoogleBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.GoogleBucketLifecycle;
import bio.terra.workspace.generated.model.GoogleBucketLifecycleRule;
import bio.terra.workspace.generated.model.GoogleBucketLifecycleRuleAction;
import bio.terra.workspace.generated.model.GoogleBucketLifecycleRuleActionType;
import bio.terra.workspace.generated.model.GoogleBucketLifecycleRuleCondition;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
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
  public static final GoogleBucketLifecycleRule LIFECYCLE_RULE_1 =
      new GoogleBucketLifecycleRule()
          .action(
              new GoogleBucketLifecycleRuleAction()
                  .type(
                      GoogleBucketLifecycleRuleActionType
                          .DELETE)) // no storage class require for delete actions
          .condition(
              new GoogleBucketLifecycleRuleCondition()
                  .age(64)
                  .live(true)
                  .addMatchesStorageClassItem(GoogleBucketDefaultStorageClass.ARCHIVE)
                  .numNewerVersions(2));

  public static final GoogleBucketLifecycleRule LIFECYCLE_RULE_2 =
      new GoogleBucketLifecycleRule()
          .action(
              new GoogleBucketLifecycleRuleAction()
                  .storageClass(GoogleBucketDefaultStorageClass.NEARLINE)
                  .type(GoogleBucketLifecycleRuleActionType.SET_STORAGE_CLASS))
          .condition(
              new GoogleBucketLifecycleRuleCondition()
                  .createdBefore(LocalDate.of(2017, 2, 18))
                  .addMatchesStorageClassItem(GoogleBucketDefaultStorageClass.STANDARD));
  // list must not be immutable if deserialization is to work
  public static final List<GoogleBucketLifecycleRule> LIFECYCLE_RULES =
      new ArrayList<>(List.of(LIFECYCLE_RULE_1, LIFECYCLE_RULE_2));
  public static final String BUCKET_NAME = "my-bucket";
  public static final String BUCKET_LOCATION = "US-CENTRAL1";
  public static final GoogleBucketCreationParameters GOOGLE_BUCKET_CREATION_PARAMETERS =
      new GoogleBucketCreationParameters()
          .name(BUCKET_NAME)
          .location(BUCKET_LOCATION)
          .defaultStorageClass(GoogleBucketDefaultStorageClass.STANDARD)
          .lifecycle(new GoogleBucketLifecycle().rules(LIFECYCLE_RULES));

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
          USER_SHARED,
          BUCKET_NAME);

  /** Flawed resource missing owner email. */
  public static ControlledGcsBucketResource makeControlledGcsBucketResource() {
    UUID resourceId = UUID.randomUUID();
    return new ControlledGcsBucketResource(
        WORKSPACE_ID,
        resourceId,
        "testgcs-" + resourceId,
        RESOURCE_DESCRIPTION,
        CLONING_INSTRUCTIONS,
        OWNER_EMAIL,
        USER_PRIVATE,
        BUCKET_NAME);
  }

  private ControlledResourceFixtures() {}
}
