package scripts.utils;

import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceCommonFields;
import bio.terra.workspace.model.CreateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.model.CreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.model.CreateGcpBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.model.CreateGcpGcsBucketReferenceRequestBody;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.DataRepoSnapshotAttributes;
import bio.terra.workspace.model.DataRepoSnapshotResource;
import bio.terra.workspace.model.DeleteControlledGcpGcsBucketRequest;
import bio.terra.workspace.model.DeleteControlledGcpGcsBucketResult;
import bio.terra.workspace.model.GcpBigQueryDatasetAttributes;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpGcsBucketAttributes;
import bio.terra.workspace.model.GcpGcsBucketCreationParameters;
import bio.terra.workspace.model.GcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.model.GcpGcsBucketLifecycle;
import bio.terra.workspace.model.GcpGcsBucketLifecycleRule;
import bio.terra.workspace.model.GcpGcsBucketLifecycleRuleAction;
import bio.terra.workspace.model.GcpGcsBucketLifecycleRuleActionType;
import bio.terra.workspace.model.GcpGcsBucketLifecycleRuleCondition;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.JobReport;
import bio.terra.workspace.model.ManagedBy;
import bio.terra.workspace.model.ReferenceResourceCommonFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static scripts.utils.ClientTestUtils.TEST_BQ_DATASET_NAME;
import static scripts.utils.ClientTestUtils.TEST_BQ_DATASET_PROJECT;
import static scripts.utils.ClientTestUtils.TEST_BUCKET_NAME;

// Static methods to create resources
public class ResourceMaker {
  private static final Logger logger = LoggerFactory.getLogger(ResourceMaker.class);
  private static final long CREATE_BUCKET_POLL_SECONDS = 5;
  private static final long DELETE_BUCKET_POLL_SECONDS = 15;

  public static GcpBigQueryDatasetResource makeBigQueryReference(
      ReferencedGcpResourceApi resourceApi, UUID workspaceId, String name) throws ApiException {

    var body =
        new CreateGcpBigQueryDatasetReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(CloningInstructionsEnum.NOTHING)
                    .description("Descriptiono of " + name)
                    .name(name))
            .dataset(
                new GcpBigQueryDatasetAttributes()
                    .datasetId(TEST_BQ_DATASET_NAME)
                    .projectId(TEST_BQ_DATASET_PROJECT));

    return resourceApi.createBigQueryDatasetReference(body, workspaceId);
  }

  public static DataRepoSnapshotResource makeDataRepoSnapshotReference(
      ReferencedGcpResourceApi resourceApi, UUID workspaceId, String name) throws ApiException {

    var body =
        new CreateDataRepoSnapshotReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(CloningInstructionsEnum.NOTHING)
                    .description("Descriptiono of " + name)
                    .name(name))
            .snapshot(
                new DataRepoSnapshotAttributes()
                    .snapshot(ClientTestUtils.TEST_SNAPSHOT)
                    .instanceName(ClientTestUtils.TERRA_DATA_REPO_INSTANCE));

    return resourceApi.createDataRepoSnapshotReference(body, workspaceId);
  }

  public static GcpGcsBucketResource makeGcsBucketReference(
      ReferencedGcpResourceApi resourceApi, UUID workspaceId, String name) throws ApiException {

    var body =
        new CreateGcpGcsBucketReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(CloningInstructionsEnum.NOTHING)
                    .description("Descriptiono of " + name)
                    .name(name))
            .bucket(new GcpGcsBucketAttributes().bucketName(TEST_BUCKET_NAME));

    return resourceApi.createBucketReference(body, workspaceId);
  }

  public static GcpGcsBucketResource makeControlledGcsBucketUserShared(
      ControlledGcpResourceApi resourceApi, UUID workspaceId, String name) throws Exception {

    // TODO: we should tolerate getting no default storage and no lifecycle rule. That is captured
    //  in PF-635. For now, we build those things so the bucket gets created.
    GcpGcsBucketLifecycleRule lifecycleRule =
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

    List<GcpGcsBucketLifecycleRule> lifecycleRules = new ArrayList<>(List.of(lifecycleRule));

    String bucketName = ClientTestUtils.getTestBucketName();
    String jobId = UUID.randomUUID().toString();
    var body =
        new CreateControlledGcpGcsBucketRequestBody()
            .common(
                new ControlledResourceCommonFields()
                    .accessScope(AccessScope.SHARED_ACCESS)
                    .managedBy(ManagedBy.USER)
                    .cloningInstructions(CloningInstructionsEnum.NOTHING)
                    .description("Description of " + name)
                    .name(name)
                    .jobControl(new JobControl().id(jobId)))
            .gcsBucket(
                new GcpGcsBucketCreationParameters()
                    .name(bucketName)
                    .defaultStorageClass(GcpGcsBucketDefaultStorageClass.STANDARD)
                    .lifecycle(new GcpGcsBucketLifecycle().rules(lifecycleRules))
                    .location("US-CENTRAL1"));

    logger.info("Creating bucket {} jobId {} workspace {}", bucketName, jobId, workspaceId);
    CreatedControlledGcpGcsBucket createdBucket = resourceApi.createBucket(body, workspaceId);
    while (ClientTestUtils.jobIsRunning(createdBucket.getJobReport())) {
      TimeUnit.SECONDS.sleep(CREATE_BUCKET_POLL_SECONDS);
      createdBucket = resourceApi.getCreateBucketResult(workspaceId, jobId);
    }
    logger.info("Create bucket status is {}", createdBucket.getJobReport().getStatus().toString());
    if (createdBucket.getJobReport().getStatus() != JobReport.StatusEnum.SUCCEEDED) {
      throw new RuntimeException(
          "Create bucket failed: " + createdBucket.getErrorReport().getMessage());
    }
    return createdBucket.getGcpBucket();
  }

  public static void deleteControlledGcsBucketUserShared(
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
      throw new RuntimeException("Delte bucket failed: " + result.getErrorReport().getMessage());
    }
  }
}
