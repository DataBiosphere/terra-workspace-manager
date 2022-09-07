package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static scripts.utils.BqDatasetUtils.makeControlledBigQueryDatasetUserShared;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.CloneReferencedGcpBigQueryDatasetResourceResult;
import bio.terra.workspace.model.CloneReferencedResourceRequestBody;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.ResourceLineageEntry;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.BqDatasetUtils;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.TestUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class ResourceLineage extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(ResourceLineage.class);

  private ControlledGcpResourceApi controlledGcpResourceApi;
  private ReferencedGcpResourceApi referencedGcpResourceApi;
  private UUID bqDatasetWorkspace1ResourceId;

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

    // In first workspace, create BigQuery dataset referenced resource
    GcpBigQueryDatasetResource referencedBqDataset =
        BqDatasetUtils.makeBigQueryDatasetReference(
            controlledBqDataset.getAttributes(),
            referencedGcpResourceApi,
            getWorkspaceId(),
            TestUtils.appendRandomNumber("referenced_bigquery_dataset"));
    bqDatasetWorkspace1ResourceId = referencedBqDataset.getMetadata().getResourceId();

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
    // Clone resource 1 into second workspace
    CloneReferencedGcpBigQueryDatasetResourceResult resource2CloneResult =
        referencedGcpResourceApi.cloneGcpBigQueryDatasetReference(
            new CloneReferencedResourceRequestBody()
                .cloningInstructions(CloningInstructionsEnum.REFERENCE)
                .destinationWorkspaceId(workspaceId2),
            getWorkspaceId(),
            bqDatasetWorkspace1ResourceId);
    UUID resource2Id = resource2CloneResult.getResource().getMetadata().getResourceId();

    // Assert resource lineage on resource 2 in second workspace. There is one entry.
    bio.terra.workspace.model.ResourceLineage expectedResourceLineage =
        new bio.terra.workspace.model.ResourceLineage();
    expectedResourceLineage.add(
        new ResourceLineageEntry()
            .sourceWorkspaceId(getWorkspaceId())
            .sourceResourceId(bqDatasetWorkspace1ResourceId));
    assertBqDatasetResourceLineage(resource2Id, expectedResourceLineage);

    // Clone resource 2 to resource 3, still in second workspace.
    CloneReferencedGcpBigQueryDatasetResourceResult resource3CloneResult =
        referencedGcpResourceApi.cloneGcpBigQueryDatasetReference(
            new CloneReferencedResourceRequestBody()
                .cloningInstructions(CloningInstructionsEnum.REFERENCE)
                .destinationWorkspaceId(workspaceId2)
                // Resource 3 needs different name from resource 2, since they're in same workspace
                .name(UUID.randomUUID().toString()),
            workspaceId2,
            resource2Id);
    UUID resource3Id = resource3CloneResult.getResource().getMetadata().getResourceId();

    // Assert resource lineage on resource 3.
    // Now there are two entries. Add second entry for second clone.
    expectedResourceLineage.add(
        new ResourceLineageEntry().sourceWorkspaceId(workspaceId2).sourceResourceId(resource2Id));
    assertBqDatasetResourceLineage(resource3Id, expectedResourceLineage);
  }

  private void assertBqDatasetResourceLineage(
      UUID resourceToCheckId, bio.terra.workspace.model.ResourceLineage expected) throws Exception {
    GcpBigQueryDatasetResource gotResource =
        referencedGcpResourceApi.getBigQueryDatasetReference(workspaceId2, resourceToCheckId);
    assertEquals(expected, gotResource.getMetadata().getResourceLineage());
  }
}
