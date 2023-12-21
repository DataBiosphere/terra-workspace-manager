package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static scripts.utils.BqDatasetUtils.assertDatasetsAreEqualIgnoringLastUpdatedDate;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import java.util.List;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.BqDatasetUtils;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class DeleteWorkspaceWithControlledResource extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger =
      LoggerFactory.getLogger(DeleteWorkspaceWithControlledResource.class);

  private static final String DATASET_RESOURCE_NAME = "wsmtest_dataset";

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ControlledGcpResourceApi resourceApi =
        ClientTestUtils.getControlledGcpResourceClient(testUser, server);

    // Create a cloud context
    String projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
    logger.info("Created project {}", projectId);

    // Create a shared BigQuery dataset
    GcpBigQueryDatasetResource createdDataset =
        BqDatasetUtils.makeControlledBigQueryDatasetUserShared(
            resourceApi, getWorkspaceId(), DATASET_RESOURCE_NAME, null, null);
    UUID resourceId = createdDataset.getMetadata().getResourceId();
    logger.info("Created controlled dataset {}", resourceId);

    // Confirm the dataset was created in WSM
    GcpBigQueryDatasetResource fetchedDataset =
        resourceApi.getBigQueryDataset(getWorkspaceId(), resourceId);
    assertDatasetsAreEqualIgnoringLastUpdatedDate(createdDataset, fetchedDataset);

    // Delete the workspace, which should delete the included context and resource
    WorkspaceAllocateTestScriptBase.deleteWorkspaceAsyncAssertSuccess(
        workspaceApi, getWorkspaceId());

    // Confirm the workspace is deleted
    var workspaceMissingException =
        assertThrows(
            ApiException.class,
            () -> workspaceApi.getWorkspace(getWorkspaceId(), /* minimumHighestRole= */ null));
    assertEquals(HttpStatus.SC_NOT_FOUND, workspaceMissingException.getCode());

    // Confirm the controlled resource was deleted
    var resourceMissingException =
        assertThrows(
            ApiException.class, () -> resourceApi.getBigQueryDataset(getWorkspaceId(), resourceId));
    assertEquals(HttpStatus.SC_NOT_FOUND, resourceMissingException.getCode());
  }

  @Override
  public void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    try {
      WorkspaceAllocateTestScriptBase.deleteWorkspaceAsync(workspaceApi, getWorkspaceId());
    } catch (ApiException e) {
      // If this test succeeds, it will clean up the workspace as part of the user journey, meaning
      // a "not found" exception should not be considered an error here.
      assertEquals(HttpStatus.SC_NOT_FOUND, e.getCode());
    }
  }
}
