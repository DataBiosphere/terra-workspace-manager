package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.UpdateControlledGcpBigQueryDatasetRequestBody;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Dataset;
import com.google.cloud.bigquery.DatasetId;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.StandardTableDefinition;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableInfo;
import com.google.cloud.bigquery.TableResult;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.ResourceMaker;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class ControlledBigQueryDatasetLifecycle extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(ControlledGcsBucketLifecycle.class);

  private static final String DATASET_NAME = "wsmtest_dataset";
  private static final String TABLE_NAME = "wsmtest_table";
  private static final String COLUMN_NAME = "myColumn";

  private TestUserSpecification writer;
  private TestUserSpecification reader;
  // We require a reader, writer, and owner for this test. Arbitrarily, user 0 in the provided list
  // will be the workspace owner (handled in base class), user 1 will be the writer, and user 2 will
  // be the reader.
  private static final int WRITER_USER_INDEX = 1;
  private static final int READER_USER_INDEX = 2;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    // Note the 0th user is the owner of the workspace, pulled out in the super class.
    assertThat(
        "There must be at least three test users defined for this test.",
        testUsers != null && testUsers.size() > 2);
    this.writer = testUsers.get(WRITER_USER_INDEX);
    this.reader = testUsers.get(READER_USER_INDEX);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {

    ControlledGcpResourceApi ownerResourceApi =
        ClientTestUtils.getControlledGcpResourceClient(testUser, server);

    // Add a writer and reader to this workspace
    logger.info(
        "Adding {} as writer and {} as reader to workspace {}",
        writer.userEmail,
        reader.userEmail,
        getWorkspaceId());
    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(writer.userEmail), getWorkspaceId(), IamRole.WRITER);
    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(reader.userEmail), getWorkspaceId(), IamRole.READER);

    // Create a cloud context
    String projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
    logger.info("Created project {}", projectId);

    // Create a shared BigQuery dataset
    GcpBigQueryDatasetResource createdDataset =
        ResourceMaker.makeControlledBigQueryDatasetUserShared(
            ownerResourceApi, getWorkspaceId(), DATASET_NAME);
    UUID resourceId = createdDataset.getMetadata().getResourceId();

    // Retrieve the dataset resource
    logger.info("Retrieving dataset resource id {}", resourceId.toString());
    GcpBigQueryDatasetResource fetchedResource =
        ownerResourceApi.getBigQueryDataset(getWorkspaceId(), resourceId);
    assertEquals(createdDataset, fetchedResource);

    BigQuery ownerBqClient = ClientTestUtils.getGcpBigQueryClient(testUser, projectId);
    BigQuery writerBqClient = ClientTestUtils.getGcpBigQueryClient(writer, projectId);
    BigQuery readerBqClient = ClientTestUtils.getGcpBigQueryClient(reader, projectId);

    // Workspace owner can create a table in this dataset
    Table table = createTable(ownerBqClient, projectId);
    String tableName = table.getTableId().getTable();

    // Workspace reader can read the table
    // This is the reader's first use of cloud APIs after being added to the workspace, so we
    // retry this operation until cloud IAM has properly synced.
    var readTable =
        ClientTestUtils.getWithRetryOnException(() -> readerBqClient.getTable(table.getTableId()));
    assertEquals(table, readTable);
    logger.info("Read table {} as workspace reader", tableName);

    // Workspace reader cannot modify tables
    Table readerUpdatedTable = table.toBuilder().setDescription("A new table description").build();
    assertThrows(
        BigQueryException.class,
        () -> readerBqClient.update(readerUpdatedTable),
        "Workspace reader was able to modify table metadata");
    logger.info("Workspace reader could not modify table {} metadata as expected", tableName);

    // Workspace reader cannot write data to tables
    assertThrows(
        BigQueryException.class,
        () -> insertValueIntoTable(readerBqClient, "some value"),
        "Workspace reader was able to insert data into a table");
    logger.info("Workspace reader could not modify table {} contents as expected", tableName);

    // In contrast, a workspace writer can write data to tables
    String columnValue = "this value lives in a table";
    insertValueIntoTable(writerBqClient, columnValue);
    logger.info("Workspace writer wrote a row to table {}", tableName);

    // Workspace reader can now read the row inserted above
    assertEquals(columnValue, readValueFromTable(readerBqClient));
    logger.info("Workspace reader read that row from table {}", tableName);

    // Workspace writer can update the table metadata
    String newDescription = "Another new table description";
    Table writerUpdatedTable = table.toBuilder().setDescription(newDescription).build();
    Table updatedTable = writerBqClient.update(writerUpdatedTable);
    assertEquals(newDescription, updatedTable.getDescription());
    logger.info("Workspace writer modified table {} metadata", tableName);

    // Workspace owner can update the dataset resource through WSM
    String resourceDescription = "a description for WSM";
    Integer defaultTableLifetimeSec = 5400;
    var updateDatasetRequest =
        new UpdateControlledGcpBigQueryDatasetRequestBody()
            .description(resourceDescription)
            .updateParameters(
                new GcpBigQueryDatasetUpdateParameters()
                    .defaultTableLifetime(defaultTableLifetimeSec));
    ownerResourceApi.updateBigQueryDataset(updateDatasetRequest, getWorkspaceId(), resourceId);
    var datasetAfterUpdate = ownerResourceApi.getBigQueryDataset(getWorkspaceId(), resourceId);
    assertEquals(datasetAfterUpdate.getMetadata().getDescription(), resourceDescription);
    logger.info("Workspace owner updated resource {}", resourceId);

    // However, invalid updates are rejected.
    String invalidName = "!!!invalid_name!!!";
    var invalidUpdateDatasetRequest =
        new UpdateControlledGcpBigQueryDatasetRequestBody().name(invalidName);
    ApiException invalidUpdateEx =
        assertThrows(
            ApiException.class,
            () ->
                ownerResourceApi.updateBigQueryDataset(
                    invalidUpdateDatasetRequest, getWorkspaceId(), resourceId));
    assertEquals(HttpStatusCodes.STATUS_CODE_BAD_REQUEST, invalidUpdateEx.getCode());

    // Cloud metadata matches the updated values
    Dataset cloudDataset = ownerBqClient.getDataset(DatasetId.of(projectId, DATASET_NAME));
    assertEquals(defaultTableLifetimeSec * 1000L, cloudDataset.getDefaultTableLifetime());
    assertNull(cloudDataset.getDefaultPartitionExpirationMs());

    // Workspace writer can delete the table we created earlier
    logger.info("Deleting table {} from dataset {}", table.getTableId().getTable(), DATASET_NAME);
    assertTrue(
        writerBqClient.delete(TableId.of(projectId, DATASET_NAME, table.getTableId().getTable())));

    // Workspace writer cannot delete the dataset directly
    var writerCannotDeleteException =
        assertThrows(BigQueryException.class, () -> writerBqClient.delete(DATASET_NAME));
    assertEquals(HttpStatusCodes.STATUS_CODE_FORBIDDEN, writerCannotDeleteException.getCode());
    // Workspace owner cannot delete the dataset directly
    var ownerCannotDeleteException =
        assertThrows(BigQueryException.class, () -> ownerBqClient.delete(DATASET_NAME));
    assertEquals(HttpStatusCodes.STATUS_CODE_FORBIDDEN, ownerCannotDeleteException.getCode());

    // Workspace owner can delete the dataset through WSM
    ownerResourceApi.deleteBigQueryDataset(getWorkspaceId(), resourceId);
  }

  /**
   * Create and return a table with a single column in this test's dataset. Unlike createDataset,
   * this talks directly to BigQuery and does not go through WSM.
   */
  private Table createTable(BigQuery bigQueryClient, String projectId) {
    var tableId = TableId.of(projectId, DATASET_NAME, TABLE_NAME);
    var tableField = Field.of(COLUMN_NAME, StandardSQLTypeName.STRING);
    var schema = Schema.of(tableField);
    var tableDefinition = StandardTableDefinition.of(schema);
    var tableInfo = TableInfo.of(tableId, tableDefinition);

    logger.info("Creating table {} in dataset {}", TABLE_NAME, DATASET_NAME);
    return bigQueryClient.create(tableInfo);
  }

  /** Insert a single String value into the column/table/dataset specified by constant values. */
  private void insertValueIntoTable(BigQuery bigQueryClient, String value) throws Exception {
    String query =
        String.format("INSERT %s.%s (%s) VALUES(@value)", DATASET_NAME, TABLE_NAME, COLUMN_NAME);
    QueryJobConfiguration queryConfig =
        QueryJobConfiguration.newBuilder(query)
            .addNamedParameter("value", QueryParameterValue.string(value))
            .build();
    runBigQueryJob(bigQueryClient, queryConfig);
  }

  /** Read a single String value from the column/table/dataset specified by constant values. */
  private String readValueFromTable(BigQuery bigQueryClient) throws Exception {
    String query = String.format("SELECT %s FROM %s.%s", COLUMN_NAME, DATASET_NAME, TABLE_NAME);
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
    TableResult result = runBigQueryJob(bigQueryClient, queryConfig);
    assertEquals(1, result.getTotalRows());
    return result.getValues().iterator().next().get(COLUMN_NAME).getStringValue();
  }

  /** Run a BigQuery query to completion and validate it does not return any errors. */
  private TableResult runBigQueryJob(BigQuery bigQueryClient, QueryJobConfiguration jobConfig)
      throws Exception {
    JobId jobId = JobId.of(UUID.randomUUID().toString());
    Job queryJob = bigQueryClient.create(JobInfo.newBuilder(jobConfig).setJobId(jobId).build());
    Job completedJob = queryJob.waitFor();

    // Check for errors
    if (completedJob == null) {
      throw new RuntimeException("Job no longer exists");
    } else if (completedJob.getStatus().getError() != null) {
      throw new RuntimeException(queryJob.getStatus().getError().toString());
    }
    return completedJob.getQueryResults();
  }
}
