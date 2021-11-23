package scripts.utils;

import static scripts.utils.ClientTestUtils.TEST_BQ_DATASET_NAME;
import static scripts.utils.ClientTestUtils.TEST_BQ_DATASET_PROJECT;
import static scripts.utils.ClientTestUtils.TEST_BQ_DATATABLE_NAME;
import static scripts.utils.ClientTestUtils.TEST_BUCKET_FILE_NAME;
import static scripts.utils.ClientTestUtils.TEST_BUCKET_NAME;
import static scripts.utils.GcsBucketTestFixtures.LIFECYCLE_RULES;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceCommonFields;
import bio.terra.workspace.model.ControlledResourceIamRole;
import bio.terra.workspace.model.CreateControlledGcpAiNotebookInstanceRequestBody;
import bio.terra.workspace.model.CreateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.model.CreateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.model.CreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.model.CreateGcpBigQueryDataTableReferenceRequestBody;
import bio.terra.workspace.model.CreateGcpBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.model.CreateGcpGcsBucketFileReferenceRequestBody;
import bio.terra.workspace.model.CreateGcpGcsBucketReferenceRequestBody;
import bio.terra.workspace.model.CreatedControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.DataRepoSnapshotAttributes;
import bio.terra.workspace.model.DataRepoSnapshotResource;
import bio.terra.workspace.model.DeleteControlledGcpGcsBucketRequest;
import bio.terra.workspace.model.DeleteControlledGcpGcsBucketResult;
import bio.terra.workspace.model.GcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.model.GcpAiNotebookInstanceVmImage;
import bio.terra.workspace.model.GcpBigQueryDataTableAttributes;
import bio.terra.workspace.model.GcpBigQueryDataTableResource;
import bio.terra.workspace.model.GcpBigQueryDatasetAttributes;
import bio.terra.workspace.model.GcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpGcsBucketAttributes;
import bio.terra.workspace.model.GcpGcsBucketCreationParameters;
import bio.terra.workspace.model.GcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.model.GcpGcsBucketFileAttributes;
import bio.terra.workspace.model.GcpGcsBucketFileResource;
import bio.terra.workspace.model.GcpGcsBucketLifecycle;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.JobReport;
import bio.terra.workspace.model.ManagedBy;
import bio.terra.workspace.model.PrivateResourceIamRoles;
import bio.terra.workspace.model.PrivateResourceUser;
import bio.terra.workspace.model.ReferenceResourceCommonFields;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Static methods to create resources
public class ResourceMaker {
  private static final Logger logger = LoggerFactory.getLogger(ResourceMaker.class);
  private static final long DELETE_BUCKET_POLL_SECONDS = 15;

  /**
   * Calls WSM to create a referenced BigQuery dataset in the specified workspace.
   *
   * <p>This method retries on all WSM exceptions, do not use it for the negative case (where you do
   * not expect a user to be able to create a reference).
   */
  public static GcpBigQueryDatasetResource makeBigQueryDatasetReference(
      ReferencedGcpResourceApi resourceApi, UUID workspaceId, String name)
      throws ApiException, InterruptedException {

    var body =
        new CreateGcpBigQueryDatasetReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(CloningInstructionsEnum.NOTHING)
                    .description("Description of " + name)
                    .name(name))
            .dataset(
                new GcpBigQueryDatasetAttributes()
                    .datasetId(TEST_BQ_DATASET_NAME)
                    .projectId(TEST_BQ_DATASET_PROJECT));

