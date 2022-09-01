package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static scripts.utils.BqDatasetUtils.makeBigQueryDatasetReference;
import static scripts.utils.BqDatasetUtils.makeControlledBigQueryDatasetUserShared;

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
import bio.terra.workspace.model.ResourceLineageEntry;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.GcsBucketUtils;
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
    controlledGcpResourceApi = ClientTestUtils.getControlledGcpResourceClient(testUser, server);
    referencedGcpResourceApi = ClientTestUtils.getReferencedGcpResourceClient(testUser, server);

    String projectId1 = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
    logger.info("Created project {} in workspace {}", projectId1, getWorkspaceId());

    // In first workspace, create BigQuery dataset
    GcpBigQueryDatasetResource controlledBqDataset =
        makeControlledBigQueryDatasetUserShared(
            controlledGcpResourceApi,
            getWorkspaceId(),
            TestUtils.appendRandomNumber("resource_lineage_bq_dataset"),
            /*datasetId=*/ null,
            CloningInstructionsEnum.DEFINITION);
    controlledBqDatasetWorkspace1ResourceId = controlledBqDataset.getMetadata().getResourceId();

    // In the first workspace, create controlled gcs bucket controlled resource.
    CreatedControlledGcpGcsBucket controlledGcsBucket =
        GcsBucketUtils.makeControlledGcsBucketUserShared(
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
            TestUtils.appendRandomNumber("referenced_bigquery_dataset"));
    referencedBqDatasetWorkspace1ResourceId = referencedBqDataset.getMetadata().getResourceId();

    // Create second workspace
    createWorkspace(workspaceId2, getSpendProfileId(), workspaceApi);
    logger.info("Created second workspace {}", workspaceId2);
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

  private void cloneControlledBqDataset() throws ApiException {
    // Clone controlled bq dataset 1 into second workspace
    CloneControlledGcpBigQueryDatasetResult resource2CloneResult =
        controlledGcpResourceApi.cloneBigQueryDataset(
            new CloneControlledGcpBigQueryDatasetRequest()
                .cloningInstructions(CloningInstructionsEnum.REFERENCE)
                .destinationWorkspaceId(workspaceId2),
            getWorkspaceId(),
            controlledBqDatasetWorkspace1ResourceId);
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
    CloneControlledGcpBigQueryDatasetResult resource3CloneResult =
        controlledGcpResourceApi.cloneBigQueryDataset(
            new CloneControlledGcpBigQueryDatasetRequest()
                .cloningInstructions(CloningInstructionsEnum.DEFINITION)
                .destinationWorkspaceId(workspaceId2)
                // Resource 3 needs different name from resource 2, since they're in same workspace
                .name(UUID.randomUUID().toString()),
            workspaceId2,
            cloneControlledBqDataset2ResourceId);

    // Assert resource lineage on resource 3.
    // Now there are two entries. Add second entry for second clone.
    expectedControlledBqDatasetLineage.add(
        new ResourceLineageEntry()
            .sourceWorkspaceId(workspaceId2)
            .sourceResourceId(cloneControlledBqDataset2ResourceId));
    assertEquals(
        expectedControlledBqDatasetLineage,
        resource3CloneResult.getDataset().getDataset().getMetadata().getResourceLineage());
  }

  private void cloneControlledGcsBucket() throws ApiException {
    // Clone controlled bq dataset 1 into second workspace
    CloneControlledGcpGcsBucketResult resource2CloneResult =
        controlledGcpResourceApi.cloneGcsBucket(
            new CloneControlledGcpGcsBucketRequest()
                .cloningInstructions(CloningInstructionsEnum.DEFINITION)
                .destinationWorkspaceId(workspaceId2)
                // Resource 3 needs different name from resource 2, since they're in same workspace
                .name(UUID.randomUUID().toString()),
            workspaceId2,
            controlledGcsBucketWorkspace1ResourceId);
    var cloneControlledBqDataset2ResourceId =
        resource2CloneResult.getBucket().getBucket().getGcpBucket().getMetadata().getResourceId();
    expectedControlledBqDatasetLineage.add(
        new ResourceLineageEntry()
            .sourceResourceId(controlledBqDatasetWorkspace1ResourceId)
            .sourceWorkspaceId(getWorkspaceId()));
    assertEquals(
        expectedControlledBqDatasetLineage,
        resource2CloneResult
            .getBucket()
            .getBucket()
            .getGcpBucket()
            .getMetadata()
            .getResourceLineage());

    // Clone resource 2 to resource 3, still in second workspace.
    CloneControlledGcpGcsBucketResult resource3CloneResult =
        controlledGcpResourceApi.cloneGcsBucket(
            new CloneControlledGcpGcsBucketRequest()
                .cloningInstructions(CloningInstructionsEnum.DEFINITION)
                .destinationWorkspaceId(workspaceId2)
                // Resource 3 needs different name from resource 2, since they're in same workspace
                .name(UUID.randomUUID().toString()),
            workspaceId2,
            cloneControlledBqDataset2ResourceId);

    // Assert resource lineage on resource 3.
    // Now there are two entries. Add second entry for second clone.
    expectedControlledBqDatasetLineage.add(
        new ResourceLineageEntry()
            .sourceWorkspaceId(workspaceId2)
            .sourceResourceId(cloneControlledBqDataset2ResourceId));
    assertEquals(
        expectedControlledBqDatasetLineage,
        resource3CloneResult
            .getBucket()
            .getBucket()
            .getGcpBucket()
            .getMetadata()
            .getResourceLineage());
  }
}
