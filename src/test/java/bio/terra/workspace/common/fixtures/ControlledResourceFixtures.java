package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.generated.model.GoogleBucketCreationParameters;
import bio.terra.workspace.generated.model.GoogleBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.GoogleBucketLifecycle;
import bio.terra.workspace.generated.model.GoogleBucketLifecycleRule;
import bio.terra.workspace.generated.model.GoogleBucketLifecycleRuleAction;
import bio.terra.workspace.generated.model.GoogleBucketLifecycleRuleActionType;
import bio.terra.workspace.generated.model.GoogleBucketLifecycleRuleCondition;
import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.resource.controlled.gcp.ControlledGcsBucketResource;
import com.google.common.collect.ImmutableList;
import java.time.LocalDate;
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
                  .storageClass(GoogleBucketDefaultStorageClass.COLDLINE)
                  .type(GoogleBucketLifecycleRuleActionType.DELETE))
          .condition(
              new GoogleBucketLifecycleRuleCondition()
                  .age(64)
                  .isLive(true)
                  .matchesStorageClass(ImmutableList.of(GoogleBucketDefaultStorageClass.ARCHIVE)));

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

  public static final GoogleBucketCreationParameters GOOGLE_BUCKET_CREATION_PARAMETERS =
      new GoogleBucketCreationParameters()
          .name("my-bucket")
          .location("US-CENTRAL1")
          .defaultStorageClass(GoogleBucketDefaultStorageClass.STANDARD)
          .lifecycle(
              new GoogleBucketLifecycle()
                  .rules(ImmutableList.of(LIFECYCLE_RULE_1, LIFECYCLE_RULE_2)));

  public static final String RESOURCE_NAME = "my_first_bucket";

  public static final String RESOURCE_DESCRIPTION =
      "A bucket that had beer in it, briefly. \uD83C\uDF7B";
  public static final CloningInstructions CLONING_INSTRUCTIONS = CloningInstructions.COPY_REFERENCE;
  public static final ControlledGcsBucketResource BUCKET_RESOURCE =
      new ControlledGcsBucketResource(
          RESOURCE_NAME,
          CLONING_INSTRUCTIONS,
          RESOURCE_DESCRIPTION,
          WORKSPACE_ID,
          OWNER_EMAIL,
          GOOGLE_BUCKET_CREATION_PARAMETERS);

  /** Flawed resource missing owner email. */
  public static final ControlledGcsBucketResource INVALID_BUCKET_RESOURCE =
      new ControlledGcsBucketResource(
          RESOURCE_NAME,
          CLONING_INSTRUCTIONS,
          RESOURCE_DESCRIPTION,
          WORKSPACE_ID,
          "",
          GOOGLE_BUCKET_CREATION_PARAMETERS);

  private ControlledResourceFixtures() {}
}