    return ClientTestUtils.getWithRetryOnException(
        () -> resourceApi.createBigQueryDatasetReference(body, workspaceId));
  }

  /**
   * Calls WSM to create a referenced BigQuery table in the specified workspace.
   *
   * <p>This method retries on all WSM exceptions, do not use it for the negative case (where you do
   * not expect a user to be able to create a reference).
   */
  public static GcpBigQueryDataTableResource makeBigQueryDataTableReference(
      ReferencedGcpResourceApi resourceApi, UUID workspaceId, String name)
      throws ApiException, InterruptedException {

    var body =
        new CreateGcpBigQueryDataTableReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(CloningInstructionsEnum.NOTHING)
                    .description("Description of " + name)
                    .name(name))
            .dataTable(
                new GcpBigQueryDataTableAttributes()
                    .datasetId(TEST_BQ_DATASET_NAME)
                    .projectId(TEST_BQ_DATASET_PROJECT)
                    .dataTableId(TEST_BQ_DATATABLE_NAME));

    return ClientTestUtils.getWithRetryOnException(
        () -> resourceApi.createBigQueryDataTableReference(body, workspaceId));
  }

  /** Calls WSM to create a referenced TDR snapshot in the specified workspace. */
  public static DataRepoSnapshotResource makeDataRepoSnapshotReference(
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceId,
      String name,
      String dataRepoSnapshotId,
      String dataRepoInstanceName)
      throws ApiException {

    var body =
        new CreateDataRepoSnapshotReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(CloningInstructionsEnum.NOTHING)
                    .description("Description of " + name)
                    .name(name))
            .snapshot(
                new DataRepoSnapshotAttributes()
                    .snapshot(dataRepoSnapshotId)
                    .instanceName(dataRepoInstanceName));

    return resourceApi.createDataRepoSnapshotReference(body, workspaceId);
  }

  /**
   * Calls WSM to create a referenced GCS bucket in the specified workspace.
   *
   * <p>This method retries on all WSM exceptions, do not use it for the negative case (where you do
   * not expect a user to be able to create a reference).
   */
  public static GcpGcsBucketResource makeGcsBucketReference(
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceId,
      String name,
      @Nullable CloningInstructionsEnum cloningInstructions)
      throws ApiException, InterruptedException {

    var body =
        new CreateGcpGcsBucketReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(
                        Optional.ofNullable(cloningInstructions)
                            .orElse(CloningInstructionsEnum.NOTHING))
                    .description("Description of " + name)
                    .name(name))
            .bucket(new GcpGcsBucketAttributes().bucketName(TEST_BUCKET_NAME));

    return ClientTestUtils.getWithRetryOnException(
        () -> resourceApi.createBucketReference(body, workspaceId));
  }

  /**
   * Calls WSM to create a referenced GCS bucket file in the specified workspace.
   *
   * <p>This method retries on all WSM exceptions, do not use it for the negative case (where you do
   * not expect a user to be able to create a reference).
   */
  public static GcpGcsBucketFileResource makeGcsBucketFileReference(
      ReferencedGcpResourceApi resourceApi, UUID workspaceId, String name)
      throws ApiException, InterruptedException {
    var body =
        new CreateGcpGcsBucketFileReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(CloningInstructionsEnum.NOTHING)
                    .description("Description of " + name)
                    .name(name))
            .file(
                new GcpGcsBucketFileAttributes()
                    .bucketName(TEST_BUCKET_NAME)
                    .fileName(TEST_BUCKET_FILE_NAME));

    return ClientTestUtils.getWithRetryOnException(
        () -> resourceApi.createBucketFileReference(body, workspaceId));
  }

  public static GcpGcsBucketResource makeGcsBucketReference(
      ReferencedGcpResourceApi resourceApi, UUID workspaceId, String name)
      throws ApiException, InterruptedException {
    return makeGcsBucketReference(resourceApi, workspaceId, name, null);
  }

  public static CreatedControlledGcpGcsBucket makeControlledGcsBucketUserShared(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String name,
      CloningInstructionsEnum cloningInstructions)
      throws Exception {

    String bucketName = ClientTestUtils.generateCloudResourceName();
    var body =
        new CreateControlledGcpGcsBucketRequestBody()
            .common(
                new ControlledResourceCommonFields()
                    .accessScope(AccessScope.SHARED_ACCESS)
                    .managedBy(ManagedBy.USER)
                    .cloningInstructions(cloningInstructions)
                    .description("Description of " + name)
                    .name(name))
            .gcsBucket(
                new GcpGcsBucketCreationParameters()
                    .name(bucketName)
                    .defaultStorageClass(GcpGcsBucketDefaultStorageClass.STANDARD)
                    .lifecycle(new GcpGcsBucketLifecycle().rules(LIFECYCLE_RULES))
                    .location("US-CENTRAL1"));

    logger.info("Creating bucket {} workspace {}", bucketName, workspaceId);
    return resourceApi.createBucket(body, workspaceId);
  }

  public static CreatedControlledGcpGcsBucket makeControlledGcsBucketUserPrivate(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String name,
      String privateResourceUserEmail,
      PrivateResourceIamRoles privateResourceRoles,
      CloningInstructionsEnum cloningInstructions)
      throws Exception {
    String bucketName = ClientTestUtils.generateCloudResourceName();
    var body =
        new CreateControlledGcpGcsBucketRequestBody()
            .common(
                new ControlledResourceCommonFields()
                    .accessScope(AccessScope.PRIVATE_ACCESS)
                    .privateResourceUser(
                        new PrivateResourceUser()
                            .userName(privateResourceUserEmail)
                            .privateResourceIamRoles(privateResourceRoles))
                    .managedBy(ManagedBy.USER)
                    .cloningInstructions(cloningInstructions)
                    .description("Description of " + name)
                    .name(name))
            .gcsBucket(
                new GcpGcsBucketCreationParameters()
                    .name(bucketName)
                    .defaultStorageClass(GcpGcsBucketDefaultStorageClass.STANDARD)
                    .lifecycle(new GcpGcsBucketLifecycle().rules(LIFECYCLE_RULES))
                    .location("US-CENTRAL1"));

    logger.info("Creating bucket {} workspace {}", bucketName, workspaceId);
    return resourceApi.createBucket(body, workspaceId);
  }

  public static CreatedControlledGcpGcsBucket makeControlledGcsBucketUserPrivate(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String name,
      String privateResourceUserEmail,
      PrivateResourceIamRoles privateResourceRoles)
      throws Exception {
    return makeControlledGcsBucketUserPrivate(
        resourceApi,
        workspaceId,
        name,
        privateResourceUserEmail,
        privateResourceRoles,
        CloningInstructionsEnum.NOTHING);
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
   * Create and return a BigQuery dataset controlled resource with constant values. This uses the
   * given datasetID as both the WSM resource name and the actual BigQuery dataset ID.
   */
  public static GcpBigQueryDatasetResource makeControlledBigQueryDatasetUserShared(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String datasetId,
      @Nullable CloningInstructionsEnum cloningInstructions)
      throws Exception {

    var body =
        new CreateControlledGcpBigQueryDatasetRequestBody()
            .common(
                new ControlledResourceCommonFields()
                    .accessScope(AccessScope.SHARED_ACCESS)
                    .managedBy(ManagedBy.USER)
                    .cloningInstructions(
                        Optional.ofNullable(cloningInstructions)
                            .orElse(CloningInstructionsEnum.NOTHING))
                    .description("Description of " + datasetId)
                    .name(datasetId))
            .dataset(
                new GcpBigQueryDatasetCreationParameters()
                    .datasetId(datasetId)
                    .location("US-CENTRAL1"));

    logger.info("Creating dataset {} workspace {}", datasetId, workspaceId);
    return resourceApi.createBigQueryDataset(body, workspaceId).getBigQueryDataset();
  }

  public static GcpBigQueryDatasetResource makeControlledBigQueryDatasetUserShared(
      ControlledGcpResourceApi resourceApi, UUID workspaceId, String datasetId) throws Exception {
    return makeControlledBigQueryDatasetUserShared(resourceApi, workspaceId, datasetId, null);
  }

  public static GcpBigQueryDatasetResource makeControlledBigQueryDatasetUserPrivate(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String datasetId,
      String privateResourceUserEmail,
      PrivateResourceIamRoles iamRoles,
      @Nullable CloningInstructionsEnum cloningInstructions)
      throws Exception {

    var body =
        new CreateControlledGcpBigQueryDatasetRequestBody()
            .common(
                new ControlledResourceCommonFields()
                    .accessScope(AccessScope.PRIVATE_ACCESS)
                    .managedBy(ManagedBy.USER)
                    .privateResourceUser(
                        new PrivateResourceUser()
                            .userName(privateResourceUserEmail)
                            .privateResourceIamRoles(iamRoles))
                    .cloningInstructions(
                        Optional.ofNullable(cloningInstructions)
                            .orElse(CloningInstructionsEnum.NOTHING))
                    .description("Description of " + datasetId)
                    .name(datasetId))
            .dataset(
                new GcpBigQueryDatasetCreationParameters()
                    .datasetId(datasetId)
                    .location("US-CENTRAL1"));

    logger.info("Creating dataset {} workspace {}", datasetId, workspaceId);
    return resourceApi.createBigQueryDataset(body, workspaceId).getBigQueryDataset();
  }

  /**
   * Create and return a private AI Platform Notebook controlled resource with constant values. This
   * method calls the asynchronous creation endpoint and polls until the creation job completes.
   */
  public static CreatedControlledGcpAiNotebookInstanceResult makeControlledNotebookUserPrivate(
      UUID workspaceId,
      String instanceId,
      TestUserSpecification user,
      ControlledGcpResourceApi resourceApi)
      throws ApiException, InterruptedException {
    // Fill out the minimum required fields to arbitrary values.
    var creationParameters =
        new GcpAiNotebookInstanceCreationParameters()
            .instanceId(instanceId)
            .location("us-east1-b")
            .machineType("e2-standard-2")
            .vmImage(
                new GcpAiNotebookInstanceVmImage()
                    .projectId("deeplearning-platform-release")
                    .imageFamily("r-latest-cpu-experimental"));

    PrivateResourceIamRoles privateIamRoles = new PrivateResourceIamRoles();
    privateIamRoles.add(ControlledResourceIamRole.EDITOR);
    privateIamRoles.add(ControlledResourceIamRole.WRITER);
    var commonParameters =
        new ControlledResourceCommonFields()
            .name(RandomStringUtils.randomAlphabetic(6))
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .accessScope(AccessScope.PRIVATE_ACCESS)
            .managedBy(ManagedBy.USER)
            .privateResourceUser(
                new PrivateResourceUser()
                    .userName(user.userEmail)
                    .privateResourceIamRoles(privateIamRoles));

    var body =
        new CreateControlledGcpAiNotebookInstanceRequestBody()
            .aiNotebookInstance(creationParameters)
            .common(commonParameters)
            .jobControl(new JobControl().id(UUID.randomUUID().toString()));

    var creationResult = resourceApi.createAiNotebookInstance(body, workspaceId);
    String creationJobId = creationResult.getJobReport().getId();
    creationResult =
        ClientTestUtils.pollWhileRunning(
            creationResult,
            () -> resourceApi.getCreateAiNotebookInstanceResult(workspaceId, creationJobId),
            CreatedControlledGcpAiNotebookInstanceResult::getJobReport,
            Duration.ofSeconds(10));
    ClientTestUtils.assertJobSuccess(
        "create ai notebook", creationResult.getJobReport(), creationResult.getErrorReport());
    return creationResult;
  }
}
