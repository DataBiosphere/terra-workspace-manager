package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.model.DataRepoSnapshotAttributes;
import bio.terra.workspace.model.DataRepoSnapshotResource;
import bio.terra.workspace.model.GcpBigQueryDataTableAttributes;
import bio.terra.workspace.model.GcpBigQueryDataTableResource;
import bio.terra.workspace.model.GcpBigQueryDatasetAttributes;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpGcsBucketAttributes;
import bio.terra.workspace.model.GcpGcsBucketResource;
import java.util.List;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import scripts.utils.ClientTestUtils;
import scripts.utils.DataRepoTestScriptBase;
import scripts.utils.ResourceMaker;

public class UpdateReferenceResources extends DataRepoTestScriptBase {

  private TestUserSpecification userWithPartialAccess;

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
    userWithPartialAccess = testUsers.get(0);

    ApiClient apiClient = ClientTestUtils.getClientForTestUser(userWithPartialAccess, server);
    ReferencedGcpResourceApi referencedGcpResourceApi = new ReferencedGcpResourceApi(apiClient);

    GcpBigQueryDatasetResource bqDatasetReference =
        ResourceMaker.makeBigQueryDatasetReference(
            referencedGcpResourceApi, getWorkspaceId(), "bqDatasetReference");
    bqDatasetResourceId = bqDatasetReference.getMetadata().getResourceId();
    GcpBigQueryDataTableResource bqDataTableReference =
        ResourceMaker.makeBigQueryDataTableReference(
            referencedGcpResourceApi, getWorkspaceId(), "bqTableReference");
    bqTableResourceId = bqDataTableReference.getMetadata().getResourceId();
    DataRepoSnapshotResource snapshotResource = ResourceMaker.makeDataRepoSnapshotReference(referencedGcpResourceApi, getWorkspaceId(), "dataRepoReference",
        getDataRepoSnapshotId(), getDataRepoInstanceName());
    dataRepoSnapshotResourceId = snapshotResource.getMetadata().getResourceId();
    GcpGcsBucketResource bucketResource =
        ResourceMaker.makeGcsBucketReference(referencedGcpResourceApi, getWorkspaceId(), "bucketReference");
    bucketResourceId = bucketResource.getMetadata().getResourceId();
  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ApiClient apiClient = ClientTestUtils.getClientForTestUser(userWithPartialAccess, server);
    ReferencedGcpResourceApi referencedGcpResourceApi = new ReferencedGcpResourceApi(apiClient);

    // Update snapshot
    DataRepoSnapshotAttributes snapshotAttributes = new DataRepoSnapshotAttributes();
    String newSnapshotReferenceName = "newSnapshotReferenceName";
    String newSnapshotReferenceDescription = "a new description of another snapthot reference";
    String newSnapshotId = "newSnapshotId";
    String newInstanceName = "newInstanceName";
    // snapshotAttributes.setSnapshot(newSnapshotId);
    // snapshotAttributes.setInstanceName(newInstanceName);
    ResourceMaker.updateDataRepoSnapshotReferenceResource(
        referencedGcpResourceApi, getWorkspaceId(), dataRepoSnapshotResourceId,
        newSnapshotReferenceName, newSnapshotReferenceDescription, null);
    DataRepoSnapshotResource snapshotResource =
        referencedGcpResourceApi.getDataRepoSnapshotReference(getWorkspaceId(), dataRepoSnapshotResourceId);
    assertEquals(newSnapshotReferenceName, snapshotResource.getMetadata().getName());
    assertEquals(newSnapshotReferenceDescription, snapshotResource.getMetadata().getDescription());

    // Update BQ dataset
    String newDatasetName = "newDatasetName";
    String newDatasetDescription = "newDescription";
    GcpBigQueryDatasetAttributes datasetAttributes = new GcpBigQueryDatasetAttributes();
    ResourceMaker.updateBigQueryDatasetReference(referencedGcpResourceApi, getWorkspaceId(), bqDatasetResourceId,
        newDatasetName, newDatasetDescription, null);
    GcpBigQueryDatasetResource datasetResource = referencedGcpResourceApi.getBigQueryDatasetReference(getWorkspaceId(), bqDatasetResourceId);
    assertEquals(newDatasetName, datasetResource.getMetadata().getName());
    assertEquals(newDatasetDescription, datasetResource.getMetadata().getDescription());

    // Update BQ data table
    String newDataTableName = "newDataTableName";
    String newDataTableDescription = "a new description to the new data table reference";
    GcpBigQueryDataTableAttributes dataTableAttributes = new GcpBigQueryDataTableAttributes();
    ResourceMaker.updateBigQueryDataTableReference(referencedGcpResourceApi, getWorkspaceId(), bqTableResourceId,
        newDataTableName, newDataTableDescription, null);
    GcpBigQueryDataTableResource dataTableResource =
        referencedGcpResourceApi.getBigQueryDataTableReference(getWorkspaceId(), bqTableResourceId);
    assertEquals(newDataTableName, dataTableResource.getMetadata().getName());
    assertEquals(newDataTableDescription, dataTableResource.getMetadata().getDescription());

    // Update GCS bucket
    String newBucketName = "newGcsBucket";
    String newBucketDescription = "a new description to the new bucket reference";
    GcpGcsBucketAttributes bucketAttributes = new GcpGcsBucketAttributes();
    ResourceMaker.updateGcsBucketReference(referencedGcpResourceApi, getWorkspaceId(), bucketResourceId, newBucketName, newBucketDescription, null);
    GcpGcsBucketResource bucketResource = referencedGcpResourceApi.getBucketReference(getWorkspaceId(), bucketResourceId);
    assertEquals(newBucketName, bucketResource.getMetadata().getName());
    assertEquals(newBucketDescription, bucketResource.getMetadata().getDescription());

    // Update GCS bucket object
    String newBlobName = "newBlobName";
    String newblobDescription = "a new description to the new bucket blob reference";

  }
}
