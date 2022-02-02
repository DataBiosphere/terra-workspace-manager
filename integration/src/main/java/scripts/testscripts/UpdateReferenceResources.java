package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.DataRepoSnapshotResource;
import bio.terra.workspace.model.GcpBigQueryDataTableAttributes;
import bio.terra.workspace.model.GcpBigQueryDataTableResource;
import bio.terra.workspace.model.GcpBigQueryDatasetAttributes;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpGcsBucketAttributes;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GcpGcsObjectAttributes;
import bio.terra.workspace.model.GcpGcsObjectResource;
import bio.terra.workspace.model.GitRepoAttributes;
import bio.terra.workspace.model.GitRepoResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import scripts.utils.ClientTestUtils;
import scripts.utils.DataRepoTestScriptBase;
import scripts.utils.ParameterKeys;
import scripts.utils.ParameterUtils;
import scripts.utils.ResourceMaker;

public class UpdateReferenceResources extends DataRepoTestScriptBase {

  private TestUserSpecification userWithFullAccess;
  private TestUserSpecification userWithPartialAccess;

  private ReferencedGcpResourceApi fullAccessApi;
  private ReferencedGcpResourceApi partialAccessApi;

  private GcpGcsBucketAttributes gcsUniformAccessBucketAttributes;
  private GcpGcsObjectAttributes gcsFileAttributes;
  private GcpGcsObjectAttributes gcsFolderAttributes;
  private GcpBigQueryDataTableAttributes bqTableAttributes;
  private GcpBigQueryDataTableAttributes bqTableFromAlternateDatasetAttributes;
  private GitRepoAttributes gitRepoAttributes;

  private String dataRepoSnapshotId2;

  @MonotonicNonNull private UUID bqDatasetResourceId;
  @MonotonicNonNull private UUID bqTableResourceId;
  @MonotonicNonNull private UUID dataRepoSnapshotResourceId;
  @MonotonicNonNull private UUID bucketResourceId;
  @MonotonicNonNull private UUID bucketObjectResourceId;
  @MonotonicNonNull private UUID gitReferencedResourceId;

  @Override
  public void setParameters(Map<String, String> parameters) throws Exception {
    super.setParameters(parameters);
    gcsUniformAccessBucketAttributes = ParameterUtils.getUniformBucketReference(parameters);
    gcsFileAttributes = ParameterUtils.getGcsFileReference(parameters);
    gcsFolderAttributes = ParameterUtils.getGcsFolderReference(parameters);
    bqTableAttributes = ParameterUtils.getBigQueryDataTableReference(parameters);
    bqTableFromAlternateDatasetAttributes =
        ParameterUtils.getBigQueryDataTableFromAlternateDatasetReference(parameters);
    dataRepoSnapshotId2 =
        ParameterUtils.getParamOrThrow(
            parameters, ParameterKeys.DATA_REPO_ALTERNATE_SNAPSHOT_PARAMETER);
    gitRepoAttributes = ParameterUtils.getSshGitRepoReference(parameters);
  }

