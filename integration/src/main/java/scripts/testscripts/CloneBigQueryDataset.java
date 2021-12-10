package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.ResourceMetadata;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.ResourceModifier;
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
  protected void doSetup(
      List<TestUserSpecification> testUsers, WorkspaceApi sourceOwnerWorkspaceApi)
      throws Exception {
    super.doSetup(testUsers, sourceOwnerWorkspaceApi);
    assertThat(testUsers, hasSize(2));
    // user creating the source resource
    final TestUserSpecification sourceOwnerUser = testUsers.get(0);
    // user cloning the dataset resource
    cloningUser = testUsers.get(1);

    // source workspace project
    sourceProjectId =
        CloudContextMaker.createGcpCloudContext(getWorkspaceId(), sourceOwnerWorkspaceApi);
    logger.info("Created source project {} in workspace {}", sourceProjectId, getWorkspaceId());

    final ControlledGcpResourceApi sourceOwnerResourceApi =
        ClientTestUtils.getControlledGcpResourceClient(sourceOwnerUser, server);
    cloningUserResourceApi = ClientTestUtils.getControlledGcpResourceClient(cloningUser, server);

    // Construct the source dataset
    nameSuffix = UUID.randomUUID().toString();
    final String sourceResourceName = (RESOURCE_PREFIX + nameSuffix).replace('-', '_');
    sourceDataset =
        makeControlledBigQueryDatasetUserShared(
            sourceOwnerResourceApi, getWorkspaceId(), sourceResourceName, null);

    ResourceModifier.populateBigQueryDataset(sourceDataset, sourceOwnerUser, sourceProjectId);

    // Make the cloning user a reader on the existing workspace
    sourceOwnerWorkspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(cloningUser.userEmail),
        getWorkspaceId(),
        IamRole.READER);

    // create destination workspace
    final WorkspaceApi cloningUserWorkspaceApi =
        ClientTestUtils.getWorkspaceClient(cloningUser, server);
    destinationWorkspaceId = UUID.randomUUID();
    createWorkspace(destinationWorkspaceId, getSpendProfileId(), cloningUserWorkspaceApi);

    // create destination cloud context
    destinationProjectId =
        CloudContextMaker.createGcpCloudContext(destinationWorkspaceId, cloningUserWorkspaceApi);
    logger.info(
        "Created destination project {} in workspace {}",
        destinationProjectId,
        destinationWorkspaceId);

    // Wait for cloning user to have access to the source dataset before launching into the clone
    BigQuery readerBqClient =
        ClientTestUtils.getGcpBigQueryClient(
            cloningUser, sourceDataset.getAttributes().getProjectId());
    ClientTestUtils.getWithRetryOnException(
        () -> readerBqClient.getDataset(sourceDataset.getAttributes().getDatasetId()));
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    final String destinationDatasetName = ("clone_" + nameSuffix).replace('-', '_');
    // clone the dataset as the cloning user
    final String clonedDatasetDescription = "Clone of " + destinationDatasetName;
    final String jobId = UUID.randomUUID().toString();
    final CloneControlledGcpBigQueryDatasetRequest cloneRequest =
        new CloneControlledGcpBigQueryDatasetRequest()
            .cloningInstructions(CloningInstructionsEnum.RESOURCE)
            .description(clonedDatasetDescription)
            .location(null) // keep same
            .destinationWorkspaceId(destinationWorkspaceId)
            .name("MyClonedDataset")
            .jobControl(new JobControl().id(jobId))
            .destinationDatasetName(null); // keep same
    final ResourceMetadata sourceDatasetMetadata = sourceDataset.getMetadata();
    logger.info(
        "Cloning BigQuery dataset\n\tname: {}\n\tresource ID: {}\n\tworkspace: {}\n\t"
            + "projectID: {}\ninto destination \n\tname: {}\n\tworkspace: {}\n\tprojectID: {}",
        sourceDatasetMetadata.getName(),
        sourceDatasetMetadata.getResourceId(),
        sourceDatasetMetadata.getWorkspaceId(),
        sourceProjectId,
        sourceDatasetMetadata.getName(),
        destinationWorkspaceId,
        destinationProjectId);

    // Submit clone request and poll for async result
    CloneControlledGcpBigQueryDatasetResult cloneResult =
        cloningUserResourceApi.cloneBigQueryDataset(
            cloneRequest,
            sourceDatasetMetadata.getWorkspaceId(),
            sourceDatasetMetadata.getResourceId());
    cloneResult =
        ClientTestUtils.pollWhileRunning(
            cloneResult,
            () ->
                cloningUserResourceApi.getCloneBigQueryDatasetResult(
                    cloneRequest.getDestinationWorkspaceId(), cloneRequest.getJobControl().getId()),
            CloneControlledGcpBigQueryDatasetResult::getJobReport,
            Duration.ofSeconds(5));

    ClientTestUtils.assertJobSuccess(
        "clone BigQuery dataset", cloneResult.getJobReport(), cloneResult.getErrorReport());
    assertEquals(
        sourceDatasetMetadata.getWorkspaceId(), cloneResult.getDataset().getSourceWorkspaceId());
    assertEquals(
        sourceDatasetMetadata.getResourceId(), cloneResult.getDataset().getSourceResourceId());

    // unwrap the result one layer at a time
    final ClonedControlledGcpBigQueryDataset clonedControlledGcpBigQueryDataset =
        cloneResult.getDataset();
    assertEquals(
        CloningInstructionsEnum.RESOURCE,
        clonedControlledGcpBigQueryDataset.getEffectiveCloningInstructions());

    final GcpBigQueryDatasetResource clonedResource =
        clonedControlledGcpBigQueryDataset.getDataset();
    final ResourceMetadata clonedDatasetMetadata = clonedResource.getMetadata();
    assertEquals(
        sourceDatasetMetadata.getCloningInstructions(),
        clonedDatasetMetadata.getCloningInstructions());
    assertEquals(
        sourceDatasetMetadata.getCloudPlatform(), clonedDatasetMetadata.getCloudPlatform());
    assertEquals(sourceDatasetMetadata.getResourceType(), clonedDatasetMetadata.getResourceType());
    assertEquals(
        sourceDatasetMetadata.getStewardshipType(), clonedDatasetMetadata.getStewardshipType());
    assertEquals(
        sourceDatasetMetadata.getControlledResourceMetadata().getManagedBy(),
        clonedDatasetMetadata.getControlledResourceMetadata().getManagedBy());
    assertEquals(
        sourceDatasetMetadata.getControlledResourceMetadata().getAccessScope(),
        clonedDatasetMetadata.getControlledResourceMetadata().getAccessScope());
    assertNotEquals(
        sourceDataset.getAttributes().getProjectId(),
        clonedResource.getAttributes().getProjectId());
    assertEquals(
        sourceDataset.getAttributes().getDatasetId(),
        clonedResource.getAttributes().getDatasetId());

    // compare dataset contents
    final BigQuery bigQueryClient =
        ClientTestUtils.getGcpBigQueryClient(cloningUser, destinationProjectId);
    final QueryJobConfiguration employeeQueryJobConfiguration =
        QueryJobConfiguration.newBuilder(
                "SELECT * FROM `"
                    + destinationProjectId
                    + "."
                    + clonedResource.getAttributes().getDatasetId()
                    + ".employee`;")
            .build();
    final TableResult employeeTableResult = bigQueryClient.query(employeeQueryJobConfiguration);
    final long numRows =
        StreamSupport.stream(employeeTableResult.getValues().spliterator(), false).count();
    assertThat(numRows, is(greaterThanOrEqualTo(2L)));

    final TableResult departmentTableResult =
        bigQueryClient.query(
            QueryJobConfiguration.newBuilder(
                    "SELECT * FROM `"
                        + destinationProjectId
                        + "."
                        + clonedResource.getAttributes().getDatasetId()
                        + ".department` "
                        + "WHERE department_id = 201;")
                .build());
    final FieldValueList row =
        StreamSupport.stream(departmentTableResult.getValues().spliterator(), false)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Can't find expected result row"));
    final FieldValue nameFieldValue = row.get("name");
    assertEquals("ocean", nameFieldValue.getStringValue());
    final FieldValue managerFieldValue = row.get("manager_id");
    assertEquals(101, managerFieldValue.getLongValue());
  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
    // Destination workspace may only be deleted by the user who created it
    final WorkspaceApi destinationWorkspaceApi =
        ClientTestUtils.getWorkspaceClient(cloningUser, server);
    destinationWorkspaceApi.deleteWorkspace(destinationWorkspaceId);
  }
}
