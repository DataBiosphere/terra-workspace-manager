package scripts.utils;

import static scripts.utils.GcsBucketTestFixtures.LIFECYCLE_RULES;

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
import bio.terra.workspace.model.CreateGcpGcsBucketReferenceRequestBody;
import bio.terra.workspace.model.CreateGcpGcsObjectReferenceRequestBody;
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
import bio.terra.workspace.model.GcpGcsBucketLifecycle;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GcpGcsObjectAttributes;
import bio.terra.workspace.model.GcpGcsObjectResource;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.JobReport;
import bio.terra.workspace.model.ManagedBy;
import bio.terra.workspace.model.PrivateResourceUser;
import bio.terra.workspace.model.ReferenceResourceCommonFields;
import bio.terra.workspace.model.ResourceMetadata;
import bio.terra.workspace.model.StewardshipType;
import bio.terra.workspace.model.UpdateBigQueryDataTableReferenceRequestBody;
import bio.terra.workspace.model.UpdateBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.model.UpdateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.model.UpdateGcsBucketObjectReferenceRequestBody;
import bio.terra.workspace.model.UpdateGcsBucketReferenceRequestBody;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
      GcpBigQueryDatasetAttributes dataset,
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceId,
      String name)
      throws ApiException, InterruptedException {

    var body =
        new CreateGcpBigQueryDatasetReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(CloningInstructionsEnum.NOTHING)
                    .description("Description of " + name)
                    .name(name))
            .dataset(dataset);

    return ClientTestUtils.getWithRetryOnException(
        () -> resourceApi.createBigQueryDatasetReference(body, workspaceId));
  }

  /** Updates the name, description or referencing target of a BQ dataset reference. */
  public static void updateBigQueryDatasetReference(
      ReferencedGcpResourceApi resourceApi,
      UUID workspace,
      UUID resourceId,
      @Nullable String name,
      @Nullable String description,
      @Nullable String projectId,
      @Nullable String datasetId)
      throws ApiException {
    UpdateBigQueryDatasetReferenceRequestBody body =
        new UpdateBigQueryDatasetReferenceRequestBody();
    if (name != null) {
      body.setName(name);
    }
    if (description != null) {
      body.setDescription(description);
    }
    if (projectId != null) {
      body.setProjectId(projectId);
    }
    if (datasetId != null) {
      body.setDatasetId(datasetId);
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
      GcpBigQueryDataTableAttributes dataTable,
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceId,
      String name)
      throws ApiException, InterruptedException {

    var body =
        new CreateGcpBigQueryDataTableReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(CloningInstructionsEnum.NOTHING)
                    .description("Description of " + name)
                    .name(name))
            .dataTable(dataTable);

    return ClientTestUtils.getWithRetryOnException(
        () -> resourceApi.createBigQueryDataTableReference(body, workspaceId));
  }

  /** Updates name, description and/or referencing target of BigQuery data table reference. */
  public static void updateBigQueryDataTableReference(
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceId,
      UUID resourceId,
      @Nullable String name,
      @Nullable String description,
      @Nullable String projectId,
      @Nullable String datasetId,
      @Nullable String tableId)
      throws ApiException {
    UpdateBigQueryDataTableReferenceRequestBody body =
        new UpdateBigQueryDataTableReferenceRequestBody();
    if (name != null) {
      body.setName(name);
    }
    if (description != null) {
      body.setDescription(description);
    }
    if (projectId != null) {
      body.setProjectId(projectId);
    }
    if (datasetId != null) {
      body.setDatasetId(datasetId);
    }
    if (tableId != null) {
      body.setDataTableId(tableId);
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

  /** Updates name, description, and/or referencing target of a data repo snapshot reference. */
  public static void updateDataRepoSnapshotReferenceResource(
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceId,
      UUID resourceId,
      @Nullable String name,
      @Nullable String description,
      @Nullable String instanceId,
      @Nullable String snapshot)
      throws ApiException {
    UpdateDataRepoSnapshotReferenceRequestBody body =
        new UpdateDataRepoSnapshotReferenceRequestBody();
    if (name != null) {
      body.setName(name);
    }
    if (description != null) {
      body.setDescription(description);
    }
    if (instanceId != null) {
      body.setInstanceName(instanceId);
    }
    if (snapshot != null) {
      body.setSnapshot(snapshot);
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
      GcpGcsBucketAttributes bucket,
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
            .bucket(bucket);

    logger.info("Making reference to a gcs bucket");
    return ClientTestUtils.getWithRetryOnException(
        () -> resourceApi.createBucketReference(body, workspaceId));
  }

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

  /**
   * Calls WSM to create a referenced GCS bucket object in the specified workspace.
   *
   * <p>This method retries on all WSM exceptions, do not use it for the negative case (where you do
   * not expect a user to be able to create a reference).
   */
  public static GcpGcsObjectResource makeGcsObjectReference(
      GcpGcsObjectAttributes file,
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceId,
      String name,
      @Nullable CloningInstructionsEnum cloningInstructionsEnum)
      throws InterruptedException {
    var body =
        new CreateGcpGcsObjectReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(
                        Optional.ofNullable(cloningInstructionsEnum)
                            .orElse(CloningInstructionsEnum.NOTHING))
                    .description("Description of " + name)
                    .name(name))
            .file(file);

    logger.info("Making reference to a gcs bucket file");
    return ClientTestUtils.getWithRetryOnException(
        () -> resourceApi.createGcsObjectReference(body, workspaceId));
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
                    .lifecycle(new GcpGcsBucketLifecycle().rules(LIFECYCLE_RULES)));

    logger.info(
        "Creating {} {} bucket in workspace {}", managedBy.name(), accessScope.name(), workspaceId);
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
    return makeControlledGcsBucket(
        resourceApi,
        workspaceId,
        name,
        /*bucketName=*/ null,
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
        /*bucketName=*/ null,
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
   * Create and return a BigQuery dataset controlled resource with constant values. This uses the
   * given datasetID as both the WSM resource name and the actual BigQuery dataset ID.
   */
  private static GcpBigQueryDatasetResource makeControlledBigQueryDataset(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String resourceName,
      @Nullable String datasetId,
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
                    .description("Description of " + resourceName)
                    .name(resourceName)
                    .privateResourceUser(privateUser))
            .dataset(new GcpBigQueryDatasetCreationParameters().datasetId(datasetId));

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
      String resourceName,
      @Nullable String datasetId,
      @Nullable CloningInstructionsEnum cloningInstructions)
      throws Exception {
    return makeControlledBigQueryDataset(
        resourceApi,
        workspaceId,
        resourceName,
        datasetId,
        AccessScope.SHARED_ACCESS,
        ManagedBy.USER,
        cloningInstructions,
        null);
  }

  public static GcpBigQueryDatasetResource makeControlledBigQueryDatasetUserPrivate(
      ControlledGcpResourceApi resourceApi,
      UUID workspaceId,
      String resourceName,
      @Nullable String datasetId,
      @Nullable CloningInstructionsEnum cloningInstructions)
      throws Exception {
    return makeControlledBigQueryDataset(
        resourceApi,
        workspaceId,
        resourceName,
        datasetId,
        AccessScope.PRIVATE_ACCESS,
        ManagedBy.USER,
        cloningInstructions,
        null);
  }

  /**
   * Create and return a private AI Platform Notebook controlled resource with constant values. This
   * method calls the asynchronous creation endpoint and polls until the creation job completes.
   */
  public static CreatedControlledGcpAiNotebookInstanceResult makeControlledNotebookUserPrivate(
      UUID workspaceId,
      @Nullable String instanceId,
      @Nullable String location,
      ControlledGcpResourceApi resourceApi)
      throws ApiException, InterruptedException {
    // Fill out the minimum required fields to arbitrary values.
    var creationParameters =
        new GcpAiNotebookInstanceCreationParameters()
            .instanceId(instanceId)
            .location(location)
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

  // Support for makeResources
  private static String makeName() {
    return RandomStringUtils.random(10, true, false);
  }

  @FunctionalInterface
  public interface SupplierException<T> {
    T get() throws Exception;
  }

  /**
   * Make a bunch of resources
   *
   * @param referencedGcpResourceApi api for referenced resources
   * @param controlledGcpResourceApi api for controlled resources
   * @param workspaceId workspace where we allocate
   * @param dataRepoSnapshotId ID of the TDR snapshot to use for snapshot references
   * @param dataRepoInstanceName Instance ID to use for snapshot references
   * @param bucket GCS Bucket to use for bucket references
   * @param bqTable BigQuery table to use for BQ dataset and table references.
   * @param resourceCount number of resources to allocate
   * @return list of resources
   * @throws Exception whatever might come up
   */
  public static List<ResourceMetadata> makeResources(
      ReferencedGcpResourceApi referencedGcpResourceApi,
      ControlledGcpResourceApi controlledGcpResourceApi,
      UUID workspaceId,
      String dataRepoSnapshotId,
      String dataRepoInstanceName,
      GcpGcsBucketAttributes bucket,
      GcpBigQueryDataTableAttributes bqTable,
      int resourceCount)
      throws Exception {

    // Array of resource allocators
    List<SupplierException<ResourceMetadata>> makers =
        List.of(
            // BQ dataset reference
            () -> {
              // Use the same BQ dataset specified for table references
              GcpBigQueryDatasetAttributes dataset =
                  new GcpBigQueryDatasetAttributes()
                      .projectId(bqTable.getProjectId())
                      .datasetId(bqTable.getDatasetId());
              GcpBigQueryDatasetResource resource =
                  makeBigQueryDatasetReference(
                      dataset, referencedGcpResourceApi, workspaceId, makeName());
              return resource.getMetadata();
            },

            // TDR snapshot reference
            () -> {
              DataRepoSnapshotResource resource =
                  ResourceMaker.makeDataRepoSnapshotReference(
                      referencedGcpResourceApi,
                      workspaceId,
                      makeName(),
                      dataRepoSnapshotId,
                      dataRepoInstanceName);
              return resource.getMetadata();
            },

            // GCS bucket reference
            () -> {
              GcpGcsBucketResource resource =
                  makeGcsBucketReference(
                      bucket,
                      referencedGcpResourceApi,
                      workspaceId,
                      makeName(),
                      CloningInstructionsEnum.NOTHING);
              return resource.getMetadata();
            },

            // GCS bucket controlled shared
            () -> {
              GcpGcsBucketResource resource =
                  ResourceMaker.makeControlledGcsBucketUserShared(
                          controlledGcpResourceApi,
                          workspaceId,
                          makeName(),
                          CloningInstructionsEnum.NOTHING)
                      .getGcpBucket();
              return resource.getMetadata();
            },

            // GCS bucket controlled private
            () -> {
              GcpGcsBucketResource resource =
                  ResourceMaker.makeControlledGcsBucketUserPrivate(
                          controlledGcpResourceApi,
                          workspaceId,
                          makeName(),
                          CloningInstructionsEnum.NOTHING)
                      .getGcpBucket();
              return resource.getMetadata();
            },

            // BQ dataset controlled shared
            () -> {
              GcpBigQueryDatasetResource resource =
                  ResourceMaker.makeControlledBigQueryDatasetUserShared(
                      controlledGcpResourceApi,
                      workspaceId,
                      makeName(),
                      null,
                      CloningInstructionsEnum.NOTHING);
              return resource.getMetadata();
            },

            // BQ data table reference
            () -> {
              GcpBigQueryDataTableResource resource =
                  ResourceMaker.makeBigQueryDataTableReference(
                      bqTable, referencedGcpResourceApi, workspaceId, makeName());
              return resource.getMetadata();
            });

    // Build the resources
    List<ResourceMetadata> resources = new ArrayList<>();
    for (int i = 0; i < resourceCount; i++) {
      int index = i % makers.size();
      resources.add(makers.get(index).get());
    }
    return resources;
  }

  /**
   * Designed to cleanup the result of allocations from {@link #makeResources}
   *
   * @param resources list of resources to cleanup
   */
  public static void cleanupResources(
      List<ResourceMetadata> resources,
      ControlledGcpResourceApi controlledGcpResourceApi,
      UUID workspaceId)
      throws Exception {
    for (ResourceMetadata metadata : resources) {
      if (metadata.getStewardshipType() == StewardshipType.CONTROLLED) {
        switch (metadata.getResourceType()) {
          case GCS_BUCKET:
            ResourceMaker.deleteControlledGcsBucket(
                metadata.getResourceId(), workspaceId, controlledGcpResourceApi);
            break;
          case BIG_QUERY_DATASET:
            controlledGcpResourceApi.deleteBigQueryDataset(workspaceId, metadata.getResourceId());
            break;
          default:
            throw new IllegalStateException(
                String.format(
                    "No cleanup method specified for resource type %s.",
                    metadata.getResourceType()));
        }
      }
    }
  }
}
