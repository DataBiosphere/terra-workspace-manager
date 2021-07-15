package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static scripts.utils.ClientTestUtils.TEST_BUCKET_NAME;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.model.CloneReferencedGcpGcsBucketResourceResult;
import bio.terra.workspace.model.CloneReferencedResourceRequestBody;
import bio.terra.workspace.model.CloneReferencedResourceResult;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.DataRepoSnapshotResource;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.ResourceMetadata;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.DataRepoTestScriptBase;
import scripts.utils.ResourceMaker;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class CloneReferencedResources extends DataRepoTestScriptBase {

  private static final String DATASET_RESOURCE_NAME = "dataset_resource_name";
  private static final Logger logger = LoggerFactory.getLogger(CloneReferencedResources.class);
  private static final String CLONED_BUCKET_RESOURCE_NAME = "a_new_name";
  private static final String CLONED_DATASET_RESOURCE_NAME = "a_cloned_reference";
  private static final String CLONED_DATASET_DESCRIPTION = "Second star to the right.";

  private DataRepoSnapshotResource sourceDataRepoSnapshotReference;
  private GcpGcsBucketResource sourceBucketReference;
  private GcpBigQueryDatasetResource sourceBigQueryDatasetReference;
  private UUID destinationWorkspaceId;
  private ReferencedGcpResourceApi referencedGcpResourceApi;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    ApiClient apiClient = ClientTestUtils.getClientForTestUser(testUsers.get(0), server);
    referencedGcpResourceApi = new ReferencedGcpResourceApi(apiClient);
    String bucketReferenceName = RandomStringUtils.random(16, true, false);
    // create reference to existing test bucket
    sourceBucketReference =
        ResourceMaker
            .makeGcsBucketReference(referencedGcpResourceApi, getWorkspaceId(), bucketReferenceName);

    sourceBigQueryDatasetReference = ResourceMaker.makeBigQueryReference(referencedGcpResourceApi, getWorkspaceId(),
        DATASET_RESOURCE_NAME);

    final String snapshotReferenceName = RandomStringUtils.random(6, true, false);

    // Create a data repo snapshot reference
    sourceDataRepoSnapshotReference = ResourceMaker.makeDataRepoSnapshotReference(referencedGcpResourceApi,
        getWorkspaceId(), snapshotReferenceName, getDataRepoSnapshotId(), getDataRepoInstanceName());

    // create a new workspace with cloud context
    destinationWorkspaceId = UUID.randomUUID();
    final var requestBody =
        new CreateWorkspaceRequestBody()
            .id(destinationWorkspaceId)
            .spendProfile(getSpendProfileId())
            .stage(getStageModel());
    final CreatedWorkspace createdDestinationWorkspace = workspaceApi.createWorkspace(requestBody);
    assertThat(createdDestinationWorkspace.getId(), equalTo(destinationWorkspaceId));

    // create destination cloud context
    String destinationProjectId = CloudContextMaker
        .createGcpCloudContext(destinationWorkspaceId, workspaceApi);
    logger.info("Created destination project {} in workspace {}", destinationProjectId, destinationWorkspaceId);

  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    // clone source reference to destination
    final var cloneBucketReferenceRequestBody = new CloneReferencedResourceRequestBody()
        .cloningInstructions(CloningInstructionsEnum.REFERENCE)
        .name(CLONED_BUCKET_RESOURCE_NAME)
        .destinationWorkspaceId(destinationWorkspaceId);
    logger.info("Cloning GCS Bucket Reference\n\tworkspaceId: {}\n\tresourceId: {}\ninto\n\tworkspaceId: {}",
        sourceBucketReference.getMetadata().getWorkspaceId(),
        sourceBucketReference.getMetadata().getResourceId(),
        destinationWorkspaceId);
    final CloneReferencedGcpGcsBucketResourceResult cloneBucketReferenceResult = referencedGcpResourceApi.cloneGcpGcsBucketReference(cloneBucketReferenceRequestBody,
        getWorkspaceId(),
        sourceBucketReference.getMetadata().getResourceId());
    assertEquals(getWorkspaceId(), cloneBucketReferenceResult.getSourceWorkspaceId());
    assertEquals(StewardshipType.REFERENCED, cloneBucketReferenceResult.getResource().getMetadata().getStewardshipType());
    assertEquals(ResourceType.GCS_BUCKET, cloneBucketReferenceResult.getResource().getMetadata().getResourceType());
    assertEquals(sourceBucketReference.getMetadata().getResourceId(), cloneBucketReferenceResult.getSourceResourceId());
    assertEquals(sourceBucketReference.getMetadata().getDescription(), cloneBucketReferenceResult.getResource().getMetadata().getDescription());
    assertEquals(CLONED_BUCKET_RESOURCE_NAME, cloneBucketReferenceResult.getResource().getMetadata().getName());
    assertEquals(TEST_BUCKET_NAME,
        cloneBucketReferenceResult.getResource().getAttributes().getBucketName());

    final var cloneBigQueryDatasetRequestBody = new CloneReferencedResourceRequestBody()
        .cloningInstructions(CloningInstructionsEnum.REFERENCE)
        .name(CLONED_DATASET_RESOURCE_NAME)
        .description(CLONED_DATASET_DESCRIPTION)
        .destinationWorkspaceId(destinationWorkspaceId);

    logger.info("Cloning BigQuery Dataset Reference\n\tworkspaceId: {}\n\tresourceId: {}\ninto\n\tworkspaceId: {}",
        sourceBigQueryDatasetReference.getMetadata().getWorkspaceId(),
        sourceBigQueryDatasetReference.getMetadata().getResourceId(),
        destinationWorkspaceId);

    final CloneReferencedResourceResult cloneDatasetReferenceResult =
        referencedGcpResourceApi.cloneReferencedResource(cloneBigQueryDatasetRequestBody,
        getWorkspaceId(),
        sourceBigQueryDatasetReference.getMetadata().getResourceId());
    assertEquals(getWorkspaceId(), cloneDatasetReferenceResult.getSourceWorkspaceId());
    final ResourceMetadata clonedDatasetResourceMetadata = cloneDatasetReferenceResult.getResource().getMetadata();
    assertEquals(StewardshipType.REFERENCED, clonedDatasetResourceMetadata.getStewardshipType());
    assertEquals(ResourceType.BIG_QUERY_DATASET, clonedDatasetResourceMetadata.getResourceType());
    assertEquals(sourceBigQueryDatasetReference.getMetadata().getResourceId(), cloneDatasetReferenceResult.getSourceResourceId());
    assertEquals(CLONED_DATASET_RESOURCE_NAME, clonedDatasetResourceMetadata.getName());
    assertEquals(CLONED_DATASET_DESCRIPTION, clonedDatasetResourceMetadata.getDescription());
    assertEquals(sourceBigQueryDatasetReference.getAttributes().getProjectId(),
        cloneDatasetReferenceResult.getResource().getResourceAttributes().getGcpBqDataset().getProjectId());

    final var cloneDataRepoSnapshotReferenceRequestBody = new CloneReferencedResourceRequestBody()
        .cloningInstructions(CloningInstructionsEnum.REFERENCE)
        .destinationWorkspaceId(destinationWorkspaceId);
    logger.info("Cloning Data Repo Snapshot Reference\n\tworkspaceId: {}\n\tresourceId: {}\ninto\n\tworkspaceId: {}",
        sourceDataRepoSnapshotReference.getMetadata().getWorkspaceId(),
        sourceDataRepoSnapshotReference.getMetadata().getResourceId(),
        destinationWorkspaceId);

    final CloneReferencedResourceResult cloneDataRepoSnapshotResult =
        referencedGcpResourceApi.cloneReferencedResource(cloneDataRepoSnapshotReferenceRequestBody,
            sourceDataRepoSnapshotReference.getMetadata().getWorkspaceId(),
            sourceDataRepoSnapshotReference.getMetadata().getResourceId());
    assertEquals(getWorkspaceId(), cloneDataRepoSnapshotResult.getSourceWorkspaceId());
    final ResourceMetadata clonedDataRepoSnapshotResourceMetadata = cloneDataRepoSnapshotResult.getResource().getMetadata();
    assertEquals(StewardshipType.REFERENCED, clonedDataRepoSnapshotResourceMetadata.getStewardshipType());
    assertEquals(ResourceType.DATA_REPO_SNAPSHOT, clonedDataRepoSnapshotResourceMetadata.getResourceType());
    assertEquals(sourceDataRepoSnapshotReference.getMetadata().getResourceId(), cloneDataRepoSnapshotResult.getSourceResourceId());
    assertEquals(sourceDataRepoSnapshotReference.getMetadata().getName(), clonedDataRepoSnapshotResourceMetadata.getName());
    assertEquals(sourceDataRepoSnapshotReference.getMetadata().getDescription(), clonedDataRepoSnapshotResourceMetadata.getDescription());
    assertEquals(sourceDataRepoSnapshotReference.getAttributes().getSnapshot(),
        cloneDataRepoSnapshotResult.getResource().getResourceAttributes().getGcpDataRepoSnapshot().getSnapshot());
    assertEquals(sourceDataRepoSnapshotReference.getAttributes().getInstanceName(),
        cloneDataRepoSnapshotResult.getResource().getResourceAttributes().getGcpDataRepoSnapshot().getInstanceName());
  }
}
