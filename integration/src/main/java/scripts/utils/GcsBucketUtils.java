package scripts.utils;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceCommonFields;
import bio.terra.workspace.model.CreateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.model.CreateGcpGcsBucketReferenceRequestBody;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.DeleteControlledGcpGcsBucketRequest;
import bio.terra.workspace.model.DeleteControlledGcpGcsBucketResult;
import bio.terra.workspace.model.GcpGcsBucketAttributes;
import bio.terra.workspace.model.GcpGcsBucketCreationParameters;
import bio.terra.workspace.model.GcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.model.GcpGcsBucketLifecycle;
import bio.terra.workspace.model.GcpGcsBucketLifecycleRule;
import bio.terra.workspace.model.GcpGcsBucketLifecycleRuleAction;
import bio.terra.workspace.model.GcpGcsBucketLifecycleRuleActionType;
import bio.terra.workspace.model.GcpGcsBucketLifecycleRuleCondition;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GcpGcsBucketUpdateParameters;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.JobReport;
import bio.terra.workspace.model.ManagedBy;
import bio.terra.workspace.model.PrivateResourceUser;
import bio.terra.workspace.model.ReferenceResourceCommonFields;
import bio.terra.workspace.model.UpdateGcsBucketObjectReferenceRequestBody;
import bio.terra.workspace.model.UpdateGcsBucketReferenceRequestBody;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcsBucketUtils {

  public static final boolean BUCKET_LIFECYCLE_RULE_1_CONDITION_LIVE = true;
  public static final int BUCKET_LIFECYCLE_RULE_1_CONDITION_AGE = 64;
  public static final int BUCKET_LIFECYCLE_RULE_1_CONDITION_NUM_NEWER_VERSIONS = 2;
  public static final GcpGcsBucketLifecycleRule BUCKET_LIFECYCLE_RULE_1 =
      new GcpGcsBucketLifecycleRule()
          .action(
              new GcpGcsBucketLifecycleRuleAction()
                  .type(
                      GcpGcsBucketLifecycleRuleActionType
                          .DELETE)) // no storage class required for delete actions
          .condition(
              new GcpGcsBucketLifecycleRuleCondition()
                  .age(BUCKET_LIFECYCLE_RULE_1_CONDITION_AGE)
                  .live(GcsBucketUtils.BUCKET_LIFECYCLE_RULE_1_CONDITION_LIVE)
                  .addMatchesStorageClassItem(GcpGcsBucketDefaultStorageClass.ARCHIVE)
                  .numNewerVersions(BUCKET_LIFECYCLE_RULE_1_CONDITION_NUM_NEWER_VERSIONS));
  public static final GcpGcsBucketLifecycleRule BUCKET_LIFECYCLE_RULE_2 =
      new GcpGcsBucketLifecycleRule()
          .action(
              new GcpGcsBucketLifecycleRuleAction()
                  .storageClass(GcpGcsBucketDefaultStorageClass.NEARLINE)
                  .type(GcpGcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS))
          .condition(
              new GcpGcsBucketLifecycleRuleCondition()
                  .createdBefore(OffsetDateTime.parse("2007-01-03T00:00:00.00Z"))
                  .addMatchesStorageClassItem(GcpGcsBucketDefaultStorageClass.STANDARD));

  @SuppressFBWarnings(
      value = "MS_MUTABLE_COLLECTION",
      justification = "list must not be immutable if deserialization is to work")
  public static final List<GcpGcsBucketLifecycleRule> BUCKET_LIFECYCLE_RULES =
      new ArrayList<>(List.of(BUCKET_LIFECYCLE_RULE_1, BUCKET_LIFECYCLE_RULE_2));

  public static final String UPDATED_BUCKET_RESOURCE_NAME = "new_resource_name";
  public static final String UPDATED_BUCKET_RESOURCE_NAME_2 = "another_resource_name";
  public static final String UPDATED_BUCKET_RESOURCE_DESCRIPTION = "A bucket with a hole in it.";
  public static final String BUCKET_LOCATION = "US-CENTRAL1";
  public static final String BUCKET_PREFIX = "wsmtestbucket-";
  public static final String BUCKET_RESOURCE_PREFIX = "wsmtestresource-";
  public static final String GCS_BLOB_NAME = "wsmtestblob-name";
  public static final String GCS_BLOB_CONTENT = "This is the content of a text file.";
  public static final GcpGcsBucketUpdateParameters BUCKET_UPDATE_PARAMETER_1 =
      new GcpGcsBucketUpdateParameters()
          .defaultStorageClass(GcpGcsBucketDefaultStorageClass.NEARLINE)
          .lifecycle(
              new GcpGcsBucketLifecycle()
                  .addRulesItem(
                      new GcpGcsBucketLifecycleRule()
                          .action(
                              new GcpGcsBucketLifecycleRuleAction()
                                  .type(GcpGcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS)
                                  .storageClass(GcpGcsBucketDefaultStorageClass.ARCHIVE))
                          .condition(
                              new GcpGcsBucketLifecycleRuleCondition()
                                  .age(30)
                                  .createdBefore(
                                      OffsetDateTime.parse(
                                          "1981-04-20T21:15:30-05:00",
                                          DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                                  .live(true)
                                  .numNewerVersions(3)
                                  .addMatchesStorageClassItem(
                                      GcpGcsBucketDefaultStorageClass.ARCHIVE))));
  public static final GcpGcsBucketUpdateParameters BUCKET_UPDATE_PARAMETERS_2 =
      new GcpGcsBucketUpdateParameters()
          .defaultStorageClass(GcpGcsBucketDefaultStorageClass.COLDLINE);

  private static final Logger logger = LoggerFactory.getLogger(GcsBucketUtils.class);
  private static final long DELETE_BUCKET_POLL_SECONDS = 15;
  private static final Pattern GCS_BUCKET_PATTERN = Pattern.compile("^gs://([^/]+)$");

  /** Updates name, description, and/or referencing target for GCS bucket reference. */
  public static void updateGcsBucketReference(
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceId,
      UUID resourceId,
      @Nullable String name,
      @Nullable String description,
      @Nullable String bucketName)
      throws ApiException {
    UpdateGcsBucketReferenceRequestBody body = new UpdateGcsBucketReferenceRequestBody();
    if (name != null) {
      body.setName(name);
    }
    if (description != null) {
      body.setDescription(description);
    }
    if (bucketName != null) {
      body.setBucketName(bucketName);
    }
    resourceApi.updateBucketReferenceResource(body, workspaceId, resourceId);
  }

  // Fully parameterized version; category-specific versions below
  public static CreatedControlledGcpGcsBucket makeControlledGcsBucket(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String name,
      @Nullable String bucketName,
      AccessScope accessScope,
      ManagedBy managedBy,
      CloningInstructionsEnum cloningInstructions,
      @Nullable PrivateResourceUser privateUser)
      throws Exception {
    var body =
        new CreateControlledGcpGcsBucketRequestBody()
            .common(
                new ControlledResourceCommonFields()
                    .accessScope(accessScope)
                    .managedBy(managedBy)
                    .cloningInstructions(cloningInstructions)
                    .description("Description of " + name)
                    .name(name)
                    .privateResourceUser(privateUser))
            .gcsBucket(
                new GcpGcsBucketCreationParameters()
                    .name(bucketName)
                    .defaultStorageClass(GcpGcsBucketDefaultStorageClass.STANDARD)
                    .lifecycle(new GcpGcsBucketLifecycle().rules(BUCKET_LIFECYCLE_RULES)));

    logger.info(
        "Creating {} {} bucket in workspace {}", managedBy.name(), accessScope.name(), workspaceId);
    return resourceApi.createBucket(body, workspaceId);
  }

  public static CreatedControlledGcpGcsBucket makeControlledGcsBucketAppPrivate(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String name,
      CloningInstructionsEnum cloningInstructions,
      @Nullable PrivateResourceUser privateUser)
      throws Exception {
    return makeControlledGcsBucket(
        resourceApi,
        workspaceId,
        name,
        /*bucketName=*/ null,
        AccessScope.PRIVATE_ACCESS,
        ManagedBy.APPLICATION,
        cloningInstructions,
        privateUser);
  }

  public static void deleteControlledGcsBucket(
      UUID resourceId, UUID workspaceId, ControlledGcpResourceApi resourceApi) throws Exception {
    String deleteJobId = UUID.randomUUID().toString();
    var deleteRequest =
        new DeleteControlledGcpGcsBucketRequest().jobControl(new JobControl().id(deleteJobId));
    logger.info("Deleting bucket resource id {} jobId {}", resourceId, deleteJobId);
    DeleteControlledGcpGcsBucketResult result =
        resourceApi.deleteBucket(deleteRequest, workspaceId, resourceId);
    while (ClientTestUtils.jobIsRunning(result.getJobReport())) {
      TimeUnit.SECONDS.sleep(DELETE_BUCKET_POLL_SECONDS);
      result = resourceApi.getDeleteBucketResult(workspaceId, deleteJobId);
    }
    logger.info("Delete bucket status is {}", result.getJobReport().getStatus().toString());
    if (result.getJobReport().getStatus() != JobReport.StatusEnum.SUCCEEDED) {
      throw new RuntimeException("Delete bucket failed: " + result.getErrorReport().getMessage());
    }
  }

  /**
   * Calls WSM to create a referenced GCS bucket in the specified workspace.
   *
   * <p>This method retries on all WSM exceptions, do not use it for the negative case (where you do
   * not expect a user to be able to create a reference).
   */
  public static GcpGcsBucketResource makeGcsBucketReference(
      GcpGcsBucketAttributes bucket,
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceId,
      String name,
      @Nullable CloningInstructionsEnum cloningInstructions)
      throws Exception {
    var body =
        new CreateGcpGcsBucketReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(
                        Optional.ofNullable(cloningInstructions)
                            .orElse(CloningInstructionsEnum.NOTHING))
                    .description("Description of " + name)
                    .name(name))
            .bucket(bucket);

    logger.info("Making reference to a gcs bucket");
    return ClientTestUtils.getWithRetryOnException(
        () -> resourceApi.createBucketReference(body, workspaceId));
  }

  public static CreatedControlledGcpGcsBucket makeControlledGcsBucketAppShared(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String name,
      @Nullable CloningInstructionsEnum cloningInstructions)
      throws Exception {
    return makeControlledGcsBucket(
        resourceApi,
        workspaceId,
        name,
        /*bucketName=*/ null,
        AccessScope.SHARED_ACCESS,
        ManagedBy.APPLICATION,
        cloningInstructions,
        null);
  }

  public static CreatedControlledGcpGcsBucket makeControlledGcsBucketUserShared(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String name,
      CloningInstructionsEnum cloningInstructions)
      throws Exception {
    return GcsBucketUtils.makeControlledGcsBucket(
        resourceApi,
        workspaceId,
        name,
        /*bucketName=*/ null,
        AccessScope.SHARED_ACCESS,
        ManagedBy.USER,
        cloningInstructions,
        null);
  }

  public static CreatedControlledGcpGcsBucket makeControlledGcsBucketUserPrivate(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String name,
      CloningInstructionsEnum cloningInstructions)
      throws Exception {
    return GcsBucketUtils.makeControlledGcsBucket(
        resourceApi,
        workspaceId,
        name,
        /*bucketName=*/ null,
        AccessScope.PRIVATE_ACCESS,
        ManagedBy.USER,
        cloningInstructions,
        null);
  }

  /** Updates name, description, and/or referencing target for GCS bucket object reference. */
  public static void updateGcsBucketObjectReference(
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceId,
      UUID resourceId,
      @Nullable String name,
      @Nullable String description,
      @Nullable String bucketName,
      @Nullable String objectName)
      throws ApiException {
    UpdateGcsBucketObjectReferenceRequestBody body =
        new UpdateGcsBucketObjectReferenceRequestBody();
    if (name != null) {
      body.setName(name);
    }
    if (description != null) {
      body.setDescription(description);
    }
    if (bucketName != null) {
      body.setBucketName(bucketName);
    }
    if (objectName != null) {
      body.setObjectName(objectName);
    }
    resourceApi.updateBucketObjectReferenceResource(body, workspaceId, resourceId);
  }

  /**
   * Add a simple text file to a GCS bucket.
   *
   * <p>This method retries on all GCP exceptions, do not use it for the negative case (where you do
   * not expect a user to be able to create a file in the bucket).
   */
  public static Blob addFileToBucket(
      CreatedControlledGcpGcsBucket bucket, TestUserSpecification bucketWriter, String gcpProjectId)
      throws Exception {
    final Storage sourceOwnerStorageClient =
        ClientTestUtils.getGcpStorageClient(bucketWriter, gcpProjectId);
    final BlobId blobId =
        BlobId.of(bucket.getGcpBucket().getAttributes().getBucketName(), GCS_BLOB_NAME);
    final BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();

    // There can be IAM propagation delays, so be a patient with the creation
    return ClientTestUtils.getWithRetryOnException(
        () ->
            sourceOwnerStorageClient.create(
                blobInfo, GCS_BLOB_CONTENT.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Parse GCS bucket attributes from a GCS URI (e.g. "gs://my-bucket").
   *
   * <p>This only parses the resource identifier string, it does not check if the provided IDs are
   * real or valid.
   */
  public static GcpGcsBucketAttributes parseGcsBucket(String resourceIdentifier) {
    Matcher matcher = GCS_BUCKET_PATTERN.matcher(resourceIdentifier);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Resource identifier "
              + resourceIdentifier
              + " does not match expected pattern for GCS bucket");
    }
    return new GcpGcsBucketAttributes().bucketName(matcher.group(1));
  }
}
