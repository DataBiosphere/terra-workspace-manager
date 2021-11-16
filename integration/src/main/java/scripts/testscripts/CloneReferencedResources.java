package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static scripts.utils.ClientTestUtils.TEST_BUCKET_NAME;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloneReferencedGcpDataRepoSnapshotResourceResult;
import bio.terra.workspace.model.CloneReferencedGcpGcsBucketResourceResult;
import bio.terra.workspace.model.CloneReferencedResourceRequestBody;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.DataRepoSnapshotResource;
import bio.terra.workspace.model.GcpBigQueryDataTableResource;
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
import scripts.utils.DataRepoTestScriptBase;
import scripts.utils.ResourceMaker;

public class CloneReferencedResources extends DataRepoTestScriptBase {

  private static final String DATASET_RESOURCE_NAME = "dataset_resource_name";
  private static final String DATA_TABLE_RESOURCE_NAME = "data_table_resource_name";
  private static final Logger logger = LoggerFactory.getLogger(CloneReferencedResources.class);
  private static final String CLONED_BUCKET_RESOURCE_NAME = "a_new_name";
  private static final String CLONED_DATASET_RESOURCE_NAME = "a_cloned_reference";
  private static final String CLONED_DATASET_DESCRIPTION = "Second star to the right.";
  private static final String CLONED_DATA_TABLE_REFERENCE = "a_cloned_data_table_reference";
  private static final String CLONED_DATA_TABLE_DESCRIPTION = "a cloned data table reference";

  private DataRepoSnapshotResource sourceDataRepoSnapshotReference;
  private GcpGcsBucketResource sourceBucketReference;
  private GcpBigQueryDatasetResource sourceBigQueryDatasetReference;
  private GcpBigQueryDataTableResource sourceBigQueryDataTableReference;
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
        ResourceMaker.makeGcsBucketReference(
            referencedGcpResourceApi, getWorkspaceId(), bucketReferenceName);

    sourceBigQueryDatasetReference =
        ResourceMaker.makeBigQueryReference(
            referencedGcpResourceApi, getWorkspaceId(), DATASET_RESOURCE_NAME);

    sourceBigQueryDataTableReference =
        ResourceMaker.makeBigQueryDataTableReference(
            referencedGcpResourceApi, getWorkspaceId(), DATA_TABLE_RESOURCE_NAME);

    final String snapshotReferenceName = RandomStringUtils.random(6, true, false);

    // Create a data repo snapshot reference
    sourceDataRepoSnapshotReference =
        ResourceMaker.makeDataRepoSnapshotReference(
            referencedGcpResourceApi,
            getWorkspaceId(),
            snapshotReferenceName,
            getDataRepoSnapshotId(),
            getDataRepoInstanceName());

    // create a new workspace with cloud context
    destinationWorkspaceId = UUID.randomUUID();
    createWorkspace(destinationWorkspaceId, getSpendProfileId(), workspaceApi);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    // clone source reference to destination
    final var cloneBucketReferenceRequestBody =
        new CloneReferencedResourceRequestBody()
            .cloningInstructions(CloningInstructionsEnum.REFERENCE)
            .name(CLONED_BUCKET_RESOURCE_NAME)
            .destinationWorkspaceId(destinationWorkspaceId);
    logger.info(
        "Cloning GCS Bucket Reference\n\tworkspaceId: {}\n\tresourceId: {}\ninto\n\tworkspaceId: {}",
        sourceBucketReference.getMetadata().getWorkspaceId(),
        sourceBucketReference.getMetadata().getResourceId(),
        destinationWorkspaceId);
    final CloneReferencedGcpGcsBucketResourceResult cloneBucketReferenceResult =
        referencedGcpResourceApi.cloneGcpGcsBucketReference(
            cloneBucketReferenceRequestBody,
            getWorkspaceId(),
            sourceBucketReference.getMetadata().getResourceId());
    assertEquals(getWorkspaceId(), cloneBucketReferenceResult.getSourceWorkspaceId());
    assertEquals(
        StewardshipType.REFERENCED,
        cloneBucketReferenceResult.getResource().getMetadata().getStewardshipType());
    assertEquals(
        ResourceType.GCS_BUCKET,
        cloneBucketReferenceResult.getResource().getMetadata().getResourceType());
    assertEquals(
        sourceBucketReference.getMetadata().getResourceId(),
        cloneBucketReferenceResult.getSourceResourceId());
    assertEquals(
        sourceBucketReference.getMetadata().getDescription(),
        cloneBucketReferenceResult.getResource().getMetadata().getDescription());
    assertEquals(
        CLONED_BUCKET_RESOURCE_NAME,
        cloneBucketReferenceResult.getResource().getMetadata().getName());
    assertEquals(
        TEST_BUCKET_NAME, cloneBucketReferenceResult.getResource().getAttributes().getBucketName());

