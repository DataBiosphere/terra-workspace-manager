package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static scripts.utils.ClientTestUtils.TEST_BQ_DATASET_NAME;
import static scripts.utils.ClientTestUtils.TEST_BQ_DATASET_NAME_2;
import static scripts.utils.ClientTestUtils.TEST_BQ_DATASET_PROJECT;
import static scripts.utils.ClientTestUtils.TEST_BQ_DATATABLE_NAME;
import static scripts.utils.ClientTestUtils.TEST_BQ_DATATABLE_NAME_2;
import static scripts.utils.ClientTestUtils.TEST_BUCKET_NAME;
import static scripts.utils.ClientTestUtils.TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS;
import static scripts.utils.ClientTestUtils.TEST_FILE_IN_FINE_GRAINED_BUCKET;
import static scripts.utils.ClientTestUtils.TEST_FOLDER_IN_FINE_GRAINED_BUCKET;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.DataRepoSnapshotAttributes;
import bio.terra.workspace.model.DataRepoSnapshotResource;
import bio.terra.workspace.model.GcpBigQueryDataTableAttributes;
import bio.terra.workspace.model.GcpBigQueryDataTableResource;
import bio.terra.workspace.model.GcpBigQueryDatasetAttributes;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpGcsBucketAttributes;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GcpGcsObjectAttributes;
import bio.terra.workspace.model.GcpGcsObjectResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import java.util.List;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import scripts.utils.ClientTestUtils;
import scripts.utils.DataRepoTestScriptBase;
import scripts.utils.ResourceMaker;

public class UpdateReferenceResources extends DataRepoTestScriptBase {

  private TestUserSpecification userWithFullAccess;
  private TestUserSpecification userWithPartialAccess;

  private ReferencedGcpResourceApi fullAccessApi;
  private ReferencedGcpResourceApi partialAccessApi;

  @MonotonicNonNull private UUID bqDatasetResourceId;
  @MonotonicNonNull private UUID bqTableResourceId;
  @MonotonicNonNull private UUID dataRepoSnapshotResourceId;
  @MonotonicNonNull private UUID bucketResourceId;
  @MonotonicNonNull private UUID bucketObjectResourceId;

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

    GcpBigQueryDatasetResource bqDatasetReference =
        ResourceMaker.makeBigQueryDatasetReference(
            fullAccessApi, getWorkspaceId(), "bqDatasetReference");
    bqDatasetResourceId = bqDatasetReference.getMetadata().getResourceId();
    GcpBigQueryDataTableResource bqDataTableReference =
        ResourceMaker.makeBigQueryDataTableReference(
            fullAccessApi, getWorkspaceId(), "bqTableReference");
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
        ResourceMaker.makeGcsBucketReference(fullAccessApi, getWorkspaceId(), "bucketReference");
    bucketResourceId = bucketResource.getMetadata().getResourceId();
    GcpGcsObjectResource blobResource =
        ResourceMaker.makeGcsObjectReference(
            fullAccessApi,
            getWorkspaceId(),
            "a_reference_to_foo_monkey_sees_monkey_dos",
            CloningInstructionsEnum.REFERENCE,
            TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS,
            TEST_FILE_IN_FINE_GRAINED_BUCKET);
    bucketObjectResourceId = blobResource.getMetadata().getResourceId();
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

    // Update snapshot's name and description
    String newSnapshotReferenceName = "newSnapshotReferenceName";
    String newSnapshotReferenceDescription = "a new description of another snapshot reference";
    ResourceMaker.updateDataRepoSnapshotReferenceResource(
        fullAccessApi,
        getWorkspaceId(),
        dataRepoSnapshotResourceId,
        newSnapshotReferenceName,
        newSnapshotReferenceDescription,
        null);
    DataRepoSnapshotResource snapshotResource =
        fullAccessApi.getDataRepoSnapshotReference(getWorkspaceId(), dataRepoSnapshotResourceId);
    assertEquals(newSnapshotReferenceName, snapshotResource.getMetadata().getName());
    assertEquals(newSnapshotReferenceDescription, snapshotResource.getMetadata().getDescription());

