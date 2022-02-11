package scripts.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceCommonFields;
import bio.terra.workspace.model.CreateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.model.CreateGcpBigQueryDataTableReferenceRequestBody;
import bio.terra.workspace.model.CreateGcpBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.model.GcpBigQueryDataTableAttributes;
import bio.terra.workspace.model.GcpBigQueryDataTableResource;
import bio.terra.workspace.model.GcpBigQueryDatasetAttributes;
import bio.terra.workspace.model.GcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.ManagedBy;
import bio.terra.workspace.model.PrivateResourceUser;
import bio.terra.workspace.model.ReferenceResourceCommonFields;
import bio.terra.workspace.model.UpdateBigQueryDatasetReferenceRequestBody;
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
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BqDatasetUtils {

  private static final Pattern BQ_DATASET_PATTERN =
      Pattern.compile("^projects/([^/]+)/datasets/([^/]+)$");
  private static final Logger logger = LoggerFactory.getLogger(BqDatasetUtils.class);

  public static final String BQ_EMPLOYEE_TABLE_NAME = "employee";
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
    return makeBigQueryDatasetReference(
        dataset, resourceApi, workspaceId, name, CloningInstructionsEnum.NOTHING);
  }

  public static GcpBigQueryDatasetResource makeBigQueryDatasetReference(
      GcpBigQueryDatasetAttributes dataset,
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceId,
      String name,
      CloningInstructionsEnum cloningInstructions)
      throws ApiException, InterruptedException {

    var body =
        new CreateGcpBigQueryDatasetReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(cloningInstructions)
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
    return makeBigQueryDataTableReference(
        dataTable, resourceApi, workspaceId, name, CloningInstructionsEnum.NOTHING);
  }

  public static GcpBigQueryDataTableResource makeBigQueryDataTableReference(
      GcpBigQueryDataTableAttributes dataTable,
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceId,
      String name,
      CloningInstructionsEnum cloningInstructions)
      throws ApiException, InterruptedException {
    var body =
        new CreateGcpBigQueryDataTableReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(cloningInstructions)
                    .description("Description of " + name)
                    .name(name))
            .dataTable(dataTable);

    return ClientTestUtils.getWithRetryOnException(
        () -> resourceApi.createBigQueryDataTableReference(body, workspaceId));
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

  /**
   * Create two tables with multiple rows in them into the provided dataset. Uses a mixture of
   * streaming and DDL insertion to demonstrate the difference in copy job behavior.
   *
   * <p>This method retries on all GCP exceptions, do not use it for the negative case (where you do
   * not expect a user to be able to create tables in a dataset).
   *
   * @param dataset - empty BigQuery dataset
   * @param ownerUser - User who owns the dataset
   * @param projectId - project that owns the dataset
   * @throws IOException
   * @throws InterruptedException
   */
  public static void populateBigQueryDataset(
      GcpBigQueryDatasetResource dataset, TestUserSpecification ownerUser, String projectId)
      throws IOException, InterruptedException {
    // Add tables to the source dataset
    final BigQuery bigQueryClient = ClientTestUtils.getGcpBigQueryClient(ownerUser, projectId);

    final Schema employeeSchema =
        Schema.of(
            Field.of("employee_id", LegacySQLTypeName.INTEGER),
            Field.of("name", LegacySQLTypeName.STRING));
    final TableId employeeTableId =
        TableId.of(projectId, dataset.getAttributes().getDatasetId(), BQ_EMPLOYEE_TABLE_NAME);

    final TableInfo employeeTableInfo =
        TableInfo.newBuilder(employeeTableId, StandardTableDefinition.of(employeeSchema))
            .setFriendlyName("Employee")
            .build();

    // Wrap the first operation in a wait to allow the IAM permissions to propagate
    final Table createdEmployeeTable =
        ClientTestUtils.getWithRetryOnException(() -> bigQueryClient.create(employeeTableInfo));
    logger.debug("Employee Table: {}", createdEmployeeTable);

    final Table createdDepartmentTable =
        bigQueryClient.create(
            TableInfo.newBuilder(
                    TableId.of(projectId, dataset.getAttributes().getDatasetId(), "department"),
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
    bigQueryClient.insertAll(
        InsertAllRequest.newBuilder(employeeTableInfo)
            .addRow(ImmutableMap.of("employee_id", 103, "name", "Batman"))
            .build());

    // Use DDL to insert rows instead of InsertAllRequest so that data won't
    // be in the streaming buffer where it's un-copyable for up to 90 minutes.
    bigQueryClient.query(
        QueryJobConfiguration.newBuilder(
                "INSERT INTO `"
                    + projectId
                    + "."
                    + dataset.getAttributes().getDatasetId()
                    + "."
                    + BQ_EMPLOYEE_TABLE_NAME
                    + "` (employee_id, name) VALUES("
                    + "101, 'Aquaman'), (102, 'Superman');")
            .build());

    bigQueryClient.query(
        QueryJobConfiguration.newBuilder(
                "INSERT INTO `"
                    + projectId
                    + "."
                    + dataset.getAttributes().getDatasetId()
                    + ".department` (department_id, manager_id, name) "
                    + "VALUES(201, 101, 'ocean'), (202, 102, 'sky');")
            .build());

    // double-check the rows are there
    final TableResult employeeTableResult =
        bigQueryClient.query(
            QueryJobConfiguration.newBuilder(
                    "SELECT * FROM `"
                        + projectId
                        + "."
                        + dataset.getAttributes().getDatasetId()
                        + "."
                        + BQ_EMPLOYEE_TABLE_NAME
                        + "`;")
                .build());
    final long numRows =
        StreamSupport.stream(employeeTableResult.getValues().spliterator(), false).count();
    assertThat(numRows, is(greaterThanOrEqualTo(2L)));
  }

  /**
   * Parse BigQuery dataset attributes from a fully-qualified GCP resource identifier string (e.g.
   * "projects/my-project/datasets/mydataset").
   *
   * <p>This only parses the resource identifier string, it does not check if the provided IDs are
   * real or valid.
   */
  public static GcpBigQueryDatasetAttributes parseBqDataset(String resourceIdentifier) {
    Matcher matcher = BQ_DATASET_PATTERN.matcher(resourceIdentifier);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Resource identifier "
              + resourceIdentifier
              + " does not match expected pattern for BQ dataset");
    }
    return new GcpBigQueryDatasetAttributes()
        .projectId(matcher.group(1))
        .datasetId(matcher.group(2));
  }
}