    final var cloneBigQueryDatasetRequestBody =
        new CloneReferencedResourceRequestBody()
            .cloningInstructions(CloningInstructionsEnum.REFERENCE)
            .name(CLONED_DATASET_RESOURCE_NAME)
            .description(CLONED_DATASET_DESCRIPTION)
            .destinationWorkspaceId(destinationWorkspaceId);

    logger.info(
        "Cloning BigQuery Dataset Reference\n\tworkspaceId: {}\n\tresourceId: {}\ninto\n\tworkspaceId: {}",
        sourceBigQueryDatasetReference.getMetadata().getWorkspaceId(),
        sourceBigQueryDatasetReference.getMetadata().getResourceId(),
        destinationWorkspaceId);

    final var cloneDatasetReferenceResult =
        referencedGcpResourceApi.cloneGcpBigQueryDatasetReference(
            cloneBigQueryDatasetRequestBody,
            getWorkspaceId(),
            sourceBigQueryDatasetReference.getMetadata().getResourceId());
    assertEquals(getWorkspaceId(), cloneDatasetReferenceResult.getSourceWorkspaceId());
    final ResourceMetadata clonedDatasetResourceMetadata =
        cloneDatasetReferenceResult.getResource().getMetadata();
    assertEquals(StewardshipType.REFERENCED, clonedDatasetResourceMetadata.getStewardshipType());
    assertEquals(ResourceType.BIG_QUERY_DATASET, clonedDatasetResourceMetadata.getResourceType());
    assertEquals(
        sourceBigQueryDatasetReference.getMetadata().getResourceId(),
        cloneDatasetReferenceResult.getSourceResourceId());
    assertEquals(CLONED_DATASET_RESOURCE_NAME, clonedDatasetResourceMetadata.getName());
    assertEquals(CLONED_DATASET_DESCRIPTION, clonedDatasetResourceMetadata.getDescription());
    assertEquals(
        sourceBigQueryDatasetReference.getAttributes().getProjectId(),
        cloneDatasetReferenceResult.getResource().getAttributes().getProjectId());

    final var cloneBigQueryDataTableRequestBody =
        new CloneReferencedResourceRequestBody()
            .cloningInstructions(CloningInstructionsEnum.REFERENCE)
            .name(CLONED_DATA_TABLE_REFERENCE)
            .description(CLONED_DATA_TABLE_DESCRIPTION)
            .destinationWorkspaceId(destinationWorkspaceId);

    logger.info(
        "Cloning BigQuery Data table Reference\n\tworkspaceId: {}\n\tresourceId: {}\ninto\n\tworkspaceId: {}",
        sourceBigQueryDataTableReference.getMetadata().getWorkspaceId(),
        sourceBigQueryDataTableReference.getMetadata().getResourceId(),
        destinationWorkspaceId);