  @Override
  protected void doSetup(
      List<TestUserSpecification> testUsers, WorkspaceApi sourceOwnerWorkspaceApi)
      throws Exception {
    super.doSetup(testUsers, sourceOwnerWorkspaceApi);
    userWithFullAccess = testUsers.get(0);
    userWithPartialAccess = testUsers.get(1);

    fullAccessApi =
        new ReferencedGcpResourceApi(
            ClientTestUtils.getClientForTestUser(userWithFullAccess, server));
    partialAccessApi =
        new ReferencedGcpResourceApi(
            ClientTestUtils.getClientForTestUser(userWithPartialAccess, server));

    // Use the same dataset that the BQ table parameter references
    GcpBigQueryDatasetAttributes datasetAttributes =
        new GcpBigQueryDatasetAttributes()
            .projectId(bqTableAttributes.getProjectId())
            .datasetId(bqTableAttributes.getDatasetId());
    GcpBigQueryDatasetResource bqDatasetReference =
        ResourceMaker.makeBigQueryDatasetReference(
            datasetAttributes, fullAccessApi, getWorkspaceId(), "bqDatasetReference");
    bqDatasetResourceId = bqDatasetReference.getMetadata().getResourceId();
    GcpBigQueryDataTableResource bqDataTableReference =
        ResourceMaker.makeBigQueryDataTableReference(
            bqTableAttributes, fullAccessApi, getWorkspaceId(), "bqTableReference");
    bqTableResourceId = bqDataTableReference.getMetadata().getResourceId();
    DataRepoSnapshotResource snapshotResource =
        ResourceMaker.makeDataRepoSnapshotReference(
            fullAccessApi,
            getWorkspaceId(),
            "dataRepoReference",
            getDataRepoSnapshotId(),
            getDataRepoInstanceName());
    dataRepoSnapshotResourceId = snapshotResource.getMetadata().getResourceId();
    GcpGcsBucketResource bucketResource =
        ResourceMaker.makeGcsBucketReference(
            gcsUniformAccessBucketAttributes,
            fullAccessApi,
            getWorkspaceId(),
            "bucketReference",
            CloningInstructionsEnum.NOTHING);
    bucketResourceId = bucketResource.getMetadata().getResourceId();
    GcpGcsObjectResource blobResource =
        ResourceMaker.makeGcsObjectReference(
            gcsFileAttributes,
            fullAccessApi,
            getWorkspaceId(),
            "a_reference_to_foo_monkey_sees_monkey_dos",
            CloningInstructionsEnum.REFERENCE);
    bucketObjectResourceId = blobResource.getMetadata().getResourceId();

    GitRepoResource gitRepoReference =
        ResourceMaker.makeGitRepoReference(
            gitRepoAttributes, fullAccessApi, getWorkspaceId(), "a_reference_to_wsm_git_repo");
    gitReferencedResourceId = gitRepoReference.getMetadata().getResourceId();
  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    // Add {@code userWithPartialAccess} as workspace reader, though this will not affect
    // permissions on referenced external objects.
    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(userWithPartialAccess.userEmail),
        getWorkspaceId(),
        IamRole.READER);
    ResourceApi partialAccessResourceApi =
        new ResourceApi(ClientTestUtils.getClientForTestUser(userWithPartialAccess, server));

    String newGitRepoReferenceName = "newGitRepoReferenceName";
    String newGitRepoReferenceDescription = "a new description for git repo reference";
    ResourceMaker.updateGitRepoReferenceResource(
        fullAccessApi,
        getWorkspaceId(),
        gitReferencedResourceId,
        newGitRepoReferenceName,
        newGitRepoReferenceDescription,
        /*gitCloneUrl=*/ null);
    // Update snapshot's name and description
    String newSnapshotReferenceName = "newSnapshotReferenceName";
    String newSnapshotReferenceDescription = "a new description of another snapshot reference";
    ResourceMaker.updateDataRepoSnapshotReferenceResource(
        fullAccessApi,
        getWorkspaceId(),
        dataRepoSnapshotResourceId,
        newSnapshotReferenceName,
        newSnapshotReferenceDescription,
        /*instanceId=*/ null,
        /*snapshot=*/ null);
    DataRepoSnapshotResource snapshotResource =
        fullAccessApi.getDataRepoSnapshotReference(getWorkspaceId(), dataRepoSnapshotResourceId);
    assertEquals(newSnapshotReferenceName, snapshotResource.getMetadata().getName());
    assertEquals(newSnapshotReferenceDescription, snapshotResource.getMetadata().getDescription());
    assertFalse(
        partialAccessResourceApi.checkReferenceAccess(
            getWorkspaceId(), dataRepoSnapshotResourceId));

