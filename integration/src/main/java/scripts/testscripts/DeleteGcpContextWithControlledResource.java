package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.BqDatasetUtils;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class DeleteGcpContextWithControlledResource extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger =
      LoggerFactory.getLogger(DeleteGcpContextWithControlledResource.class);

  private static final String DATASET_RESOURCE_NAME = "wsmtest_dataset";

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ControlledGcpResourceApi controlledResourceApi =
        ClientTestUtils.getControlledGcpResourceClient(testUser, server);
    ReferencedGcpResourceApi referencedResourceApi =
        ClientTestUtils.getReferencedGcpResourceClient(testUser, server);

    // Create a cloud context
    String projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceUuid(), workspaceApi);
    logger.info("Created project {}", projectId);

    // Create a controlled BigQuery dataset
    GcpBigQueryDatasetResource controlledDataset =
        BqDatasetUtils.makeControlledBigQueryDatasetUserShared(
            controlledResourceApi, getWorkspaceUuid(), DATASET_RESOURCE_NAME, null, null);
    UUID controlledResourceId = controlledDataset.getMetadata().getResourceId();
    logger.info("Created controlled dataset {}", controlledResourceId);

    // Confirm the dataset was created in WSM
    GcpBigQueryDatasetResource fetchedControlledDataset =
        controlledResourceApi.getBigQueryDataset(getWorkspaceUuid(), controlledResourceId);
    assertEquals(controlledDataset, fetchedControlledDataset);

    // Create a reference to the controlled resource we just created
    String referenceName = "my-resource-name-" + UUID.randomUUID().toString();
    GcpBigQueryDatasetResource referencedDataset =
        BqDatasetUtils.makeBigQueryDatasetReference(
            controlledDataset.getAttributes(),
            referencedResourceApi,
            getWorkspaceUuid(),
            referenceName);

    // Confirm the reference was created in WSM
    GcpBigQueryDatasetResource fetchedDatasetReference =
        referencedResourceApi.getBigQueryDatasetReference(
            getWorkspaceUuid(), referencedDataset.getMetadata().getResourceId());
    assertEquals(referencedDataset, fetchedDatasetReference);

    // Delete the context, which should delete the controlled resource but not the reference.
    CloudContextMaker.deleteGcpCloudContext(getWorkspaceUuid(), workspaceApi);

    // Confirm the controlled resource was deleted.
    var noGcpContextException =
        assertThrows(
            ApiException.class,
            () -> controlledResourceApi.getBigQueryDataset(getWorkspaceUuid(), controlledResourceId));
    assertEquals(HttpStatus.SC_NOT_FOUND, noGcpContextException.getCode());

    // Confirm the referenced resource was not deleted (even though the underlying cloud resource
    // was).
    GcpBigQueryDatasetResource datasetReferenceAfterDelete =
        referencedResourceApi.getBigQueryDatasetReference(
            getWorkspaceUuid(), referencedDataset.getMetadata().getResourceId());
    assertEquals(referencedDataset, datasetReferenceAfterDelete);
  }
}
