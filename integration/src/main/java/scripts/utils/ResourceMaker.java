package scripts.utils;

import static scripts.utils.ClientTestUtils.TEST_BQ_DATASET_NAME;
import static scripts.utils.ClientTestUtils.TEST_BQ_DATASET_PROJECT;
import static scripts.utils.ClientTestUtils.TEST_BQ_DATATABLE_NAME;
import static scripts.utils.ClientTestUtils.TEST_BUCKET_FILE_NAME;
import static scripts.utils.ClientTestUtils.TEST_BUCKET_NAME;
import static scripts.utils.GcsBucketTestFixtures.BUCKET_PREFIX;
import static scripts.utils.GcsBucketTestFixtures.LIFECYCLE_RULES;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceCommonFields;
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
import bio.terra.workspace.model.PrivateResourceUser;
import bio.terra.workspace.model.ReferenceResourceCommonFields;
import bio.terra.workspace.model.UpdateBigQueryDataTableReferenceRequestBody;
import bio.terra.workspace.model.UpdateBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.model.UpdateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.model.UpdateGcsBucketReferenceRequestBody;
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
  private static final String BUCKET_LOCATION = "US-CENTRAL1";

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
   * Update the name, description or referencing target of a reference.
   * @throws ApiException
   */
  public static void updateBigQueryDatasetReference(
      ReferencedGcpResourceApi resourceApi, UUID workspace, UUID resourceId,
      @Nullable String name, @Nullable String description, @Nullable GcpBigQueryDatasetAttributes attributes)
      throws ApiException {
    UpdateBigQueryDatasetReferenceRequestBody body =
        new UpdateBigQueryDatasetReferenceRequestBody();
    if (name != null) {
      body.setName(name);
    }
    if (description != null) {
      body.setDescription(description);
    }
    if (attributes != null) {
      body.setResourceAttributes(attributes);
    }
    resourceApi.updateBigQueryDatasetReferenceResource(body, workspace, resourceId);
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

  /**
   * Update name, description and/or referencing target of BigQuery data table.
   * @throws ApiException
   */
  public static void updateBigQueryDataTableReference(
      ReferencedGcpResourceApi resourceApi, UUID workspaceId, UUID resourceId, @Nullable String name,
      @Nullable String description, @Nullable GcpBigQueryDataTableAttributes attributes
  ) throws ApiException {
    UpdateBigQueryDataTableReferenceRequestBody body =
        new UpdateBigQueryDataTableReferenceRequestBody();
    if (name != null) {
      body.setName(name);
    }
    if (description != null) {
      body.setDescription(description);
    }
    if (attributes != null) {
      body.setResourceAttributes(attributes);
    }
    resourceApi.updateBigQueryDataTableReferenceResource(body, workspaceId, resourceId);
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
   * Update name, description, and/or referencing target of a data repo snapshot reference.
   * @throws ApiException
   */
  public static void updateDataRepoSnapshotReferenceResource(
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceId, UUID resourceId, @Nullable String name, @Nullable String description,
      @Nullable DataRepoSnapshotAttributes attributes
  ) throws ApiException {
    UpdateDataRepoSnapshotReferenceRequestBody body =
        new UpdateDataRepoSnapshotReferenceRequestBody();
    if (name != null) {
      body.setName(name);
    }
    if (description != null) {
      body.setDescription(description);
    }
    if (attributes != null) {
      body.setResourceAttributes(attributes);
    }

    resourceApi.updateDataRepoSnapshotReferenceResource(body, workspaceId, resourceId);
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
   * Update name, description, and/or referencing target for GCS bucket.
   * @throws ApiException
   */
  public static void updateGcsBucketReference(
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceId,
      UUID resourceId,
      @Nullable String name,
      @Nullable String description,
      @Nullable GcpGcsBucketAttributes attributes
  ) throws ApiException {
    UpdateGcsBucketReferenceRequestBody body =
        new UpdateGcsBucketReferenceRequestBody();
    if (name != null) {
      body.setName(name);
    }
    if (description != null) {
      body.setDescription(description);
    }
    if (attributes != null) {
      body.setResourceAttributes(attributes);
    }
    resourceApi.updateBucketReferenceResource(body, workspaceId, resourceId);
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
    return makeGcsBucketFileReference(resourceApi, workspaceId, name, null);
  }

  public static GcpGcsBucketFileResource makeGcsBucketFileReference(
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceId,
      String name,
      @Nullable CloningInstructionsEnum cloningInstructions)
      throws ApiException, InterruptedException {
    var body =
        new CreateGcpGcsBucketFileReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(
                        Optional.ofNullable(cloningInstructions)
                            .orElse(CloningInstructionsEnum.NOTHING))
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

  // Fully parameters version; category-specific versions below
  public static CreatedControlledGcpGcsBucket makeControlledGcsBucket(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String name,
      AccessScope accessScope,
      ManagedBy managedBy,
      CloningInstructionsEnum cloningInstructions,
      @Nullable PrivateResourceUser privateUser)
      throws Exception {
    String bucketName = BUCKET_PREFIX + ClientTestUtils.generateCloudResourceName();
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
                    .lifecycle(new GcpGcsBucketLifecycle().rules(LIFECYCLE_RULES))
                    .location(BUCKET_LOCATION));

    logger.info(
        "Creating {} {} bucket {} workspace {}",
        managedBy.name(),
        accessScope.name(),
        bucketName,
        workspaceId);
    return resourceApi.createBucket(body, workspaceId);
  }

  public static CreatedControlledGcpGcsBucket makeControlledGcsBucketUserShared(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String name,
      CloningInstructionsEnum cloningInstructions)
      throws Exception {
    return makeControlledGcsBucket(
        resourceApi,
        workspaceId,
        name,
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
    return makeControlledGcsBucket(
        resourceApi,
        workspaceId,
        name,
        AccessScope.PRIVATE_ACCESS,
        ManagedBy.USER,
        cloningInstructions,
        null);
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
        AccessScope.SHARED_ACCESS,
        ManagedBy.APPLICATION,
        cloningInstructions,
        null);
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
   * Create and return a BigQuery dataset controlled resource with constant values. This uses the
   * given datasetID as both the WSM resource name and the actual BigQuery dataset ID.
   */
  public static GcpBigQueryDatasetResource makeControlledBigQueryDataset(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String datasetId,
      AccessScope accessScope,
      ManagedBy managedBy,
      @Nullable CloningInstructionsEnum cloningInstructions,
      @Nullable PrivateResourceUser privateUser)
      throws Exception {

    var body =
        new CreateControlledGcpBigQueryDatasetRequestBody()
            .common(
                new ControlledResourceCommonFields()
                    .accessScope(accessScope)
                    .managedBy(managedBy)
                    .cloningInstructions(
                        Optional.ofNullable(cloningInstructions)
                            .orElse(CloningInstructionsEnum.NOTHING))
                    .description("Description of " + datasetId)
                    .name(datasetId)
                    .privateResourceUser(privateUser))
            .dataset(
                new GcpBigQueryDatasetCreationParameters()
                    .datasetId(datasetId)
                    .location("US-CENTRAL1"));

    logger.info(
        "Creating {} {} dataset {} workspace {}",
        managedBy.name(),
        accessScope.name(),
        datasetId,
        workspaceId);
    return resourceApi.createBigQueryDataset(body, workspaceId).getBigQueryDataset();
  }

  public static GcpBigQueryDatasetResource makeControlledBigQueryDatasetUserShared(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String datasetId,
      @Nullable CloningInstructionsEnum cloningInstructions)
      throws Exception {
    return makeControlledBigQueryDataset(
        resourceApi,
        workspaceId,
        datasetId,
        AccessScope.SHARED_ACCESS,
        ManagedBy.USER,
        cloningInstructions,
        null);
  }

  public static GcpBigQueryDatasetResource makeControlledBigQueryDatasetUserPrivate(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String datasetId,
      @Nullable CloningInstructionsEnum cloningInstructions)
      throws Exception {
    return makeControlledBigQueryDataset(
        resourceApi,
        workspaceId,
        datasetId,
        AccessScope.PRIVATE_ACCESS,
        ManagedBy.USER,
        cloningInstructions,
        null);
  }

  public static GcpBigQueryDatasetResource makeControlledBigQueryDatasetAppShared(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String datasetId,
      @Nullable CloningInstructionsEnum cloningInstructions)
      throws Exception {
    return makeControlledBigQueryDataset(
        resourceApi,
        workspaceId,
        datasetId,
        AccessScope.PRIVATE_ACCESS,
        ManagedBy.APPLICATION,
        cloningInstructions,
        null);
  }

  public static GcpBigQueryDatasetResource makeControlledBigQueryDatasetAppPrivate(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String datasetId,
      @Nullable CloningInstructionsEnum cloningInstructions,
      @Nullable PrivateResourceUser privateUser)
      throws Exception {
    return makeControlledBigQueryDataset(
        resourceApi,
        workspaceId,
        datasetId,
        AccessScope.PRIVATE_ACCESS,
        ManagedBy.APPLICATION,
        cloningInstructions,
        privateUser);
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

    var commonParameters =
        new ControlledResourceCommonFields()
            .name(RandomStringUtils.randomAlphabetic(6))
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .accessScope(AccessScope.PRIVATE_ACCESS)
            .managedBy(ManagedBy.USER)
            .privateResourceUser(null);

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