    assertThrows(
        ApiException.class,
        () ->
            ResourceMaker.updateDataRepoSnapshotReferenceResource(
                partialAccessApi,
                getWorkspaceId(),
                dataRepoSnapshotResourceId,
                newSnapshotReferenceName,
                newSnapshotReferenceDescription,
                /*instanceId=*/ null,
                dataRepoSnapshotId2));
    ResourceMaker.updateDataRepoSnapshotReferenceResource(
        fullAccessApi,
        getWorkspaceId(),
        dataRepoSnapshotResourceId,
        newSnapshotReferenceName,
        newSnapshotReferenceDescription,
        /*instanceId=*/ null,
        dataRepoSnapshotId2);
    DataRepoSnapshotResource snapshotResourceSecondUpdate =
        fullAccessApi.getDataRepoSnapshotReference(getWorkspaceId(), dataRepoSnapshotResourceId);
    assertEquals(newSnapshotReferenceName, snapshotResourceSecondUpdate.getMetadata().getName());
    assertEquals(
        newSnapshotReferenceDescription,
        snapshotResourceSecondUpdate.getMetadata().getDescription());
    assertEquals(dataRepoSnapshotId2, snapshotResourceSecondUpdate.getAttributes().getSnapshot());
    assertEquals(
        getDataRepoInstanceName(), snapshotResourceSecondUpdate.getAttributes().getInstanceName());
    assertTrue(
        partialAccessResourceApi.checkReferenceAccess(
            getWorkspaceId(), dataRepoSnapshotResourceId));

    // Update BQ dataset's name and description
    String newDatasetName = "newDatasetName";
    String newDatasetDescription = "newDescription";
    ResourceMaker.updateBigQueryDatasetReference(
        fullAccessApi,
        getWorkspaceId(),
        bqDatasetResourceId,
        newDatasetName,
        newDatasetDescription,
        /*projectId=*/ null,
        /*datasetId=*/ null);
    GcpBigQueryDatasetResource datasetReferenceFirstUpdate =
        fullAccessApi.getBigQueryDatasetReference(getWorkspaceId(), bqDatasetResourceId);
    assertEquals(newDatasetName, datasetReferenceFirstUpdate.getMetadata().getName());
    assertEquals(newDatasetDescription, datasetReferenceFirstUpdate.getMetadata().getDescription());
    assertEquals(
        bqTableAttributes.getDatasetId(),
        datasetReferenceFirstUpdate.getAttributes().getDatasetId());
    assertEquals(
        bqTableAttributes.getProjectId(),
        datasetReferenceFirstUpdate.getAttributes().getProjectId());
    // {@code userWithPartialAccess} does not have access to the original dataset.
    assertFalse(
        partialAccessResourceApi.checkReferenceAccess(getWorkspaceId(), bqDatasetResourceId));

    // Update BQ dataset's referencing target

    // Attempt to update the referencing target but {@code userWithPartialAccess} does not have
    // access to the original dataset.
    assertThrows(
        ApiException.class,
        () ->
            ResourceMaker.updateBigQueryDatasetReference(
                partialAccessApi,
                getWorkspaceId(),
                bqDatasetResourceId,
                /*name=*/ null,
                /*description=*/ null,
                /*projectId=*/ null,
                bqTableFromAlternateDatasetAttributes.getDatasetId()));
    ResourceMaker.updateBigQueryDatasetReference(
        fullAccessApi,
        getWorkspaceId(),
        bqDatasetResourceId,
        /*name=*/ null,
        /*description=*/ null,
        /*projectId=*/ null,
        bqTableFromAlternateDatasetAttributes.getDatasetId());
    GcpBigQueryDatasetResource datasetReferenceSecondUpdate =
        fullAccessApi.getBigQueryDatasetReference(getWorkspaceId(), bqDatasetResourceId);
    assertEquals(newDatasetName, datasetReferenceSecondUpdate.getMetadata().getName());
    assertEquals(
        newDatasetDescription, datasetReferenceSecondUpdate.getMetadata().getDescription());
    assertEquals(
        bqTableAttributes.getProjectId(),
        datasetReferenceSecondUpdate.getAttributes().getProjectId());
    assertEquals(
        bqTableFromAlternateDatasetAttributes.getDatasetId(),
        datasetReferenceSecondUpdate.getAttributes().getDatasetId());
    // {@code userWithPartialAccess} have access to dataset 2. Now since the reference is pointing
    // to dataset 2, the user have access to this reference now.
    assertTrue(
        partialAccessResourceApi.checkReferenceAccess(getWorkspaceId(), bqDatasetResourceId));

