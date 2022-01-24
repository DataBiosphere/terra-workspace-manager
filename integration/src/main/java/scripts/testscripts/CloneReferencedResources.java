package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static scripts.utils.ClientTestUtils.TEST_BUCKET_NAME;
import static scripts.utils.ClientTestUtils.TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS;
import static scripts.utils.ClientTestUtils.TEST_FILE_FOO_MONKEY_SEES_MONKEY_DOS;
import static scripts.utils.ClientTestUtils.TEST_FOLDER_FOO;
import static scripts.utils.ClientTestUtils.TEST_GITHUB_REPO_PUBLIC_SSH;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloneReferencedGcpDataRepoSnapshotResourceResult;
import bio.terra.workspace.model.CloneReferencedGcpGcsBucketResourceResult;
import bio.terra.workspace.model.CloneReferencedGcpGcsObjectResourceResult;
import bio.terra.workspace.model.CloneReferencedGitRepoResourceResult;
import bio.terra.workspace.model.CloneReferencedResourceRequestBody;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.DataRepoSnapshotResource;
import bio.terra.workspace.model.GcpBigQueryDataTableResource;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GcpGcsObjectResource;
import bio.terra.workspace.model.GitRepoResource;
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
  private static final Logger logger = LoggerFactory.getLogger(CloneReferencedResources.class);

  private static final String DATASET_RESOURCE_NAME = "dataset_resource_name";
  private static final String DATA_TABLE_RESOURCE_NAME = "data_table_resource_name";
  private static final String CLONED_BUCKET_RESOURCE_NAME = "a_new_name";
  private static final String CLONED_BUCKET_FILE_RESOURCE_NAME = "a_new_name_for_the_bucket_file";
  private static final String CLONED_FOO_FOLDER_RESOURCE_NAME = "a_new_name_for_the_foo_folder";
  private static final String CLONED_DATASET_RESOURCE_NAME = "a_cloned_reference";
  private static final String CLONED_DATASET_DESCRIPTION = "Second star to the right.";
  private static final String CLONED_DATA_TABLE_REFERENCE = "a_cloned_data_table_reference";
  private static final String CLONED_DATA_TABLE_DESCRIPTION = "a cloned data table reference";
  private static final String CLONED_GITHUB_REPO_RESOURCE_NAME = "a_new_name_for_the_github_repo";
  private static final String CLONED_GITHUB_REPO_DESCRIPTION =
      "a cloned reference to the wsm github repo";

  private DataRepoSnapshotResource sourceDataRepoSnapshotReference;
  private GcpGcsBucketResource sourceBucketReference;
  // Reference to gs://terra_wsm_fine_grained_test_bucket/foo/monkey_sees_monkey_dos.txt
  private GcpGcsObjectResource sourceGcsObjectReference;
  // Reference to gs://terra_wsm_fine_grained_test_bucket/foo/
  private GcpGcsObjectResource sourceBucketFolderReference;
  private GcpBigQueryDatasetResource sourceBigQueryDatasetReference;
  private GcpBigQueryDataTableResource sourceBigQueryDataTableReference;
  private GitRepoResource sourceGitRepoReference;
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

    sourceGcsObjectReference =
        ResourceMaker.makeGcsObjectReference(
            referencedGcpResourceApi,
            getWorkspaceId(),
            "reference_to_foo_monkey_sees_monkey_dos",
            null,
            TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS,
            TEST_FILE_FOO_MONKEY_SEES_MONKEY_DOS);
    sourceBucketFolderReference =
        ResourceMaker.makeGcsObjectReference(
            referencedGcpResourceApi,
            getWorkspaceId(),
            "reference_to_foo_folder",
            null,
            TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS,
            TEST_FOLDER_FOO);

    sourceBigQueryDatasetReference =
        ResourceMaker.makeBigQueryDatasetReference(
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

    sourceGitRepoReference =
        ResourceMaker.makeGitRepoReference(
            referencedGcpResourceApi, getWorkspaceId(), "git_repo_reference_resource");

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

    // clone source reference to destination
    final var cloneBucketFileReferenceRequestBody =
        new CloneReferencedResourceRequestBody()
            .cloningInstructions(CloningInstructionsEnum.REFERENCE)
            .name(CLONED_BUCKET_FILE_RESOURCE_NAME)
            .destinationWorkspaceId(destinationWorkspaceId);
    logger.info(
        "Cloning GCS Bucket object Reference\n\tworkspaceId: {}\n\tresourceId: {}\ninto\n\tworkspaceId: {}",
        sourceGcsObjectReference.getMetadata().getWorkspaceId(),
        sourceGcsObjectReference.getMetadata().getResourceId(),
        destinationWorkspaceId);
    final CloneReferencedGcpGcsObjectResourceResult cloneBucketFileReferenceResult =
        referencedGcpResourceApi.cloneGcpGcsObjectReference(
            cloneBucketFileReferenceRequestBody,
            getWorkspaceId(),
            sourceGcsObjectReference.getMetadata().getResourceId());
    assertEquals(getWorkspaceId(), cloneBucketFileReferenceResult.getSourceWorkspaceId());
    assertEquals(
        StewardshipType.REFERENCED,
        cloneBucketFileReferenceResult.getResource().getMetadata().getStewardshipType());
    assertEquals(
        ResourceType.GCS_OBJECT,
        cloneBucketFileReferenceResult.getResource().getMetadata().getResourceType());
    assertEquals(
        sourceGcsObjectReference.getMetadata().getResourceId(),
        cloneBucketFileReferenceResult.getSourceResourceId());
    assertEquals(
        sourceGcsObjectReference.getMetadata().getDescription(),
        cloneBucketFileReferenceResult.getResource().getMetadata().getDescription());
    assertEquals(
        CLONED_BUCKET_FILE_RESOURCE_NAME,
        cloneBucketFileReferenceResult.getResource().getMetadata().getName());
    assertEquals(
        TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS,
        cloneBucketFileReferenceResult.getResource().getAttributes().getBucketName());
    assertEquals(
        TEST_FILE_FOO_MONKEY_SEES_MONKEY_DOS,
        cloneBucketFileReferenceResult.getResource().getAttributes().getFileName());

    // clone source reference to destination
    CloneReferencedResourceRequestBody cloneFooFolderReferenceRequestBody =
        new CloneReferencedResourceRequestBody()
            .cloningInstructions(CloningInstructionsEnum.REFERENCE)
            .name(CLONED_FOO_FOLDER_RESOURCE_NAME)
            .destinationWorkspaceId(destinationWorkspaceId);
    logger.info(
        "Cloning GCS Bucket folder Reference\n\tworkspaceId: {}\n\tresourceId: {}\ninto\n\tworkspaceId: {}",
        sourceBucketFolderReference.getMetadata().getWorkspaceId(),
        sourceBucketFolderReference.getMetadata().getResourceId(),
        destinationWorkspaceId);
    CloneReferencedGcpGcsObjectResourceResult cloneFooFolderReferenceResult =
        referencedGcpResourceApi.cloneGcpGcsObjectReference(
            cloneFooFolderReferenceRequestBody,
            getWorkspaceId(),
            sourceBucketFolderReference.getMetadata().getResourceId());
    assertEquals(getWorkspaceId(), cloneFooFolderReferenceResult.getSourceWorkspaceId());
    assertEquals(
        StewardshipType.REFERENCED,
        cloneFooFolderReferenceResult.getResource().getMetadata().getStewardshipType());
    assertEquals(
        ResourceType.GCS_OBJECT,
        cloneFooFolderReferenceResult.getResource().getMetadata().getResourceType());
    assertEquals(
        sourceBucketFolderReference.getMetadata().getResourceId(),
        cloneFooFolderReferenceResult.getSourceResourceId());
    assertEquals(
        sourceBucketFolderReference.getMetadata().getDescription(),
        cloneFooFolderReferenceResult.getResource().getMetadata().getDescription());
    assertEquals(
        CLONED_FOO_FOLDER_RESOURCE_NAME,
        cloneFooFolderReferenceResult.getResource().getMetadata().getName());
    assertEquals(
        TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS,
        cloneFooFolderReferenceResult.getResource().getAttributes().getBucketName());
    assertEquals(
        TEST_FOLDER_FOO, cloneFooFolderReferenceResult.getResource().getAttributes().getFileName());

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
        ResourceType.BIG_QUERY_DATA_TABLE, clonedDataTableResourceMetadata.getResourceType());
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

    // clone source reference Git repo to destination
    CloneReferencedResourceRequestBody cloneGitReferenceRequestBody =
        new CloneReferencedResourceRequestBody()
            .cloningInstructions(CloningInstructionsEnum.REFERENCE)
            .name(CLONED_GITHUB_REPO_RESOURCE_NAME)
            .description(CLONED_GITHUB_REPO_DESCRIPTION)
            .destinationWorkspaceId(destinationWorkspaceId);
    logger.info(
        "Cloning Git repo Reference\n\tworkspaceId: {}\n\tresourceId: {}\ninto\n\tworkspaceId: {}",
        sourceGitRepoReference.getMetadata().getWorkspaceId(),
        sourceGitRepoReference.getMetadata().getResourceId(),
        destinationWorkspaceId);
    final CloneReferencedGitRepoResourceResult gitHubRepoReferenceCloneResult =
        referencedGcpResourceApi.cloneGitRepoReference(
            cloneGitReferenceRequestBody,
            getWorkspaceId(),
            sourceGitRepoReference.getMetadata().getResourceId());
    assertEquals(getWorkspaceId(), gitHubRepoReferenceCloneResult.getSourceWorkspaceId());
    assertEquals(
        StewardshipType.REFERENCED,
        gitHubRepoReferenceCloneResult.getResource().getMetadata().getStewardshipType());
    assertEquals(
        ResourceType.GIT_REPO,
        gitHubRepoReferenceCloneResult.getResource().getMetadata().getResourceType());
    assertEquals(
        sourceGitRepoReference.getMetadata().getResourceId(),
        gitHubRepoReferenceCloneResult.getSourceResourceId());
    assertEquals(
        CLONED_GITHUB_REPO_RESOURCE_NAME,
        gitHubRepoReferenceCloneResult.getResource().getMetadata().getName());
    assertEquals(
        TEST_GITHUB_REPO_PUBLIC_SSH,
        gitHubRepoReferenceCloneResult.getResource().getAttributes().getGitCloneUrl());
    assertEquals(
        CLONED_GITHUB_REPO_DESCRIPTION,
        gitHubRepoReferenceCloneResult.getResource().getMetadata().getDescription());
  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
    if (destinationWorkspaceId != null) {
      try {
        workspaceApi.deleteWorkspace(destinationWorkspaceId);
      } catch (ApiException e) {
        logger.error("Failed to clean up destination workspace: {}", e.getMessage());
      }
    }
  }
}