    // Update BQ dataset's name and description
    String newDatasetName = "newDatasetName";
    String newDatasetDescription = "newDescription";
    ResourceMaker.updateBigQueryDatasetReference(
        fullAccessApi,
        getWorkspaceId(),
        bqDatasetResourceId,
        newDatasetName,
        newDatasetDescription,
        null);
    GcpBigQueryDatasetResource datasetReferenceFirstUpdate =
        fullAccessApi.getBigQueryDatasetReference(getWorkspaceId(), bqDatasetResourceId);
    assertEquals(newDatasetName, datasetReferenceFirstUpdate.getMetadata().getName());
    assertEquals(newDatasetDescription, datasetReferenceFirstUpdate.getMetadata().getDescription());
    assertEquals(TEST_BQ_DATASET_NAME, datasetReferenceFirstUpdate.getAttributes().getDatasetId());
    assertEquals(
        TEST_BQ_DATASET_PROJECT, datasetReferenceFirstUpdate.getAttributes().getProjectId());
    // {@code userWithPartialAccess} does not have access to the original dataset.
    assertFalse(
        partialAccessResourceApi.checkReferenceAccess(getWorkspaceId(), bqDatasetResourceId));

    // Update BQ dataset's referencing target
    GcpBigQueryDatasetAttributes datasetAttributes = new GcpBigQueryDatasetAttributes();
    datasetAttributes.setProjectId(TEST_BQ_DATASET_PROJECT);
    datasetAttributes.setDatasetId(TEST_BQ_DATASET_NAME_2);
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
                datasetAttributes));
    ResourceMaker.updateBigQueryDatasetReference(
        fullAccessApi,
        getWorkspaceId(),
        bqDatasetResourceId,
        /*name=*/ null,
        /*description=*/ null,
        datasetAttributes);
    GcpBigQueryDatasetResource datasetReferenceSecondUpdate =
        fullAccessApi.getBigQueryDatasetReference(getWorkspaceId(), bqDatasetResourceId);
    assertEquals(newDatasetName, datasetReferenceSecondUpdate.getMetadata().getName());
    assertEquals(
        newDatasetDescription, datasetReferenceSecondUpdate.getMetadata().getDescription());
    assertEquals(
        TEST_BQ_DATASET_PROJECT, datasetReferenceSecondUpdate.getAttributes().getProjectId());
    assertEquals(
        TEST_BQ_DATASET_NAME_2, datasetReferenceSecondUpdate.getAttributes().getDatasetId());
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
        null);
    GcpBigQueryDataTableResource dataTableReferenceFirstUpdate =
        fullAccessApi.getBigQueryDataTableReference(getWorkspaceId(), bqTableResourceId);
    assertEquals(newDataTableName, dataTableReferenceFirstUpdate.getMetadata().getName());
    assertEquals(
        newDataTableDescription, dataTableReferenceFirstUpdate.getMetadata().getDescription());
    assertEquals(
        TEST_BQ_DATASET_PROJECT, dataTableReferenceFirstUpdate.getAttributes().getProjectId());
    assertEquals(
        TEST_BQ_DATASET_NAME, dataTableReferenceFirstUpdate.getAttributes().getDatasetId());
    assertEquals(
        TEST_BQ_DATATABLE_NAME, dataTableReferenceFirstUpdate.getAttributes().getDataTableId());

    // Update bq data table target
    GcpBigQueryDataTableAttributes dataTableAttributes = new GcpBigQueryDataTableAttributes();
    dataTableAttributes.setProjectId(TEST_BQ_DATASET_PROJECT);
    dataTableAttributes.setDatasetId(TEST_BQ_DATASET_NAME_2);
    dataTableAttributes.setDataTableId(TEST_BQ_DATATABLE_NAME_2);
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
                dataTableAttributes));
    // Successfully update the referencing target because the {@code userWithFullAccess} has
    // access to the bq table 2.
    ResourceMaker.updateBigQueryDataTableReference(
        fullAccessApi,
        getWorkspaceId(),
        bqTableResourceId,
        /*name=*/ null,
        /*description=*/ null,
        dataTableAttributes);

    GcpBigQueryDataTableResource dataTableReferenceSecondUpdate =
        fullAccessApi.getBigQueryDataTableReference(getWorkspaceId(), bqTableResourceId);
    assertEquals(newDataTableName, dataTableReferenceSecondUpdate.getMetadata().getName());
    assertEquals(
        newDataTableDescription, dataTableReferenceSecondUpdate.getMetadata().getDescription());
    assertEquals(
        TEST_BQ_DATASET_PROJECT, dataTableReferenceSecondUpdate.getAttributes().getProjectId());
    assertEquals(
        TEST_BQ_DATASET_NAME_2, dataTableReferenceSecondUpdate.getAttributes().getDatasetId());
    assertEquals(
        TEST_BQ_DATATABLE_NAME_2, dataTableReferenceSecondUpdate.getAttributes().getDataTableId());

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
    assertEquals(TEST_BUCKET_NAME, bucketReferenceFirstUpdate.getAttributes().getBucketName());
    assertTrue(partialAccessResourceApi.checkReferenceAccess(getWorkspaceId(), bucketResourceId));

    // Update GCS bucket referencing target from the uniform access bucket to the fine-grained
    // access bucket.
    GcpGcsBucketAttributes bucketAttributes = new GcpGcsBucketAttributes();
    bucketAttributes.setBucketName(TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS);
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
                bucketAttributes));
    // Successfully update the referencing target because the {@code userWithFullAccess} has
    // access to the bucket with fine-grained access.
    ResourceMaker.updateGcsBucketReference(
        fullAccessApi,
        getWorkspaceId(),
        bucketResourceId,
        /*name=*/ null,
        /*description=*/ null,
        bucketAttributes);
    GcpGcsBucketResource bucketReferenceSecondUpdate =
        fullAccessApi.getBucketReference(getWorkspaceId(), bucketResourceId);
    assertEquals(newBucketName, bucketReferenceSecondUpdate.getMetadata().getName());
    assertEquals(newBucketDescription, bucketReferenceSecondUpdate.getMetadata().getDescription());
    assertEquals(
        TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS,
        bucketReferenceSecondUpdate.getAttributes().getBucketName());

    // Update GCS bucket object's name and description
    String newBlobName = "newBlobName";
    String newBlobDescription = "a new description to the new bucket blob reference";
    ResourceMaker.updateGcsBucketObjectReference(
        fullAccessApi,
        getWorkspaceId(),
        bucketObjectResourceId,
        newBlobName,
        newBlobDescription,
        null);
    GcpGcsObjectResource blobResource =
        fullAccessApi.getGcsObjectReference(getWorkspaceId(), bucketObjectResourceId);
    assertEquals(newBlobName, blobResource.getMetadata().getName());
    assertEquals(newBlobDescription, blobResource.getMetadata().getDescription());
    assertEquals(
        TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS, blobResource.getAttributes().getBucketName());
    assertEquals(TEST_FILE_IN_FINE_GRAINED_BUCKET, blobResource.getAttributes().getFileName());

    // Update GCS bucket object's referencing target from foo/monkey_sees_monkey_dos.txt to foo/.
    GcpGcsObjectAttributes gcsObjectAttributes = new GcpGcsObjectAttributes();
    gcsObjectAttributes.bucketName(TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS);
    gcsObjectAttributes.fileName(TEST_FOLDER_IN_FINE_GRAINED_BUCKET);
    assertTrue(
        partialAccessResourceApi.checkReferenceAccess(getWorkspaceId(), bucketObjectResourceId));

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
                gcsObjectAttributes));
    // User with access to foo/ can successfully update the referencing target to foo/.
    ResourceMaker.updateGcsBucketObjectReference(
        fullAccessApi,
        getWorkspaceId(),
        bucketObjectResourceId,
        /*name=*/ null,
        /*description=*/ null,
        gcsObjectAttributes);
    GcpGcsObjectResource blobReferenceSecondUpdate =
        fullAccessApi.getGcsObjectReference(getWorkspaceId(), bucketObjectResourceId);
    assertEquals(
        TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS,
        blobReferenceSecondUpdate.getAttributes().getBucketName());
    assertEquals(
        TEST_FOLDER_IN_FINE_GRAINED_BUCKET,
        blobReferenceSecondUpdate.getAttributes().getFileName());
    assertEquals(newBlobName, blobReferenceSecondUpdate.getMetadata().getName());
    assertEquals(newBlobDescription, blobReferenceSecondUpdate.getMetadata().getDescription());
  }
}