    // Update BQ data table's name and description.
    String newDataTableName = "newDataTableName";
    String newDataTableDescription = "a new description to the new data table reference";
    ResourceMaker.updateBigQueryDataTableReference(
        fullAccessApi,
        getWorkspaceId(),
        bqTableResourceId,
        newDataTableName,
        newDataTableDescription,
        /*projectId=*/ null,
        /*datasetId=*/ null,
        /*tableId=*/ null);
    GcpBigQueryDataTableResource dataTableReferenceFirstUpdate =
        fullAccessApi.getBigQueryDataTableReference(getWorkspaceId(), bqTableResourceId);
    assertEquals(newDataTableName, dataTableReferenceFirstUpdate.getMetadata().getName());
    assertEquals(
        newDataTableDescription, dataTableReferenceFirstUpdate.getMetadata().getDescription());
    assertEquals(
        bqTableAttributes.getProjectId(),
        dataTableReferenceFirstUpdate.getAttributes().getProjectId());
    assertEquals(
        bqTableAttributes.getDatasetId(),
        dataTableReferenceFirstUpdate.getAttributes().getDatasetId());
    assertEquals(
        bqTableAttributes.getDataTableId(),
        dataTableReferenceFirstUpdate.getAttributes().getDataTableId());

    // Update bq data table target

    // Attempt to update bq data table reference but {@code userWithPartialAccess} does not have
    // access to the bq table 2.
    assertThrows(
        ApiException.class,
        () ->
            ResourceMaker.updateBigQueryDataTableReference(
                partialAccessApi,
                getWorkspaceId(),
                bqTableResourceId,
                /*name=*/ null,
                /*description=*/ null,
                /*projectId=*/ null,
                bqTableFromAlternateDatasetAttributes.getDatasetId(),
                bqTableFromAlternateDatasetAttributes.getDataTableId()));
    // Successfully update the referencing target because the {@code userWithFullAccess} has
    // access to the bq table 2.
    ResourceMaker.updateBigQueryDataTableReference(
        fullAccessApi,
        getWorkspaceId(),
        bqTableResourceId,
        /*name=*/ null,
        /*description=*/ null,
        /*projectId=*/ null,
        bqTableFromAlternateDatasetAttributes.getDatasetId(),
        bqTableFromAlternateDatasetAttributes.getDataTableId());

    GcpBigQueryDataTableResource dataTableReferenceSecondUpdate =
        fullAccessApi.getBigQueryDataTableReference(getWorkspaceId(), bqTableResourceId);
    assertEquals(newDataTableName, dataTableReferenceSecondUpdate.getMetadata().getName());
    assertEquals(
        newDataTableDescription, dataTableReferenceSecondUpdate.getMetadata().getDescription());
    assertEquals(
        bqTableAttributes.getProjectId(),
        dataTableReferenceSecondUpdate.getAttributes().getProjectId());
    assertEquals(
        bqTableFromAlternateDatasetAttributes.getDatasetId(),
        dataTableReferenceSecondUpdate.getAttributes().getDatasetId());
    assertEquals(
        bqTableFromAlternateDatasetAttributes.getDataTableId(),
        dataTableReferenceSecondUpdate.getAttributes().getDataTableId());

    ResourceMaker.updateBigQueryDataTableReference(
        fullAccessApi,
        getWorkspaceId(),
        bqTableResourceId,
        /*name=*/ null,
        /*description=*/ null,
        /*projectId=*/ null,
        bqTableAttributes.getDatasetId(),
        /*tableId=*/ null);

    GcpBigQueryDataTableResource dataTableReferenceThirdUpdate =
        fullAccessApi.getBigQueryDataTableReference(getWorkspaceId(), bqTableResourceId);
    assertEquals(newDataTableName, dataTableReferenceThirdUpdate.getMetadata().getName());
    assertEquals(
        newDataTableDescription, dataTableReferenceThirdUpdate.getMetadata().getDescription());
    assertEquals(
        bqTableAttributes.getProjectId(),
        dataTableReferenceThirdUpdate.getAttributes().getProjectId());
    assertEquals(
        bqTableAttributes.getDatasetId(),
        dataTableReferenceThirdUpdate.getAttributes().getDatasetId());
    assertEquals(
        bqTableFromAlternateDatasetAttributes.getDataTableId(),
        dataTableReferenceThirdUpdate.getAttributes().getDataTableId());

