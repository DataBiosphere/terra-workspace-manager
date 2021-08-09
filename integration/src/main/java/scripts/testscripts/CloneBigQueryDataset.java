package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static scripts.utils.GcsBucketTestFixtures.RESOURCE_PREFIX;
import static scripts.utils.ResourceMaker.makeControlledBigQueryDatasetUserShared;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.CloneControlledGcpBigQueryDatasetRequest;
import bio.terra.workspace.model.CloneControlledGcpBigQueryDatasetResult;
import bio.terra.workspace.model.ClonedControlledGcpBigQueryDataset;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.JobReport.StatusEnum;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
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
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class CloneBigQueryDataset extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(CloneBigQueryDataset.class);

  private ControlledGcpResourceApi cloningUserResourceApi;
  private GcpBigQueryDatasetResource sourceDataset;
  private String destinationProjectId;
  private String nameSuffix;
  private String sourceProjectId;
  private TestUserSpecification cloningUser;
  private UUID destinationWorkspaceId;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi sourceOwnerWorkspaceApi)
      throws Exception {
    super.doSetup(testUsers, sourceOwnerWorkspaceApi);
    assertThat(testUsers, hasSize(2));
    // user creating the source resource
    final TestUserSpecification sourceOwnerUser = testUsers.get(0);
    // user cloning the dataset resource
    cloningUser = testUsers.get(1);

    // source workspace project
    sourceProjectId = CloudContextMaker
        .createGcpCloudContext(getWorkspaceId(), sourceOwnerWorkspaceApi);
    logger.info("Created source project {} in workspace {}", sourceProjectId, getWorkspaceId());

    final ControlledGcpResourceApi sourceOwnerResourceApi = ClientTestUtils
        .getControlledGcpResourceClient(sourceOwnerUser, server);
    cloningUserResourceApi = ClientTestUtils.getControlledGcpResourceClient(cloningUser, server);

    // Construct the source dataset
    nameSuffix = UUID.randomUUID().toString();
    final String sourceResourceName = (RESOURCE_PREFIX + nameSuffix).replace('-', '_');
    sourceDataset = makeControlledBigQueryDatasetUserShared(sourceOwnerResourceApi, getWorkspaceId(),
        sourceResourceName);

    // Add tables to the source dataset
    final BigQuery bigQueryClient = ClientTestUtils.getGcpBigQueryClient(sourceOwnerUser, sourceProjectId);

    final Schema employeeSchema = Schema.of(
        Field.of("employee_id", LegacySQLTypeName.INTEGER),
        Field.of("name", LegacySQLTypeName.STRING));
    final TableId employeeTableId = TableId.of(sourceProjectId, sourceDataset.getAttributes().getDatasetId(), "employee");
    final TableInfo employeeTableInfo = TableInfo.newBuilder(employeeTableId, StandardTableDefinition.of(employeeSchema))
        .setFriendlyName("Employee")
        .build();

    final Schema departmentSchema = Schema.of(
        Field.of("department_id", LegacySQLTypeName.INTEGER),
        Field.of("manager_id", LegacySQLTypeName.INTEGER),
        Field.of("name", LegacySQLTypeName.STRING));
    final TableId departmentTableId = TableId.of(sourceProjectId, sourceDataset.getAttributes().getDatasetId(), "department");
    final TableInfo departmentTableInfo = TableInfo.newBuilder(departmentTableId, StandardTableDefinition.of(departmentSchema))
        .setFriendlyName("Department")
        .build();

    final Table createdEmployeeTable = bigQueryClient.create(employeeTableInfo);
    logger.info("Employee Table: {}", createdEmployeeTable);
    final Table createdDepartmentTable = bigQueryClient.create(departmentTableInfo);
    logger.info("Department Table: {}", createdDepartmentTable);

    // Add rows to the tables
    bigQueryClient.insertAll(InsertAllRequest.newBuilder(employeeTableInfo)
        .addRow(ImmutableMap.of("employee_id", 101, "name", "Aquaman"))
        .addRow(ImmutableMap.of("employee_id", 102, "name", "Superman"))
        .build());
    bigQueryClient.insertAll(InsertAllRequest.newBuilder(departmentTableInfo)
        .addRow(ImmutableMap.of("department_id", 201, "manager_id", 101, "name", "ocean"))
        .addRow(ImmutableMap.of("department_id", 202, "manager_id", 102, "name", "sky"))
        .build());

    // Make the cloning user a reader on the existing workspace
    sourceOwnerWorkspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(cloningUser.userEmail), getWorkspaceId(), IamRole.READER);

    // create destination workspace
    final WorkspaceApi cloningUserWorkspaceApi = ClientTestUtils.getWorkspaceClient(cloningUser, server);
    destinationWorkspaceId = UUID.randomUUID();
    final var requestBody =
        new CreateWorkspaceRequestBody()
            .id(destinationWorkspaceId)
            .spendProfile(getSpendProfileId())
            .stage(getStageModel());
    final CreatedWorkspace createdDestinationWorkspace = cloningUserWorkspaceApi.createWorkspace(requestBody);
    assertThat(createdDestinationWorkspace.getId(), equalTo(destinationWorkspaceId));

    // create destination cloud context
    destinationProjectId = CloudContextMaker.createGcpCloudContext(destinationWorkspaceId, cloningUserWorkspaceApi);
    logger.info("Created destination project {} in workspace {}", destinationProjectId, destinationWorkspaceId);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    final String destinationDatasetName = ("clone_" + nameSuffix).replace('-', '_');
    // clone the dataset as the cloning user
    final String clonedDatasetDescription = "Clone of " + destinationDatasetName;
    final String jobId = UUID.randomUUID().toString();
    final CloneControlledGcpBigQueryDatasetRequest cloneRequest = new CloneControlledGcpBigQueryDatasetRequest()
        .cloningInstructions(CloningInstructionsEnum.RESOURCE)
        .description(clonedDatasetDescription)
        .location(null) // keep same
        .destinationWorkspaceId(destinationWorkspaceId)
        .name("MyClonedDataset")
        .jobControl(new JobControl().id(jobId))
        .destinationDatasetName(null); // keep same
    logger.info("Cloning BigQuery dataset\n\tname: {}\n\tresource ID: {}\n\tworkspace: {}\n\t"
        + "projectID: {}\ninto destination \n\tname: {}\n\tworkspace: {}\n\tprojectID: {}",
        sourceDataset.getMetadata().getName(),
        sourceDataset.getMetadata().getResourceId(),
        sourceDataset.getMetadata().getWorkspaceId(),
        sourceProjectId,
        sourceDataset.getMetadata().getName(),
        destinationWorkspaceId,
        destinationProjectId);

    // Submit clone request and poll for async result
    CloneControlledGcpBigQueryDatasetResult cloneResult =
        cloningUserResourceApi.cloneBigQueryDataset(
            cloneRequest,
            sourceDataset.getMetadata().getWorkspaceId(),
            sourceDataset.getMetadata().getResourceId());
    cloneResult = ClientTestUtils.pollWhileRunning(
        cloneResult,
        () -> cloningUserResourceApi.getCloneBigQueryDatasetResult(
            cloneRequest.getDestinationWorkspaceId(),
            cloneRequest.getJobControl().getId()),
        CloneControlledGcpBigQueryDatasetResult::getJobReport,
        Duration.ofSeconds(5));

    assertEquals(StatusEnum.SUCCEEDED, cloneResult.getJobReport().getStatus());
    logger.info("Successfully cloned BigQuery dataset with result {}", cloneResult);
    assertEquals(sourceDataset.getMetadata().getWorkspaceId(), cloneResult.getDataset().getSourceWorkspaceId());
    assertEquals(sourceDataset.getMetadata().getResourceId(), cloneResult.getDataset().getSourceResourceId());

    // unwrap the result one layer at a time
    final ClonedControlledGcpBigQueryDataset clonedControlledGcpBigQueryDataset = cloneResult.getDataset();
    assertEquals(CloningInstructionsEnum.RESOURCE, clonedControlledGcpBigQueryDataset.getEffectiveCloningInstructions());

    final GcpBigQueryDatasetResource clonedResource = clonedControlledGcpBigQueryDataset.getDataset();
    assertEquals(sourceDataset.getMetadata().getCloningInstructions(), clonedResource.getMetadata().getCloningInstructions());
    assertEquals(sourceDataset.getMetadata().getCloudPlatform(), clonedResource.getMetadata().getCloudPlatform());
    assertEquals(sourceDataset.getMetadata().getResourceType(), clonedResource.getMetadata().getResourceType());
    assertEquals(sourceDataset.getMetadata().getStewardshipType(), clonedResource.getMetadata().getStewardshipType());
    assertEquals(sourceDataset.getMetadata().getControlledResourceMetadata().getManagedBy(),
        clonedResource.getMetadata().getControlledResourceMetadata().getManagedBy());
    assertEquals(sourceDataset.getMetadata().getControlledResourceMetadata().getAccessScope(),
        clonedResource.getMetadata().getControlledResourceMetadata().getAccessScope());
    assertNotEquals(sourceDataset.getAttributes().getProjectId(), clonedResource.getAttributes().getProjectId());
    assertEquals(sourceDataset.getAttributes().getDatasetId(), clonedResource.getAttributes().getDatasetId());

    // compare dataset contents
    final BigQuery bigQueryClient = ClientTestUtils.getGcpBigQueryClient(cloningUser, sourceProjectId);
    final QueryJobConfiguration employeeQueryJobConfiguration = QueryJobConfiguration.newBuilder(
        "SELECT * FROM `" +
            destinationProjectId + "." +
            clonedResource.getAttributes().getDatasetId() + ".employee`;")
        .build();
    logger.info("query: {}", employeeQueryJobConfiguration.getQuery());
    final TableResult employeeTableResult = bigQueryClient.query(employeeQueryJobConfiguration);
    logger.info("table result: {}", employeeTableResult);
    final long numRows = StreamSupport.stream(employeeTableResult.getValues().spliterator(), false)
        .count();
    assertEquals(2, numRows);

    final QueryJobConfiguration departmentQueryJobConfiguration = QueryJobConfiguration.newBuilder(
        "SELECT * FROM `"
            + destinationProjectId + "."
            + clonedResource.getAttributes().getDatasetId() + ".department` " +
            "WHERE department_id = 201;"
    ).build();
    final TableResult departmentTableResult = bigQueryClient.query(departmentQueryJobConfiguration);
    final FieldValueList row = StreamSupport.stream(departmentTableResult.getValues().spliterator(), false).findFirst()
        .orElseThrow(() -> new RuntimeException("Can't find first row"));
    final FieldValue nameFieldValue = row.get("name");
    assertEquals("ocean", nameFieldValue.getStringValue());
    final FieldValue managerFieldValue = row.get("manager_id");
    assertEquals(101, managerFieldValue.getLongValue());
  }
}
