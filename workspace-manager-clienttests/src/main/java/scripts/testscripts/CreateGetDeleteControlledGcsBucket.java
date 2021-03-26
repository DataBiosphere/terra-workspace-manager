package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CloudPlatform;
import bio.terra.workspace.model.ControlledResourceCommonFields;
import bio.terra.workspace.model.CreateCloudContextRequest;
import bio.terra.workspace.model.CreateCloudContextResult;
import bio.terra.workspace.model.CreateControlledGcsBucketRequestBody;
import bio.terra.workspace.model.CreatedControlledGcsBucket;
import bio.terra.workspace.model.DeleteControlledGcsBucketRequest;
import bio.terra.workspace.model.DeleteControlledGcsBucketResult;
import bio.terra.workspace.model.GcsBucketAttributes;
import bio.terra.workspace.model.GcsBucketCreationParameters;
import bio.terra.workspace.model.GcsBucketDefaultStorageClass;
import bio.terra.workspace.model.GcsBucketLifecycle;
import bio.terra.workspace.model.GcsBucketLifecycleRule;
import bio.terra.workspace.model.GcsBucketLifecycleRuleAction;
import bio.terra.workspace.model.GcsBucketLifecycleRuleActionType;
import bio.terra.workspace.model.GcsBucketLifecycleRuleCondition;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.JobReport;
import com.google.api.client.http.HttpStatusCodes;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class CreateGetDeleteControlledGcsBucket extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger =
      LoggerFactory.getLogger(CreateGetDeleteControlledGcsBucket.class);
  private static final Duration CREATE_BUCKET_POLL_INTERVAL = Duration.ofSeconds(5);
  private static final long CREATE_BUCKET_POLL_SECONDS = 5;
  private static final long DELETE_BUCKET_POLL_SECONDS = 15;
  private static final long CREATE_CONTEXT_POLL_SECONDS = 10;

  private static final GcsBucketLifecycleRule LIFECYCLE_RULE_1 =
      new GcsBucketLifecycleRule()
          .action(
              new GcsBucketLifecycleRuleAction()
                  .type(
                      GcsBucketLifecycleRuleActionType
                          .DELETE)) // no storage class required for delete actions
          .condition(
              new GcsBucketLifecycleRuleCondition()
                  .age(64)
                  .live(true)
                  .addMatchesStorageClassItem(GcsBucketDefaultStorageClass.ARCHIVE)
                  .numNewerVersions(2));

  private static final GcsBucketLifecycleRule LIFECYCLE_RULE_2 =
      new GcsBucketLifecycleRule()
          .action(
              new GcsBucketLifecycleRuleAction()
                  .storageClass(GcsBucketDefaultStorageClass.NEARLINE)
                  .type(GcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS))
          .condition(
              new GcsBucketLifecycleRuleCondition()
                  .createdBefore(LocalDate.of(2017, 2, 18))
                  .addMatchesStorageClassItem(GcsBucketDefaultStorageClass.STANDARD));

  // list must not be immutable if deserialization is to work
  static final List<GcsBucketLifecycleRule> LIFECYCLE_RULES =
      new ArrayList<>(List.of(LIFECYCLE_RULE_1, LIFECYCLE_RULE_2));

  private static final String BUCKET_LOCATION = "US-CENTRAL1";
  private static final String BUCKET_PREFIX = "wsmtestbucket-";
  private static final String RESOURCE_PREFIX = "wsmtestresource-";

  private TestUserSpecification reader;
  private String bucketName;
  private String resourceName;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    // Note the 0th user is the owner of the workspace, pulled out in the super class.
    assertThat(
        "There must be at least two test users defined for this test.",
        testUsers != null && testUsers.size() > 1);
    this.reader = testUsers.get(1);
    String nameSuffix = UUID.randomUUID().toString();
    this.bucketName = BUCKET_PREFIX + nameSuffix;
    this.resourceName = RESOURCE_PREFIX + nameSuffix;
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {

    ControlledGcpResourceApi resourceApi =
        ClientTestUtils.getControlledGpcResourceClient(testUser, server);

    // Create a user-shared controlled GCS bucket - should fail due to no cloud context
    CreatedControlledGcsBucket bucket = createBucketAttempt(resourceApi);
    assertThat(bucket.getJobReport().getStatus(), equalTo(JobReport.StatusEnum.FAILED));
    assertThat(
        bucket.getErrorReport().getStatusCode(), equalTo(HttpStatusCodes.STATUS_CODE_BAD_REQUEST));

    // Create the cloud context
    logger.info("Creating cloud context");

    String contextJobId = UUID.randomUUID().toString();
    var createContext =
        new CreateCloudContextRequest()
            .cloudPlatform(CloudPlatform.GCP)
            .jobControl(new JobControl().id(contextJobId));
    CreateCloudContextResult contextResult =
        workspaceApi.createCloudContext(createContext, getWorkspaceId());
    while (ClientTestUtils.jobIsRunning(contextResult.getJobReport())) {
      TimeUnit.SECONDS.sleep(CREATE_CONTEXT_POLL_SECONDS);
      contextResult = workspaceApi.getCreateCloudContextResult(getWorkspaceId(), contextJobId);
    }
    logger.info("Create context status is {}", contextResult.getJobReport().getStatus().toString());
    assertThat(contextResult.getJobReport().getStatus(), equalTo(JobReport.StatusEnum.SUCCEEDED));

    // Create the bucket - should work this time
    bucket = createBucketAttempt(resourceApi);
    assertThat(bucket.getJobReport().getStatus(), equalTo(JobReport.StatusEnum.SUCCEEDED));
    UUID resourceId = bucket.getResourceId();

    // Retrieve the bucket resource
    logger.info("Retrieving bucket resource id {}", resourceId.toString());
    GcsBucketAttributes gotBucket = resourceApi.getBucket(getWorkspaceId(), resourceId);
    assertThat(gotBucket.getBucketName(), equalTo(bucket.getGcpBucket().getBucketName()));

    // TODO: Check access:
    // - writer can add the file
    // - writer can read the file
    // - reader can read the file
    // - reader cannot write a file
    // - reader cannot delete the bucket

    // Delete bucket
    String deleteJobId = UUID.randomUUID().toString();
    var deleteRequest =
        new DeleteControlledGcsBucketRequest().jobControl(new JobControl().id(deleteJobId));
    logger.info("Deleting bucket resource id {} jobId {}", resourceId, deleteJobId);
    DeleteControlledGcsBucketResult result =
        resourceApi.deleteBucket(deleteRequest, getWorkspaceId(), resourceId);
    while (ClientTestUtils.jobIsRunning(result.getJobReport())) {
      TimeUnit.SECONDS.sleep(DELETE_BUCKET_POLL_SECONDS);
      result = resourceApi.getDeleteBucketResult(getWorkspaceId(), deleteJobId);
    }
    logger.info("Delete bucket status is {}", result.getJobReport().getStatus().toString());
    assertThat(result.getJobReport().getStatus(), equalTo(JobReport.StatusEnum.SUCCEEDED));

    // verify it's not there anymore
    // - via metadata
    // TODO: via GCP access

    try {
      resourceApi.getBucket(getWorkspaceId(), resourceId);
      throw new IllegalStateException("Incorrectly found a deleted bucket!");
    } catch (ApiException ex) {
      assertThat(ex.getCode(), equalTo(HttpStatusCodes.STATUS_CODE_NOT_FOUND));
    }
    bucketName = null;

    // Delete the cloud context. This is not required. Just some exercise for deleteCloudContext
    logger.info("Deleting the cloud context");
    workspaceApi.deleteCloudContext(getWorkspaceId(), CloudPlatform.GCP);
  }

  private CreatedControlledGcsBucket createBucketAttempt(ControlledGcpResourceApi resourceApi)
      throws Exception {
    String jobId = UUID.randomUUID().toString();
    var creationParameters =
        new GcsBucketCreationParameters()
            .name(bucketName)
            .location(BUCKET_LOCATION)
            .defaultStorageClass(GcsBucketDefaultStorageClass.STANDARD)
            .lifecycle(new GcsBucketLifecycle().rules(LIFECYCLE_RULES));

    var commonParameters =
        new ControlledResourceCommonFields()
            .name(resourceName)
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .accessScope(ControlledResourceCommonFields.AccessScopeEnum.SHARED_ACCESS)
            .managedBy(ControlledResourceCommonFields.ManagedByEnum.USER)
            .jobControl(new JobControl().id(jobId));

    var body =
        new CreateControlledGcsBucketRequestBody()
            .gcsBucket(creationParameters)
            .common(commonParameters);

    logger.info(
        "Attempt to creating bucket {} jobId {} workspace {}", bucketName, jobId, getWorkspaceId());
    CreatedControlledGcsBucket bucket = resourceApi.createBucket(body, getWorkspaceId());
    while (ClientTestUtils.jobIsRunning(bucket.getJobReport())) {
      TimeUnit.SECONDS.sleep(CREATE_BUCKET_POLL_SECONDS);
      bucket = resourceApi.getCreateBucketResult(getWorkspaceId(), jobId);
    }
    logger.info("Create bucket status is {}", bucket.getJobReport().getStatus().toString());
    return bucket;
  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws ApiException {
    super.doCleanup(testUsers, workspaceApi);
    if (bucketName != null) {
      logger.warn("Test failed to cleanup bucket " + bucketName);
    }
  }
}
