package scripts.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static scripts.utils.ClientTestUtils.TEST_BQ_DATASET_NAME;
import static scripts.utils.ClientTestUtils.TEST_BQ_DATASET_PROJECT;
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
import bio.terra.workspace.model.CreateControlledGcpBigQueryDatasetRequestBody;
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
import bio.terra.workspace.model.GcpBigQueryDatasetCreationParameters;
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
import bio.terra.workspace.model.PrivateResourceIamRoles;
import bio.terra.workspace.model.PrivateResourceUser;
import bio.terra.workspace.model.ReferenceResourceCommonFields;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardTableDefinition;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableInfo;
import com.google.cloud.bigquery.TableResult;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                    .description("Description of " + name)
                    .name(name))
            .dataset(
                new GcpBigQueryDatasetAttributes()
                    .datasetId(TEST_BQ_DATASET_NAME)
                    .projectId(TEST_BQ_DATASET_PROJECT));

    return resourceApi.createBigQueryDatasetReference(body, workspaceId);
  }

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

  public static GcpGcsBucketResource makeGcsBucketReference(
      ReferencedGcpResourceApi resourceApi, UUID workspaceId, String name) throws ApiException {

    var body =
        new CreateGcpGcsBucketReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(CloningInstructionsEnum.NOTHING)
                    .description("Description of " + name)
                    .name(name))
            .bucket(new GcpGcsBucketAttributes().bucketName(TEST_BUCKET_NAME));

    return resourceApi.createBucketReference(body, workspaceId);
  }

  public static CreatedControlledGcpGcsBucket makeControlledGcsBucketUserShared(
      ControlledGcpResourceApi resourceApi, UUID workspaceId, String name,
      CloningInstructionsEnum cloningInstructions) throws Exception {

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
      ControlledGcpResourceApi resourceApi, UUID workspaceId, String name,
      String privateResourceUserEmail, PrivateResourceIamRoles privateResourceRoles,
      CloningInstructionsEnum cloningInstructions) throws Exception {
    String bucketName = ClientTestUtils.generateCloudResourceName();
    var body =
        new CreateControlledGcpGcsBucketRequestBody()
            .common(
                new ControlledResourceCommonFields()
                    .accessScope(AccessScope.PRIVATE_ACCESS)
                    .privateResourceUser(new PrivateResourceUser()
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
      ControlledGcpResourceApi resourceApi, UUID workspaceId, String name,
      String privateResourceUserEmail, PrivateResourceIamRoles privateResourceRoles) throws Exception {
    return  makeControlledGcsBucketUserPrivate(
         resourceApi,  workspaceId, name, privateResourceUserEmail, privateResourceRoles, CloningInstructionsEnum.NOTHING);
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
      ControlledGcpResourceApi resourceApi, UUID workspaceId, String datasetId,
      @Nullable CloningInstructionsEnum cloningInstructions) throws Exception {

    var body =
        new CreateControlledGcpBigQueryDatasetRequestBody()
            .common(
                new ControlledResourceCommonFields()
                    .accessScope(AccessScope.SHARED_ACCESS)
                    .managedBy(ManagedBy.USER)
                    .cloningInstructions(Optional.ofNullable(cloningInstructions).orElse(CloningInstructionsEnum.NOTHING))
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

  /**
   * Create two tables with multiple rows in them into the provided dataset.
   * Uses a mixture of streaming and DDL insertion to demonstrate the difference
   * in copy job behavior.
   * @param dataset - empty BigQuery dataset
   * @param ownerUser - User who owns the dataset
   * @param projectId - project that owns the dataset
   * @throws IOException
   * @throws InterruptedException
   */
  public static void populateBigQueryDataset(
      GcpBigQueryDatasetResource dataset,
      TestUserSpecification ownerUser,
      String projectId)
      throws IOException, InterruptedException {
    // Add tables to the source dataset
    final BigQuery bigQueryClient = ClientTestUtils.getGcpBigQueryClient(ownerUser, projectId);

    final Schema employeeSchema = Schema.of(
        Field.of("employee_id", LegacySQLTypeName.INTEGER),
        Field.of("name", LegacySQLTypeName.STRING));
    final TableId employeeTableId = TableId.of(projectId, dataset.getAttributes().getDatasetId(), "employee");

    final TableInfo employeeTableInfo = TableInfo
        .newBuilder(employeeTableId, StandardTableDefinition.of(employeeSchema))
        .setFriendlyName("Employee")
        .build();
    final Table createdEmployeeTable = bigQueryClient.create(
        employeeTableInfo);
    logger.debug("Employee Table: {}", createdEmployeeTable);

    final Table createdDepartmentTable = bigQueryClient.create(
        TableInfo.newBuilder(TableId.of(projectId, dataset.getAttributes().getDatasetId(), "department"),
        StandardTableDefinition.of(
            Schema.of(
                Field.of("department_id", LegacySQLTypeName.INTEGER),
                Field.of("manager_id", LegacySQLTypeName.INTEGER),
                Field.of("name", LegacySQLTypeName.STRING))))
        .setFriendlyName("Department")
        .build());
    logger.debug("Department Table: {}", createdDepartmentTable);

    // Add rows to the tables

    // Stream insert one row to check the error handling/warning. This row may not be copied. (If
    // the stream happens after the DDL insert, sometimes it gets copied).
    bigQueryClient.insertAll(InsertAllRequest.newBuilder(employeeTableInfo)
        .addRow(ImmutableMap.of("employee_id", 103, "name", "Batman"))
        .build());

    // Use DDL to insert rows instead of InsertAllRequest so that data won't
    // be in the streaming buffer where it's un-copyable for up to 90 minutes.
    bigQueryClient.query(QueryJobConfiguration.newBuilder(
        "INSERT INTO `" + projectId + "." + dataset.getAttributes().getDatasetId()
            + ".employee` (employee_id, name) VALUES("
            + "101, 'Aquaman'), (102, 'Superman');")
        .build());

    bigQueryClient.query(QueryJobConfiguration.newBuilder(
        "INSERT INTO `" + projectId + "." + dataset.getAttributes().getDatasetId()
            + ".department` (department_id, manager_id, name) "
            + "VALUES(201, 101, 'ocean'), (202, 102, 'sky');")
        .build());

    // double-check the rows are there
    final TableResult employeeTableResult = bigQueryClient.query(QueryJobConfiguration.newBuilder(
        "SELECT * FROM `" +
            projectId + "." +
            dataset.getAttributes().getDatasetId() + ".employee`;")
        .build());
    final long numRows = StreamSupport.stream(employeeTableResult.getValues().spliterator(), false)
        .count();
    assertThat(numRows, is(greaterThanOrEqualTo(2L)));
  }
}