    ResourceMaker.updateBigQueryDataTableReference(
        fullAccessApi,
        getWorkspaceId(),
        bqTableResourceId,
        /*name=*/ null,
        /*description=*/ null,
        /*projectId=*/ null,
        /*datasetId=*/ null,
        bqTableAttributes.getDataTableId());
    GcpBigQueryDataTableResource dataTableReferenceFourthUpdate =
        fullAccessApi.getBigQueryDataTableReference(getWorkspaceId(), bqTableResourceId);
    assertEquals(newDataTableName, dataTableReferenceFourthUpdate.getMetadata().getName());
    assertEquals(
        newDataTableDescription, dataTableReferenceFourthUpdate.getMetadata().getDescription());
    assertEquals(
        bqTableAttributes.getProjectId(),
        dataTableReferenceFourthUpdate.getAttributes().getProjectId());
    assertEquals(
        bqTableAttributes.getDatasetId(),
        dataTableReferenceFourthUpdate.getAttributes().getDatasetId());
    assertEquals(
        bqTableAttributes.getDataTableId(),
        dataTableReferenceFourthUpdate.getAttributes().getDataTableId());

    // Update GCS bucket's name and description
    String newBucketName = "newGcsBucket";
    String newBucketDescription = "a new description to the new bucket reference";
    ResourceMaker.updateGcsBucketReference(
        fullAccessApi,
        getWorkspaceId(),
        bucketResourceId,
        newBucketName,
        newBucketDescription,
        null);
    GcpGcsBucketResource bucketReferenceFirstUpdate =
        fullAccessApi.getBucketReference(getWorkspaceId(), bucketResourceId);
    assertEquals(newBucketName, bucketReferenceFirstUpdate.getMetadata().getName());
    assertEquals(newBucketDescription, bucketReferenceFirstUpdate.getMetadata().getDescription());
    assertEquals(
        gcsUniformAccessBucketAttributes.getBucketName(),
        bucketReferenceFirstUpdate.getAttributes().getBucketName());
    assertTrue(partialAccessResourceApi.checkReferenceAccess(getWorkspaceId(), bucketResourceId));

    // Update GCS bucket referencing target from the uniform access bucket to the fine-grained
    // access bucket.
    // Use the bucket holding the reference file as our fine-grained alternate bucket.
    String fineGrainedGcsBucketName = gcsFileAttributes.getBucketName();

    // Attempt to update bucket reference but {@code userWithPartialAccess} does not have
    // access to the bucket with fine-grained access
    assertThrows(
        ApiException.class,
        () ->
            ResourceMaker.updateGcsBucketReference(
                partialAccessApi,
                getWorkspaceId(),
                bucketResourceId,
                /*name=*/ null,
                /*description=*/ null,
                fineGrainedGcsBucketName));
    // Successfully update the referencing target because the {@code userWithFullAccess} has
    // access to the bucket with fine-grained access.
    ResourceMaker.updateGcsBucketReference(
        fullAccessApi,
        getWorkspaceId(),
        bucketResourceId,
        /*name=*/ null,
        /*description=*/ null,
        fineGrainedGcsBucketName);
    GcpGcsBucketResource bucketReferenceSecondUpdate =
        fullAccessApi.getBucketReference(getWorkspaceId(), bucketResourceId);
    assertEquals(newBucketName, bucketReferenceSecondUpdate.getMetadata().getName());
    assertEquals(newBucketDescription, bucketReferenceSecondUpdate.getMetadata().getDescription());
    assertEquals(
        fineGrainedGcsBucketName, bucketReferenceSecondUpdate.getAttributes().getBucketName());

