package scripts.utils;

import static scripts.utils.ClientTestUtils.TEST_BQ_DATASET_NAME;
import static scripts.utils.ClientTestUtils.TEST_BQ_DATASET_PROJECT;
import static scripts.utils.ClientTestUtils.TEST_BUCKET_NAME;

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
import bio.terra.workspace.model.GcpBigQueryDatasetAttributes;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpGcsBucketAttributes;
import bio.terra.workspace.model.GcpGcsBucketCreationParameters;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.ManagedBy;
import bio.terra.workspace.model.ReferenceResourceCommonFields;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Static methods to create resources
public class ResourceMaker {
  private static final Logger logger = LoggerFactory.getLogger(ResourceMaker.class);

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

  private static final long CREATE_BUCKET_POLL_SECONDS = 5;

  public static GcpGcsBucketResource makeControlledGcsBucketUserShared(
      ControlledGcpResourceApi resourceApi, UUID workspaceId, String name) throws Exception {

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
            .gcsBucket(new GcpGcsBucketCreationParameters().name(bucketName));

    logger.info("Creating bucket {} jobId {} workspace {}", bucketName, jobId, workspaceId);
    CreatedControlledGcpGcsBucket createdBucket = resourceApi.createBucket(body, workspaceId);
    while (ClientTestUtils.jobIsRunning(createdBucket.getJobReport())) {
      TimeUnit.SECONDS.sleep(CREATE_BUCKET_POLL_SECONDS);
      createdBucket = resourceApi.getCreateBucketResult(workspaceId, jobId);
    }
    logger.info("Create bucket status is {}", createdBucket.getJobReport().getStatus().toString());
    return createdBucket.getGcpBucket();
  }
}
