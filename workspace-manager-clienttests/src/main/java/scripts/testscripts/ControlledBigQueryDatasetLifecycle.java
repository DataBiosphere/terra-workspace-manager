package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceCommonFields;
import bio.terra.workspace.model.CreateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.model.CreatedControlledGcpBigQueryDataset;
import bio.terra.workspace.model.GcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ManagedBy;
import bio.terra.workspace.model.UpdateControlledResourceRequestBody;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.StandardTableDefinition;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableInfo;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class ControlledBigQueryDatasetLifecycle extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(ControlledGcsBucketLifecycle.class);

  private static final String DATASET_LOCATION = "US-CENTRAL1";
  private static final String DATASET_NAME = "wsmtest_dataset";
  private static final String TABLE_NAME = "wsmtest_table";
  private static final String RESOURCE_NAME = "wsmtestresource";

  private TestUserSpecification writer;
  private TestUserSpecification reader;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    // Note the 0th user is the owner of the workspace, pulled out in the super class.
    assertThat(
        "There must be at least three test users defined for this test.",
        testUsers != null && testUsers.size() > 2);
    this.writer = testUsers.get(1);
    this.reader = testUsers.get(2);
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

    logger.info("Waiting 15s for permissions to propagate");
    TimeUnit.SECONDS.sleep(15);

    // Create a cloud context
    String projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
    logger.info("Created project {}", projectId);

    // Create a shared BigQuery dataset
    CreatedControlledGcpBigQueryDataset createdDataset = createDataset(ownerResourceApi);
    UUID resourceId = createdDataset.getResourceId();

    // Retrieve the dataset resource
    logger.info("Retrieving dataset resource id {}", resourceId.toString());
    GcpBigQueryDatasetResource fetchedResource =
        ownerResourceApi.getBigQueryDataset(getWorkspaceId(), resourceId);
    assertEquals(createdDataset.getBigQueryDataset(), fetchedResource);

    BigQuery ownerBqClient = ClientTestUtils.getGcpBigQueryClient(testUser, projectId);
    BigQuery writerBqClient = ClientTestUtils.getGcpBigQueryClient(writer, projectId);
    BigQuery readerBqClient = ClientTestUtils.getGcpBigQueryClient(reader, projectId);

    // Workspace owner can create a table in this dataset
    Table table = createTable(ownerBqClient, projectId);
    String tableName = table.getTableId().getTable();

    // Workspace reader can read the table
    var readTable = readerBqClient.getTable(table.getTableId());
    assertEquals(table, readTable);
    logger.info("Read table {} as workspace reader", tableName);

    // Workspace reader cannot modify tables
    Table readerUpdatedTable = table.toBuilder().setDescription("A new table description").build();
    assertThrows(
        BigQueryException.class,
        () -> readerBqClient.update(readerUpdatedTable),
        "Workspace reader was able to modify a table");
    logger.info("Workspace reader could not modify table {} as expected", tableName);

    // Workspace writer can update the table
    String newDescription = "Another new table description";
    Table writerUpdatedTable = table.toBuilder().setDescription(newDescription).build();
    Table updatedTable = writerBqClient.update(writerUpdatedTable);
    assertEquals(newDescription, updatedTable.getDescription());
    logger.info("Workspace writer modified table {}", tableName);

    // Workspace owner can update the dataset resource through WSM
    String resourceDescription = "a description for WSM";
    var updateDatasetRequest =
        new UpdateControlledResourceRequestBody().description(resourceDescription);
    ownerResourceApi.updateBigQueryDataset(updateDatasetRequest, getWorkspaceId(), resourceId);
    var datasetAfterUpdate = ownerResourceApi.getBigQueryDataset(getWorkspaceId(), resourceId);
    assertEquals(datasetAfterUpdate.getMetadata().getDescription(), resourceDescription);
    logger.info("Workspace owner updated resource {}", resourceId);

    // Workspace writer can delete the table we created earlier
    logger.info("Deleting table {} from dataset {}", table.getTableId().getTable(), DATASET_NAME);
    assertTrue(
        writerBqClient.delete(TableId.of(projectId, DATASET_NAME, table.getTableId().getTable())));

    // TODO(PF-735): test that neither owners or writers can directly delete the BQ dataset. They
    // can because of the broad project-level permissions we grant.

    // Workspace owner can delete the dataset through WSM
    ownerResourceApi.deleteBigQueryDataset(getWorkspaceId(), resourceId);
  }

  /** Create and return a controlled BigQuery dataset resource. */
  private CreatedControlledGcpBigQueryDataset createDataset(ControlledGcpResourceApi resourceApi)
      throws Exception {
    var creationParameters =
        new GcpBigQueryDatasetCreationParameters()
            .datasetId(DATASET_NAME)
            .location(DATASET_LOCATION);
    var commonParameters =
        new ControlledResourceCommonFields()
            .name(RESOURCE_NAME)
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .accessScope(AccessScope.SHARED_ACCESS)
            .managedBy(ManagedBy.USER);
    var requestBody =
        new CreateControlledGcpBigQueryDatasetRequestBody()
            .dataset(creationParameters)
            .common(commonParameters);
    logger.info("Creating dataset {} in workspace {}", DATASET_NAME, getWorkspaceId());
    return resourceApi.createBigQueryDataset(requestBody, getWorkspaceId());
  }
  /**
   * Create and return a table with a single column in this test's dataset. Unlike createDataset,
   * this talks directly on the BigQuery and does not go through WSM.
   */
  private Table createTable(BigQuery bigQueryClient, String projectId) {
    var tableId = TableId.of(projectId, DATASET_NAME, TABLE_NAME);
    var tableField = Field.of("myFieldName", StandardSQLTypeName.STRING);
    var schema = Schema.of(tableField);
    var tableDefinition = StandardTableDefinition.of(schema);
    var tableInfo = TableInfo.of(tableId, tableDefinition);

    logger.info("Creating table {} in dataset {}", TABLE_NAME, DATASET_NAME);
    return bigQueryClient.create(tableInfo);
  }
}