    // Update GCS bucket object's name and description
    String newBlobName = "newBlobName";
    String newBlobDescription = "a new description to the new bucket blob reference";
    ResourceMaker.updateGcsBucketObjectReference(
        fullAccessApi,
        getWorkspaceId(),
        bucketObjectResourceId,
        newBlobName,
        newBlobDescription,
        /*bucketName=*/ null,
        /*objectName=*/ null);
    GcpGcsObjectResource blobResource =
        fullAccessApi.getGcsObjectReference(getWorkspaceId(), bucketObjectResourceId);
    assertEquals(newBlobName, blobResource.getMetadata().getName());
    assertEquals(newBlobDescription, blobResource.getMetadata().getDescription());
    assertEquals(gcsFileAttributes.getBucketName(), blobResource.getAttributes().getBucketName());
    assertEquals(gcsFileAttributes.getFileName(), blobResource.getAttributes().getFileName());

    // Update GCS bucket object's referencing target from foo/monkey_sees_monkey_dos.txt to foo/.

    assertTrue(
        partialAccessResourceApi.checkReferenceAccess(getWorkspaceId(), bucketObjectResourceId));

    // Update object path only.
    // Attempt to update to foo but {@code userWithPartialAccess} does not have access to foo/
    assertThrows(
        ApiException.class,
        () ->
            ResourceMaker.updateGcsBucketObjectReference(
                partialAccessApi,
                getWorkspaceId(),
                bucketObjectResourceId,
                /*name=*/ null,
                /*description=*/ null,
                gcsFileAttributes.getBucketName(),
                gcsFolderAttributes.getFileName()));
    // User with access to foo/ can successfully update the referencing target to foo/.
    ResourceMaker.updateGcsBucketObjectReference(
        fullAccessApi,
        getWorkspaceId(),
        bucketObjectResourceId,
        /*name=*/ null,
        /*description=*/ null,
        /*bucketName=*/ null,
        gcsFolderAttributes.getFileName());
    GcpGcsObjectResource blobReferenceSecondUpdate =
        fullAccessApi.getGcsObjectReference(getWorkspaceId(), bucketObjectResourceId);
    assertEquals(
        gcsFileAttributes.getBucketName(),
        blobReferenceSecondUpdate.getAttributes().getBucketName());
    assertEquals(
        gcsFolderAttributes.getFileName(), blobReferenceSecondUpdate.getAttributes().getFileName());
    assertEquals(newBlobName, blobReferenceSecondUpdate.getMetadata().getName());
    assertEquals(newBlobDescription, blobReferenceSecondUpdate.getMetadata().getDescription());

    // update bucket only.
    ResourceMaker.updateGcsBucketObjectReference(
        fullAccessApi,
        getWorkspaceId(),
        bucketObjectResourceId,
        /*name=*/ null,
        /*description=*/ null,
        /*bucketName=*/ gcsUniformAccessBucketAttributes.getBucketName(),
        null);
    GcpGcsObjectResource blobReferenceThirdUpdate =
        fullAccessApi.getGcsObjectReference(getWorkspaceId(), bucketObjectResourceId);
    assertEquals(
        gcsUniformAccessBucketAttributes.getBucketName(),
        blobReferenceThirdUpdate.getAttributes().getBucketName());
    assertEquals(
        gcsFolderAttributes.getFileName(), blobReferenceThirdUpdate.getAttributes().getFileName());
    assertEquals(newBlobName, blobReferenceThirdUpdate.getMetadata().getName());
    assertEquals(newBlobDescription, blobReferenceThirdUpdate.getMetadata().getDescription());

    // Update both bucket and object path.
    ResourceMaker.updateGcsBucketObjectReference(
        fullAccessApi,
        getWorkspaceId(),
        bucketObjectResourceId,
        /*name=*/ null,
        /*description=*/ null,
        /*bucketName=*/ gcsFileAttributes.getBucketName(),
        gcsFileAttributes.getFileName());
    GcpGcsObjectResource blobReferenceFourthUpdate =
        fullAccessApi.getGcsObjectReference(getWorkspaceId(), bucketObjectResourceId);
    assertEquals(
        gcsFileAttributes.getBucketName(),
        blobReferenceFourthUpdate.getAttributes().getBucketName());
    assertEquals(
        gcsFileAttributes.getFileName(), blobReferenceFourthUpdate.getAttributes().getFileName());
    assertEquals(newBlobName, blobReferenceFourthUpdate.getMetadata().getName());
    assertEquals(newBlobDescription, blobReferenceFourthUpdate.getMetadata().getDescription());
  }
}
