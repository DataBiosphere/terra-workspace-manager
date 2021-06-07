package scripts.utils;

import bio.terra.workspace.model.GcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.model.GcpGcsBucketLifecycle;
import bio.terra.workspace.model.GcpGcsBucketLifecycleRule;
import bio.terra.workspace.model.GcpGcsBucketLifecycleRuleAction;
import bio.terra.workspace.model.GcpGcsBucketLifecycleRuleActionType;
import bio.terra.workspace.model.GcpGcsBucketLifecycleRuleCondition;
import bio.terra.workspace.model.GcpGcsBucketUpdateParameters;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class GcsBucketTestFixtures {

  public static final GcpGcsBucketLifecycleRule LIFECYCLE_RULE_1 =
      new GcpGcsBucketLifecycleRule()
          .action(
              new GcpGcsBucketLifecycleRuleAction()
                  .type(
                      GcpGcsBucketLifecycleRuleActionType
                          .DELETE)) // no storage class required for delete actions
          .condition(
              new GcpGcsBucketLifecycleRuleCondition()
                  .age(64)
                  .live(true)
                  .addMatchesStorageClassItem(GcpGcsBucketDefaultStorageClass.ARCHIVE)
                  .numNewerVersions(2));
  private static final GcpGcsBucketLifecycleRule LIFECYCLE_RULE_2 =
      new GcpGcsBucketLifecycleRule()
          .action(
              new GcpGcsBucketLifecycleRuleAction()
                  .storageClass(GcpGcsBucketDefaultStorageClass.NEARLINE)
                  .type(GcpGcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS))
          .condition(
              new GcpGcsBucketLifecycleRuleCondition()
                  .createdBefore(OffsetDateTime.parse("2007-01-03T00:00:00.00Z"))
                  .addMatchesStorageClassItem(GcpGcsBucketDefaultStorageClass.STANDARD));
  // list must not be immutable if deserialization is to work
  public static final List<GcpGcsBucketLifecycleRule> LIFECYCLE_RULES =
      new ArrayList<>(List.of(LIFECYCLE_RULE_1, LIFECYCLE_RULE_2));
  public static final String RESOURCE_DESCRIPTION = "A huge bucket";
  public static final String UPDATED_RESOURCE_NAME = "new_resource_name";
  public static final String UPDATED_RESOURCE_NAME_2 = "another_resource_name";
  public static final String UPDATED_DESCRIPTION = "A bucket with a hole in it.";
  public static final String BUCKET_LOCATION = "US-CENTRAL1";
  public static final String BUCKET_PREFIX = "wsmtestbucket-";
  public static final String RESOURCE_PREFIX = "wsmtestresource-";
  public static final String GCS_BLOB_NAME = "wsmtestblob-name";
  public static final String GCS_BLOB_CONTENT = "This is the content of a text file.";
  public static final GcpGcsBucketUpdateParameters UPDATE_PARAMETERS_1 =
      new GcpGcsBucketUpdateParameters()
          .defaultStorageClass(GcpGcsBucketDefaultStorageClass.NEARLINE)
          .lifecycle(new GcpGcsBucketLifecycle()
              .addRulesItem(new GcpGcsBucketLifecycleRule()
                  .action(new GcpGcsBucketLifecycleRuleAction()
                      .type(GcpGcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS)
                      .storageClass(GcpGcsBucketDefaultStorageClass.ARCHIVE))
                  .condition(new GcpGcsBucketLifecycleRuleCondition()
                      .age(30)
                      .createdBefore(OffsetDateTime
                          .parse("1981-04-20T21:15:30-05:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                      .live(true)
                      .numNewerVersions(3)
                      .addMatchesStorageClassItem(GcpGcsBucketDefaultStorageClass.ARCHIVE))));
  public static final GcpGcsBucketUpdateParameters UPDATE_PARAMETERS_2 = new GcpGcsBucketUpdateParameters()
                                      .defaultStorageClass(GcpGcsBucketDefaultStorageClass.COLDLINE);
}