    final var cloneDataTableReferenceResult =
        referencedGcpResourceApi.cloneGcpBigQueryDataTableReference(
            cloneBigQueryDataTableRequestBody,
            getWorkspaceId(),
            sourceBigQueryDataTableReference.getMetadata().getResourceId());
    assertEquals(getWorkspaceId(), cloneDataTableReferenceResult.getSourceWorkspaceId());
    final ResourceMetadata clonedDataTableResourceMetadata =
        cloneDataTableReferenceResult.getResource().getMetadata();
    assertEquals(StewardshipType.REFERENCED, clonedDataTableResourceMetadata.getStewardshipType());
    assertEquals(
        ResourceType.BIG_QUERY_DATATABLE, clonedDataTableResourceMetadata.getResourceType());
    assertEquals(
        sourceBigQueryDataTableReference.getMetadata().getResourceId(),
        cloneDataTableReferenceResult.getSourceResourceId());
    assertEquals(CLONED_DATA_TABLE_REFERENCE, clonedDataTableResourceMetadata.getName());
    assertEquals(CLONED_DATA_TABLE_DESCRIPTION, clonedDataTableResourceMetadata.getDescription());
    assertEquals(
        sourceBigQueryDataTableReference.getAttributes().getProjectId(),
        cloneDataTableReferenceResult.getResource().getAttributes().getProjectId());

    final var cloneDataRepoSnapshotReferenceRequestBody =
        new CloneReferencedResourceRequestBody()
            .cloningInstructions(CloningInstructionsEnum.REFERENCE)
            .destinationWorkspaceId(destinationWorkspaceId);
    logger.info(
        "Cloning Data Repo Snapshot Reference\n\tworkspaceId: {}\n\tresourceId: {}\ninto\n\tworkspaceId: {}",
        sourceDataRepoSnapshotReference.getMetadata().getWorkspaceId(),
        sourceDataRepoSnapshotReference.getMetadata().getResourceId(),
        destinationWorkspaceId);

    final CloneReferencedGcpDataRepoSnapshotResourceResult cloneDataRepoSnapshotResult =
        referencedGcpResourceApi.cloneGcpDataRepoSnapshotReference(
            cloneDataRepoSnapshotReferenceRequestBody,
            sourceDataRepoSnapshotReference.getMetadata().getWorkspaceId(),
            sourceDataRepoSnapshotReference.getMetadata().getResourceId());
    assertEquals(getWorkspaceId(), cloneDataRepoSnapshotResult.getSourceWorkspaceId());
    final ResourceMetadata clonedDataRepoSnapshotResourceMetadata =
        cloneDataRepoSnapshotResult.getResource().getMetadata();
    assertEquals(
        StewardshipType.REFERENCED, clonedDataRepoSnapshotResourceMetadata.getStewardshipType());
    assertEquals(
        ResourceType.DATA_REPO_SNAPSHOT, clonedDataRepoSnapshotResourceMetadata.getResourceType());
    assertEquals(
        sourceDataRepoSnapshotReference.getMetadata().getResourceId(),
        cloneDataRepoSnapshotResult.getSourceResourceId());
    assertEquals(
        sourceDataRepoSnapshotReference.getMetadata().getName(),
        clonedDataRepoSnapshotResourceMetadata.getName());
    assertEquals(
        sourceDataRepoSnapshotReference.getMetadata().getDescription(),
        clonedDataRepoSnapshotResourceMetadata.getDescription());
    assertEquals(
        sourceDataRepoSnapshotReference.getAttributes().getSnapshot(),
        cloneDataRepoSnapshotResult.getResource().getAttributes().getSnapshot());
    assertEquals(
        sourceDataRepoSnapshotReference.getAttributes().getInstanceName(),
        cloneDataRepoSnapshotResult.getResource().getAttributes().getInstanceName());
  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi) {
    if (destinationWorkspaceId != null) {
      try {
        workspaceApi.deleteWorkspace(destinationWorkspaceId);
      } catch (ApiException e) {
        logger.error("Failed to clean up destination workspace: {}", e.getMessage());
      }
    }
  }
}
