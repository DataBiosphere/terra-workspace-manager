package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.GcpBigQueryDataTableAttributes;
import bio.terra.workspace.model.GcpBigQueryDataTableResource;
import bio.terra.workspace.model.GcpBigQueryDatasetAttributes;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import java.util.Map;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.BqDatasetUtils;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.ParameterUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class DeleteGcpContextWithControlledResource extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger =
      LoggerFactory.getLogger(DeleteGcpContextWithControlledResource.class);

  private static final String DATASET_RESOURCE_NAME = "wsmtest_dataset";

  private GcpBigQueryDataTableAttributes dataTableAttributes;

  public void setParameters(Map<String, String> parameters) throws Exception {
    super.setParameters(parameters);
    dataTableAttributes = ParameterUtils.getBigQueryDataTableReference(parameters);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ControlledGcpResourceApi controlledResourceApi =
        ClientTestUtils.getControlledGcpResourceClient(testUser, server);
    ReferencedGcpResourceApi referencedResourceApi =
        ClientTestUtils.getReferencedGpcResourceClient(testUser, server);

    // Create a cloud context
    String projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
    logger.info("Created project {}", projectId);

    // Create a controlled BigQuery dataset
    GcpBigQueryDatasetResource controlledDataset =
        BqDatasetUtils.makeControlledBigQueryDatasetUserShared(
            controlledResourceApi, getWorkspaceId(), DATASET_RESOURCE_NAME, null, null);
    UUID controlledResourceId = controlledDataset.getMetadata().getResourceId();
    logger.info("Created controlled dataset {}", controlledResourceId);

    // Confirm the dataset was created in WSM
    GcpBigQueryDatasetResource fetchedControlledDataset =
        controlledResourceApi.getBigQueryDataset(getWorkspaceId(), controlledResourceId);
    assertEquals(controlledDataset, fetchedControlledDataset);

    // Create a referenced BigQuery dataset
    // Reference the same table as the BigQuery table parameter
    GcpBigQueryDatasetAttributes datasetAttributes =
        new GcpBigQueryDatasetAttributes()
            .projectId(dataTableAttributes.getProjectId())
            .datasetId(dataTableAttributes.getDatasetId());
    String resourceName = "my-resource-name-" + UUID.randomUUID().toString();
    GcpBigQueryDatasetResource referencedDataset =
        BqDatasetUtils.makeBigQueryDatasetReference(
            datasetAttributes, referencedResourceApi, getWorkspaceId(), resourceName);
    UUID referencedResourceId = referencedDataset.getMetadata().getResourceId();
    logger.info("Created referenced dataset {}", referencedResourceId);

    // Confirm the reference was created in WSM
    GcpBigQueryDatasetResource fetchedDatasetReference =
        referencedResourceApi.getBigQueryDatasetReference(getWorkspaceId(), referencedResourceId);
    assertEquals(referencedDataset, fetchedDatasetReference);

    // Create a referenced BigQuery data table
    String bqDataReferenceResourceName = "my-resource-name-" + UUID.randomUUID().toString();
    GcpBigQueryDataTableResource referencedDataTable =
        BqDatasetUtils.makeBigQueryDataTableReference(
            dataTableAttributes,
            referencedResourceApi,
            getWorkspaceId(),
            bqDataReferenceResourceName);
    UUID bqDataTableReferencedResourceId = referencedDataTable.getMetadata().getResourceId();
    logger.info("Created referenced dataset {}", bqDataTableReferencedResourceId);

    // Confirm the reference was created in WSM
    GcpBigQueryDataTableResource fetchedDataTableReference =
        referencedResourceApi.getBigQueryDataTableReference(
            getWorkspaceId(), bqDataTableReferencedResourceId);
    assertEquals(referencedDataTable, fetchedDataTableReference);

    // Delete the context, which should delete the controlled resource but not the reference.
    CloudContextMaker.deleteGcpCloudContext(getWorkspaceId(), workspaceApi);

    // Confirm the controlled resource was deleted.
    var noGcpContextException =
        assertThrows(
            ApiException.class,
            () -> controlledResourceApi.getBigQueryDataset(getWorkspaceId(), controlledResourceId));
    assertEquals(HttpStatus.SC_NOT_FOUND, noGcpContextException.getCode());

    // Confirm the referenced resource was not deleted.
    GcpBigQueryDatasetResource datasetReferenceAfterDelete =
        referencedResourceApi.getBigQueryDatasetReference(getWorkspaceId(), referencedResourceId);
    assertEquals(referencedDataset, datasetReferenceAfterDelete);
    GcpBigQueryDataTableResource dataTableReferenceAfterDelete =
        referencedResourceApi.getBigQueryDataTableReference(
            getWorkspaceId(), bqDataTableReferencedResourceId);
    assertEquals(referencedDataTable, dataTableReferenceAfterDelete);
  }
}
