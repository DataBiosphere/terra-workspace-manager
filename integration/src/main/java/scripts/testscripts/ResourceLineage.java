package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static scripts.utils.BqDatasetUtils.makeBigQueryDatasetReference;
import static scripts.utils.BqDatasetUtils.makeControlledBigQueryDatasetUserShared;
import static scripts.utils.ClientTestUtils.assertJobSuccess;
import static scripts.utils.ClientTestUtils.getControlledGcpResourceClient;
import static scripts.utils.ClientTestUtils.getReferencedGcpResourceClient;
import static scripts.utils.CloudContextMaker.createGcpCloudContext;
import static scripts.utils.GcsBucketUtils.makeControlledGcsBucketUserShared;
import static scripts.utils.TestUtils.appendRandomNumber;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloneControlledGcpBigQueryDatasetRequest;
import bio.terra.workspace.model.CloneControlledGcpBigQueryDatasetResult;
import bio.terra.workspace.model.CloneControlledGcpGcsBucketRequest;
import bio.terra.workspace.model.CloneControlledGcpGcsBucketResult;
import bio.terra.workspace.model.CloneReferencedGcpBigQueryDatasetResourceResult;
import bio.terra.workspace.model.CloneReferencedResourceRequestBody;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.ResourceLineageEntry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.TestUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class ResourceLineage extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(ResourceLineage.class);

  private final bio.terra.workspace.model.ResourceLineage expectedReferencedBqDatasetLineage =
      new bio.terra.workspace.model.ResourceLineage();
  private final bio.terra.workspace.model.ResourceLineage expectedControlledBqDatasetLineage =
      new bio.terra.workspace.model.ResourceLineage();
  private final bio.terra.workspace.model.ResourceLineage expectedControlledGcsBucketLineage =
      new bio.terra.workspace.model.ResourceLineage();

  private ControlledGcpResourceApi controlledGcpResourceApi;
  private ReferencedGcpResourceApi referencedGcpResourceApi;
  private UUID controlledBqDatasetWorkspace1ResourceId;
  private UUID controlledGcsBucketWorkspace1ResourceId;
  private UUID referencedBqDatasetWorkspace1ResourceId;

  private final UUID workspaceId2 = UUID.randomUUID();

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);

    TestUserSpecification testUser = testUsers.get(0);
    controlledGcpResourceApi = getControlledGcpResourceClient(testUser, server);
    referencedGcpResourceApi = getReferencedGcpResourceClient(testUser, server);

    String projectId1 = createGcpCloudContext(getWorkspaceId(), workspaceApi);
    logger.info("Created project {} in workspace {}", projectId1, getWorkspaceId());

    // In first workspace, create BigQuery dataset
    GcpBigQueryDatasetResource controlledBqDataset =
        makeControlledBigQueryDatasetUserShared(
            controlledGcpResourceApi,
            getWorkspaceId(),
            appendRandomNumber("resource_lineage_bq_dataset"),
            /*datasetId=*/ null,
            CloningInstructionsEnum.DEFINITION);
    controlledBqDatasetWorkspace1ResourceId = controlledBqDataset.getMetadata().getResourceId();

    // In the first workspace, create controlled gcs bucket controlled resource.
    CreatedControlledGcpGcsBucket controlledGcsBucket =
        makeControlledGcsBucketUserShared(
            controlledGcpResourceApi,
            getWorkspaceId(),
            RandomStringUtils.randomAlphabetic(5),
            CloningInstructionsEnum.DEFINITION);
    controlledGcsBucketWorkspace1ResourceId =
        controlledGcsBucket.getGcpBucket().getMetadata().getResourceId();

    // In first workspace, create BigQuery dataset referenced resource
    GcpBigQueryDatasetResource referencedBqDataset =
        makeBigQueryDatasetReference(
            controlledBqDataset.getAttributes(),
            referencedGcpResourceApi,
            getWorkspaceId(),
            appendRandomNumber("referenced_bigquery_dataset"));
    referencedBqDatasetWorkspace1ResourceId = referencedBqDataset.getMetadata().getResourceId();

    // Create second workspace
    createWorkspace(workspaceId2, getSpendProfileId(), workspaceApi);
    logger.info("Created second workspace {}", workspaceId2);
    String projectId2 = createGcpCloudContext(workspaceId2, workspaceApi);
    logger.info("Created project {} in workspace {}", projectId2, workspaceId2);
  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);

    workspaceApi.deleteWorkspace(workspaceId2);
  }

  @Override
  protected void doUserJourney(
      TestUserSpecification sourceOwnerUser, WorkspaceApi sourceOwnerWorkspaceApi)
      throws Exception {
    cloneReferencedBqDataset();
    cloneControlledBqDataset();
    cloneControlledGcsBucket();
  }

  private void cloneReferencedBqDataset() throws ApiException {
    // Clone referenced resource 1 into second workspace
    CloneReferencedGcpBigQueryDatasetResourceResult resource2CloneResult =
        referencedGcpResourceApi.cloneGcpBigQueryDatasetReference(
            new CloneReferencedResourceRequestBody()
                .cloningInstructions(CloningInstructionsEnum.REFERENCE)
                .destinationWorkspaceId(workspaceId2),
            getWorkspaceId(),
            referencedBqDatasetWorkspace1ResourceId);
    var cloneReferencedBqDataset2ResourceId =
        resource2CloneResult.getResource().getMetadata().getResourceId();
    // Assert resource lineage on resource 2 in second workspace. There is one entry.
    expectedReferencedBqDatasetLineage.add(
        new ResourceLineageEntry()
            .sourceWorkspaceId(getWorkspaceId())
            .sourceResourceId(referencedBqDatasetWorkspace1ResourceId));
    assertEquals(
        expectedReferencedBqDatasetLineage,
        resource2CloneResult.getResource().getMetadata().getResourceLineage());

    // Clone resource 2 to resource 3, still in second workspace.
    CloneReferencedGcpBigQueryDatasetResourceResult resource3CloneResult =
        referencedGcpResourceApi.cloneGcpBigQueryDatasetReference(
            new CloneReferencedResourceRequestBody()
                .cloningInstructions(CloningInstructionsEnum.REFERENCE)
                .destinationWorkspaceId(workspaceId2)
                // Resource 3 needs different name from resource 2, since they're in same workspace
                .name(UUID.randomUUID().toString()),
            workspaceId2,
            cloneReferencedBqDataset2ResourceId);

    // Assert resource lineage on resource 3.
    // Now there are two entries. Add second entry for second clone.
    expectedReferencedBqDatasetLineage.add(
        new ResourceLineageEntry()
            .sourceWorkspaceId(workspaceId2)
            .sourceResourceId(cloneReferencedBqDataset2ResourceId));
    assertEquals(
        expectedReferencedBqDatasetLineage,
        resource3CloneResult.getResource().getMetadata().getResourceLineage());
  }

  private void cloneControlledBqDataset() throws ApiException, InterruptedException {
    // Clone controlled bq dataset 1 into second workspace
    var cloneRequest =
        new CloneControlledGcpBigQueryDatasetRequest()
            .name("clonedControlledDataset2")
            .jobControl(new JobControl().id(UUID.randomUUID().toString()))
            .destinationDatasetName("cloned_controlled_dataset2")
            .cloningInstructions(CloningInstructionsEnum.DEFINITION)
            .destinationWorkspaceId(workspaceId2);
    CloneControlledGcpBigQueryDatasetResult resource2CloneResult =
        controlledGcpResourceApi.cloneBigQueryDataset(
            cloneRequest, getWorkspaceId(), controlledBqDatasetWorkspace1ResourceId);
    resource2CloneResult =
        ClientTestUtils.pollWhileRunning(
            resource2CloneResult,
            () ->
                controlledGcpResourceApi.getCloneBigQueryDatasetResult(
                    getWorkspaceId(), cloneRequest.getJobControl().getId()),
            CloneControlledGcpBigQueryDatasetResult::getJobReport,
            Duration.ofSeconds(5));
    assertJobSuccess(
        "clone controlled BigQuery dataset",
        resource2CloneResult.getJobReport(),
        resource2CloneResult.getErrorReport());
    var cloneControlledBqDataset2ResourceId =
        resource2CloneResult.getDataset().getDataset().getMetadata().getResourceId();
    expectedControlledBqDatasetLineage.add(
        new ResourceLineageEntry()
            .sourceResourceId(controlledBqDatasetWorkspace1ResourceId)
            .sourceWorkspaceId(getWorkspaceId()));
    assertEquals(
        expectedControlledBqDatasetLineage,
        resource2CloneResult.getDataset().getDataset().getMetadata().getResourceLineage());

    // Clone resource 2 to resource 3, still in second workspace.
    var cloneRequest2 =
        new CloneControlledGcpBigQueryDatasetRequest()
            .cloningInstructions(CloningInstructionsEnum.DEFINITION)
            .destinationWorkspaceId(workspaceId2)
            .name("clonedControlledDataset3")
            // Specify the dataset name because there is already a cloned dataset in workspace 2.
            .destinationDatasetName("cloned_controlled_dataset3")
            .jobControl(new JobControl().id(UUID.randomUUID().toString()));
    CloneControlledGcpBigQueryDatasetResult resource3CloneResult =
        controlledGcpResourceApi.cloneBigQueryDataset(
            cloneRequest2, workspaceId2, cloneControlledBqDataset2ResourceId);
    System.out.println(resource3CloneResult.getJobReport().getStatus());
    resource3CloneResult =
        ClientTestUtils.pollWhileRunning(
            resource3CloneResult,
            () ->
                controlledGcpResourceApi.getCloneBigQueryDatasetResult(
                    getWorkspaceId(), cloneRequest2.getJobControl().getId()),
            CloneControlledGcpBigQueryDatasetResult::getJobReport,
            Duration.ofSeconds(5));
    // Assert resource lineage on resource 3.
    // Now there are two entries. Add second entry for second clone.
    expectedControlledBqDatasetLineage.add(
        new ResourceLineageEntry()
            .sourceWorkspaceId(workspaceId2)
            .sourceResourceId(cloneControlledBqDataset2ResourceId));
    assertJobSuccess(
        "clone BigQuery dataset",
        resource3CloneResult.getJobReport(),
        resource3CloneResult.getErrorReport());
    assertEquals(
        expectedControlledBqDatasetLineage,
        resource3CloneResult.getDataset().getDataset().getMetadata().getResourceLineage());
  }

  private void cloneControlledGcsBucket() throws ApiException, InterruptedException {
    // Clone controlled bq dataset 1 into second workspace
    var cloneRequest1 =
        new CloneControlledGcpGcsBucketRequest()
            .cloningInstructions(CloningInstructionsEnum.DEFINITION)
            .destinationWorkspaceId(workspaceId2)
            .name("clonedControlledGcsBucket2")
            .bucketName(TestUtils.appendRandomNumber("cloned-controlled-bucket2"))
            .jobControl(new JobControl().id(UUID.randomUUID().toString()));
    CloneControlledGcpGcsBucketResult resource2CloneResult =
        controlledGcpResourceApi.cloneGcsBucket(
            cloneRequest1, workspaceId2, controlledGcsBucketWorkspace1ResourceId);
    ClientTestUtils.pollWhileRunning(
        resource2CloneResult,
        () ->
            controlledGcpResourceApi.getCloneGcsBucketResult(
                getWorkspaceId(), cloneRequest1.getJobControl().getId()),
        CloneControlledGcpGcsBucketResult::getJobReport,
        Duration.ofSeconds(5));
    assertJobSuccess(
        "clone controlled gcs bucket",
        resource2CloneResult.getJobReport(),
        resource2CloneResult.getErrorReport());
    var cloneControlledBqDataset2ResourceId =
        resource2CloneResult.getBucket().getBucket().getGcpBucket().getMetadata().getResourceId();
    expectedControlledGcsBucketLineage.add(
        new ResourceLineageEntry()
            .sourceResourceId(controlledBqDatasetWorkspace1ResourceId)
            .sourceWorkspaceId(getWorkspaceId()));
    assertEquals(
        expectedControlledGcsBucketLineage,
        resource2CloneResult
            .getBucket()
            .getBucket()
            .getGcpBucket()
            .getMetadata()
            .getResourceLineage());

    // Clone resource 2 to resource 3, still in second workspace.
    var cloneRequest2 =
        new CloneControlledGcpGcsBucketRequest()
            .cloningInstructions(CloningInstructionsEnum.DEFINITION)
            .destinationWorkspaceId(workspaceId2)
            .name("myClonedBucket3")
            .bucketName(TestUtils.appendRandomNumber("cloned-controlled-bucket3"))
            .jobControl(new JobControl().id(UUID.randomUUID().toString()));
    CloneControlledGcpGcsBucketResult resource3CloneResult =
        controlledGcpResourceApi.cloneGcsBucket(
            cloneRequest2, workspaceId2, cloneControlledBqDataset2ResourceId);
    ClientTestUtils.pollWhileRunning(
        resource3CloneResult,
        () ->
            controlledGcpResourceApi.getCloneGcsBucketResult(
                getWorkspaceId(), cloneRequest2.getJobControl().getId()),
        CloneControlledGcpGcsBucketResult::getJobReport,
        Duration.ofSeconds(5));

    assertJobSuccess(
        "Clone bucket", resource3CloneResult.getJobReport(), resource3CloneResult.getErrorReport());
    // Assert resource lineage on resource 3.
    // Now there are two entries. Add second entry for second clone.
    expectedControlledGcsBucketLineage.add(
        new ResourceLineageEntry()
            .sourceWorkspaceId(workspaceId2)
            .sourceResourceId(cloneControlledBqDataset2ResourceId));
    assertEquals(
        expectedControlledGcsBucketLineage,
        resource3CloneResult
            .getBucket()
            .getBucket()
            .getGcpBucket()
            .getMetadata()
            .getResourceLineage());
  }
}
