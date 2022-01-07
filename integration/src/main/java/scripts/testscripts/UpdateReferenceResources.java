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
import static scripts.utils.ClientTestUtils.TEST_FILE_FOO_MONKEY_SEES_MONKEY_DOS;
import static scripts.utils.ClientTestUtils.TEST_FOLDER_FOO;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.DataRepoSnapshotResource;
import bio.terra.workspace.model.GcpBigQueryDataTableResource;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpGcsBucketResource;
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

  private String dataRepoSnapshotId2;

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
            TEST_FILE_FOO_MONKEY_SEES_MONKEY_DOS);
    bucketObjectResourceId = blobResource.getMetadata().getResourceId();
  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
  }

  @Override
  public void setParameters(List<String> parameters) throws Exception {
    // TODO: Refactor this function when TestRunner starts supporting parameterMap
    super.setParameters(parameters);
    if (parameters == null || parameters.size() < 4) {
      throw new IllegalArgumentException(
          "Must provide Spend Profile ID, 2 Data Repo snapshot IDs, and 1 Data Repo Instance Names in the parameters list");
    } else {
      // "spendProfileId = parameters.get(0);" fetches Spend Profile ID and is already implemented
      // in the super class.
      // dataRepoSnapshotId and dataRepoInstanceName are the second and third value in the params
      // list and are implemented in the super class DataRepoTestScriptBase.
      dataRepoSnapshotId2 = parameters.get(3);
    }
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
    assertEquals(TEST_BQ_DATASET_NAME, datasetReferenceFirstUpdate.getAttributes().getDatasetId());
    assertEquals(
        TEST_BQ_DATASET_PROJECT, datasetReferenceFirstUpdate.getAttributes().getProjectId());
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
                TEST_BQ_DATASET_NAME_2));
    ResourceMaker.updateBigQueryDatasetReference(
        fullAccessApi,
        getWorkspaceId(),
        bqDatasetResourceId,
        /*name=*/ null,
        /*description=*/ null,
        /*projectId=*/ null,
        TEST_BQ_DATASET_NAME_2);
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
        /*projectId=*/ null,
        /*datasetId=*/ null,
        /*tableId=*/ null);
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
                TEST_BQ_DATASET_NAME_2,
                TEST_BQ_DATATABLE_NAME_2));
    // Successfully update the referencing target because the {@code userWithFullAccess} has
    // access to the bq table 2.
    ResourceMaker.updateBigQueryDataTableReference(
        fullAccessApi,
        getWorkspaceId(),
        bqTableResourceId,
        /*name=*/ null,
        /*description=*/ null,
        /*projectId=*/ null,
        TEST_BQ_DATASET_NAME_2,
        TEST_BQ_DATATABLE_NAME_2);

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

    ResourceMaker.updateBigQueryDataTableReference(
        fullAccessApi,
        getWorkspaceId(),
        bqTableResourceId,
        /*name=*/ null,
        /*description=*/ null,
        /*projectId=*/ null,
        TEST_BQ_DATASET_NAME,
        /*tableId=*/ null);

    GcpBigQueryDataTableResource dataTableReferenceThirdUpdate =
        fullAccessApi.getBigQueryDataTableReference(getWorkspaceId(), bqTableResourceId);
    assertEquals(newDataTableName, dataTableReferenceThirdUpdate.getMetadata().getName());
    assertEquals(
        newDataTableDescription, dataTableReferenceThirdUpdate.getMetadata().getDescription());
    assertEquals(
        TEST_BQ_DATASET_PROJECT, dataTableReferenceThirdUpdate.getAttributes().getProjectId());
    assertEquals(
        TEST_BQ_DATASET_NAME, dataTableReferenceThirdUpdate.getAttributes().getDatasetId());
    assertEquals(
        TEST_BQ_DATATABLE_NAME_2, dataTableReferenceThirdUpdate.getAttributes().getDataTableId());

    ResourceMaker.updateBigQueryDataTableReference(
        fullAccessApi,
        getWorkspaceId(),
        bqTableResourceId,
        /*name=*/ null,
        /*description=*/ null,
        /*projectId=*/ null,
        /*datasetId=*/ null,
        TEST_BQ_DATATABLE_NAME);
    GcpBigQueryDataTableResource dataTableReferenceFourthUpdate =
        fullAccessApi.getBigQueryDataTableReference(getWorkspaceId(), bqTableResourceId);
    assertEquals(newDataTableName, dataTableReferenceFourthUpdate.getMetadata().getName());
    assertEquals(
        newDataTableDescription, dataTableReferenceFourthUpdate.getMetadata().getDescription());
    assertEquals(
        TEST_BQ_DATASET_PROJECT, dataTableReferenceFourthUpdate.getAttributes().getProjectId());
    assertEquals(
        TEST_BQ_DATASET_NAME, dataTableReferenceFourthUpdate.getAttributes().getDatasetId());
    assertEquals(
        TEST_BQ_DATATABLE_NAME, dataTableReferenceFourthUpdate.getAttributes().getDataTableId());

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
                TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS));
    // Successfully update the referencing target because the {@code userWithFullAccess} has
    // access to the bucket with fine-grained access.
    ResourceMaker.updateGcsBucketReference(
        fullAccessApi,
        getWorkspaceId(),
        bucketResourceId,
        /*name=*/ null,
        /*description=*/ null,
        TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS);
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
        /*bucketName=*/ null,
        /*objectName=*/ null);
    GcpGcsObjectResource blobResource =
        fullAccessApi.getGcsObjectReference(getWorkspaceId(), bucketObjectResourceId);
    assertEquals(newBlobName, blobResource.getMetadata().getName());
    assertEquals(newBlobDescription, blobResource.getMetadata().getDescription());
    assertEquals(
        TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS, blobResource.getAttributes().getBucketName());
    assertEquals(TEST_FILE_FOO_MONKEY_SEES_MONKEY_DOS, blobResource.getAttributes().getFileName());

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
                TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS,
                TEST_FOLDER_FOO));
    // User with access to foo/ can successfully update the referencing target to foo/.
    ResourceMaker.updateGcsBucketObjectReference(
        fullAccessApi,
        getWorkspaceId(),
        bucketObjectResourceId,
        /*name=*/ null,
        /*description=*/ null,
        /*bucketName=*/ null,
        TEST_FOLDER_FOO);
    GcpGcsObjectResource blobReferenceSecondUpdate =
        fullAccessApi.getGcsObjectReference(getWorkspaceId(), bucketObjectResourceId);
    assertEquals(
        TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS,
        blobReferenceSecondUpdate.getAttributes().getBucketName());
    assertEquals(TEST_FOLDER_FOO, blobReferenceSecondUpdate.getAttributes().getFileName());
    assertEquals(newBlobName, blobReferenceSecondUpdate.getMetadata().getName());
    assertEquals(newBlobDescription, blobReferenceSecondUpdate.getMetadata().getDescription());

    // update bucket only.
    ResourceMaker.updateGcsBucketObjectReference(
        fullAccessApi,
        getWorkspaceId(),
        bucketObjectResourceId,
        /*name=*/ null,
        /*description=*/ null,
        /*bucketName=*/ TEST_BUCKET_NAME,
        null);
    GcpGcsObjectResource blobReferenceThirdUpdate =
        fullAccessApi.getGcsObjectReference(getWorkspaceId(), bucketObjectResourceId);
    assertEquals(TEST_BUCKET_NAME, blobReferenceThirdUpdate.getAttributes().getBucketName());
    assertEquals(TEST_FOLDER_FOO, blobReferenceThirdUpdate.getAttributes().getFileName());
    assertEquals(newBlobName, blobReferenceThirdUpdate.getMetadata().getName());
    assertEquals(newBlobDescription, blobReferenceThirdUpdate.getMetadata().getDescription());

    // Update both bucket and object path.
    ResourceMaker.updateGcsBucketObjectReference(
        fullAccessApi,
        getWorkspaceId(),
        bucketObjectResourceId,
        /*name=*/ null,
        /*description=*/ null,
        /*bucketName=*/ TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS,
        TEST_FILE_FOO_MONKEY_SEES_MONKEY_DOS);
    GcpGcsObjectResource blobReferenceFourthUpdate =
        fullAccessApi.getGcsObjectReference(getWorkspaceId(), bucketObjectResourceId);
    assertEquals(
        TEST_BUCKET_NAME_WITH_FINE_GRAINED_ACCESS,
        blobReferenceFourthUpdate.getAttributes().getBucketName());
    assertEquals(
        TEST_FILE_FOO_MONKEY_SEES_MONKEY_DOS,
        blobReferenceFourthUpdate.getAttributes().getFileName());
    assertEquals(newBlobName, blobReferenceFourthUpdate.getMetadata().getName());
    assertEquals(newBlobDescription, blobReferenceFourthUpdate.getMetadata().getDescription());
  }
}
