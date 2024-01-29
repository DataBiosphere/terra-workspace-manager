package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloneReferencedGcpBigQueryDataTableResourceResult;
import bio.terra.workspace.model.CloneReferencedGcpBigQueryDatasetResourceResult;
import bio.terra.workspace.model.CloneReferencedResourceRequestBody;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.GcpBigQueryDataTableAttributes;
import bio.terra.workspace.model.GcpBigQueryDataTableResource;
import bio.terra.workspace.model.GcpBigQueryDatasetAttributes;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ResourceList;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import scripts.utils.BqDataTableUtils;
import scripts.utils.BqDatasetUtils;
import scripts.utils.ClientTestUtils;
import scripts.utils.CommonResourceFieldsUtil;
import scripts.utils.MultiResourcesUtils;
import scripts.utils.ParameterUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class ReferencedBigQueryResourceLifecycle extends WorkspaceAllocateTestScriptBase {

  private TestUserSpecification noAccessUser;
  private TestUserSpecification partialAccessUser;
  private GcpBigQueryDatasetAttributes referencedBqDatasetAttributes;
  private GcpBigQueryDataTableAttributes referencedBqTableAttributes;
  private GcpBigQueryDataTableAttributes bqTableFromAlternateDatasetAttributes;
  private UUID destinationWorkspaceId;
  private UUID bqDatasetResourceId;
  private UUID bqDataTableResourceId;

  public void setParametersMap(Map<String, String> parametersMap) throws Exception {
    super.setParametersMap(parametersMap);
    referencedBqDatasetAttributes = ParameterUtils.getBigQueryDatasetReference(parametersMap);
    referencedBqTableAttributes = ParameterUtils.getBigQueryDataTableReference(parametersMap);
    bqTableFromAlternateDatasetAttributes =
        ParameterUtils.getBigQueryDataTableFromAlternateDatasetReference(parametersMap);
  }

  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    assertThat(
        "There must be at least three test users defined for this test.",
        testUsers != null && testUsers.size() > 2);
    this.noAccessUser = testUsers.get(1);
    this.partialAccessUser = testUsers.get(2);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ReferencedGcpResourceApi referencedGcpResourceApi =
        ClientTestUtils.getReferencedGcpResourceClient(testUser, server);
    // Grant secondary users READER permission in the workspace.
    ClientTestUtils.grantRole(workspaceApi, getWorkspaceId(), partialAccessUser, IamRole.READER);
    ClientTestUtils.grantRole(workspaceApi, getWorkspaceId(), noAccessUser, IamRole.READER);

    // Create the references
    GcpBigQueryDatasetResource referencedDataset =
        BqDatasetUtils.makeBigQueryDatasetReference(
            referencedBqDatasetAttributes,
            referencedGcpResourceApi,
            getWorkspaceId(),
            MultiResourcesUtils.makeName(),
            CloningInstructionsEnum.REFERENCE);
    bqDatasetResourceId = referencedDataset.getMetadata().getResourceId();
    assertEquals(
        CommonResourceFieldsUtil.getResourceDefaultProperties(),
        referencedDataset.getMetadata().getProperties());
    GcpBigQueryDataTableResource referencedDataTable =
        BqDatasetUtils.makeBigQueryDataTableReference(
            referencedBqTableAttributes,
            referencedGcpResourceApi,
            getWorkspaceId(),
            MultiResourcesUtils.makeName(),
            CloningInstructionsEnum.REFERENCE);
    bqDataTableResourceId = referencedDataTable.getMetadata().getResourceId();
    assertEquals(
        CommonResourceFieldsUtil.getResourceDefaultProperties(),
        referencedDataTable.getMetadata().getProperties());

    // Get references
    testGetReferences(referencedDataset, referencedDataTable, referencedGcpResourceApi);

    // Clone references
    testCloneReferences(
        referencedDataset, referencedDataTable, referencedGcpResourceApi, workspaceApi);

    // Validate reference access
    testValidateReferences(testUser);

    // Update the references
    testUpdateReferences(referencedDataset, referencedDataTable, referencedGcpResourceApi);

    // Delete the references
    referencedGcpResourceApi.deleteBigQueryDatasetReference(getWorkspaceId(), bqDatasetResourceId);
    referencedGcpResourceApi.deleteBigQueryDataTableReference(
        getWorkspaceId(), bqDataTableResourceId);

    // Enumerating all resources with no filters should be empty
    ResourceApi resourceApi = ClientTestUtils.getResourceClient(testUser, server);
    ResourceList enumerateResult =
        resourceApi.enumerateResources(getWorkspaceId(), 0, 100, null, null);
    assertTrue(enumerateResult.getResources().isEmpty());
  }

  private void testGetReferences(
      GcpBigQueryDatasetResource referencedDataset,
      GcpBigQueryDataTableResource referencedDataTable,
      ReferencedGcpResourceApi referencedGcpResourceApi)
      throws Exception {
    // Get the references
    GcpBigQueryDatasetResource fetchedDataset =
        referencedGcpResourceApi.getBigQueryDatasetReference(getWorkspaceId(), bqDatasetResourceId);
    assertEquals(referencedDataset, fetchedDataset);
    GcpBigQueryDataTableResource fetchedDataTable =
        referencedGcpResourceApi.getBigQueryDataTableReference(
            getWorkspaceId(), bqDataTableResourceId);
    assertEquals(referencedDataTable, fetchedDataTable);
    // Enumerate the references
    // Any workspace member can view references in WSM, even if they can't view the underlying cloud
    // resource or contents.
    ResourceApi noAccessApi = ClientTestUtils.getResourceClient(noAccessUser, server);
    ResourceList referenceList =
        noAccessApi.enumerateResources(
            getWorkspaceId(), 0, 5, /* referenceType= */ null, StewardshipType.REFERENCED);
    assertEquals(2, referenceList.getResources().size());
    ResourceList datasetList =
        noAccessApi.enumerateResources(
            getWorkspaceId(),
            0,
            5,
            /* referenceType= */ ResourceType.BIG_QUERY_DATASET,
            StewardshipType.REFERENCED);
    assertEquals(1, datasetList.getResources().size());
    MultiResourcesUtils.assertResourceType(ResourceType.BIG_QUERY_DATASET, datasetList);
    ResourceList tableList =
        noAccessApi.enumerateResources(
            getWorkspaceId(),
            0,
            5,
            /* referenceType= */ ResourceType.BIG_QUERY_DATA_TABLE,
            StewardshipType.REFERENCED);
    assertEquals(1, tableList.getResources().size());
    MultiResourcesUtils.assertResourceType(ResourceType.BIG_QUERY_DATA_TABLE, tableList);
  }

  private void testValidateReferences(TestUserSpecification owner) throws Exception {
    ResourceApi ownerApi = new ResourceApi(ClientTestUtils.getClientForTestUser(owner, server));
    ResourceApi noAccessUserApi =
        new ResourceApi(ClientTestUtils.getClientForTestUser(noAccessUser, server));
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), bqDatasetResourceId));
    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), bqDataTableResourceId));
    assertFalse(noAccessUserApi.checkReferenceAccess(getWorkspaceId(), bqDatasetResourceId));
    assertFalse(noAccessUserApi.checkReferenceAccess(getWorkspaceId(), bqDataTableResourceId));
  }

  private void testCloneReferences(
      GcpBigQueryDatasetResource sourceDataset,
      GcpBigQueryDataTableResource sourceDataTable,
      ReferencedGcpResourceApi referencedGcpResourceApi,
      WorkspaceApi workspaceApi)
      throws Exception {
    // Create a second workspace to clone references into, owned by the same user
    destinationWorkspaceId = UUID.randomUUID();
    createWorkspace(destinationWorkspaceId, getSpendProfileId(), workspaceApi);
    CloneReferencedGcpBigQueryDatasetResourceResult datasetCloneResult =
        referencedGcpResourceApi.cloneGcpBigQueryDatasetReference(
            new CloneReferencedResourceRequestBody().destinationWorkspaceId(destinationWorkspaceId),
            getWorkspaceId(),
            bqDatasetResourceId);
    assertEquals(getWorkspaceId(), datasetCloneResult.getSourceWorkspaceId());
    assertEquals(sourceDataset.getAttributes(), datasetCloneResult.getResource().getAttributes());

    CloneReferencedGcpBigQueryDataTableResourceResult dataTableCloneResult =
        referencedGcpResourceApi.cloneGcpBigQueryDataTableReference(
            new CloneReferencedResourceRequestBody().destinationWorkspaceId(destinationWorkspaceId),
            getWorkspaceId(),
            bqDataTableResourceId);
    assertEquals(getWorkspaceId(), dataTableCloneResult.getSourceWorkspaceId());
    assertEquals(
        sourceDataTable.getAttributes(), dataTableCloneResult.getResource().getAttributes());
  }

  private void testUpdateReferences(
      GcpBigQueryDatasetResource dataset,
      GcpBigQueryDataTableResource table,
      ReferencedGcpResourceApi fullAccessApi)
      throws Exception {
    ReferencedGcpResourceApi partialAccessApi =
        ClientTestUtils.getReferencedGcpResourceClient(partialAccessUser, server);
    ResourceApi partialAccessResourceApi =
        ClientTestUtils.getResourceClient(partialAccessUser, server);
    UUID bqDatasetResourceId = dataset.getMetadata().getResourceId();
    UUID bqTableResourceId = table.getMetadata().getResourceId();
    // Update BQ dataset's name and description
    String newDatasetName = "newDatasetName";
    String newDatasetDescription = "newDescription";
    GcpBigQueryDatasetResource datasetReferenceFirstUpdate =
        BqDatasetUtils.updateBigQueryDatasetReference(
            fullAccessApi,
            getWorkspaceId(),
            bqDatasetResourceId,
            newDatasetName,
            newDatasetDescription,
            /* projectId= */ null,
            /* datasetId= */ null,
            CloningInstructionsEnum.NOTHING);
    assertEquals(newDatasetName, datasetReferenceFirstUpdate.getMetadata().getName());
    assertEquals(newDatasetDescription, datasetReferenceFirstUpdate.getMetadata().getDescription());
    assertEquals(
        referencedBqTableAttributes.getDatasetId(),
        datasetReferenceFirstUpdate.getAttributes().getDatasetId());
    assertEquals(
        referencedBqTableAttributes.getProjectId(),
        datasetReferenceFirstUpdate.getAttributes().getProjectId());
    assertEquals(
        CloningInstructionsEnum.NOTHING,
        datasetReferenceFirstUpdate.getMetadata().getCloningInstructions());
    // {@code userWithPartialAccess} does not have access to the original dataset.
    assertFalse(
        partialAccessResourceApi.checkReferenceAccess(getWorkspaceId(), bqDatasetResourceId));

    // Update BQ dataset's referencing target

    // Attempt to update the referencing target but {@code userWithPartialAccess} does not have
    // access to the original dataset.
    assertThrows(
        ApiException.class,
        () ->
            BqDatasetUtils.updateBigQueryDatasetReference(
                partialAccessApi,
                getWorkspaceId(),
                bqDatasetResourceId,
                /* name= */ null,
                /* description= */ null,
                /* projectId= */ null,
                bqTableFromAlternateDatasetAttributes.getDatasetId(),
                /* cloningInstructions= */ null));
    GcpBigQueryDatasetResource datasetReferenceSecondUpdate =
        BqDatasetUtils.updateBigQueryDatasetReference(
            fullAccessApi,
            getWorkspaceId(),
            bqDatasetResourceId,
            /* name= */ null,
            /* description= */ null,
            /* projectId= */ null,
            bqTableFromAlternateDatasetAttributes.getDatasetId(),
            CloningInstructionsEnum.NOTHING);
    assertEquals(newDatasetName, datasetReferenceSecondUpdate.getMetadata().getName());
    assertEquals(
        newDatasetDescription, datasetReferenceSecondUpdate.getMetadata().getDescription());
    assertEquals(
        table.getAttributes().getProjectId(),
        datasetReferenceSecondUpdate.getAttributes().getProjectId());
    assertEquals(
        bqTableFromAlternateDatasetAttributes.getDatasetId(),
        datasetReferenceSecondUpdate.getAttributes().getDatasetId());
    // {@code userWithPartialAccess} have access to dataset 2. Now since the reference is pointing
    // to dataset 2, the user have access to this reference now.
    assertTrue(
        partialAccessResourceApi.checkReferenceAccess(getWorkspaceId(), bqDatasetResourceId));
    assertEquals(
        CloningInstructionsEnum.NOTHING,
        datasetReferenceSecondUpdate.getMetadata().getCloningInstructions());

    // Update BQ data table's name and description.
    String newDataTableName = "newDataTableName";
    String newDataTableDescription = "a new description to the new data table reference";
    GcpBigQueryDataTableResource dataTableReferenceFirstUpdate =
        BqDataTableUtils.updateBigQueryDataTableReference(
            fullAccessApi,
            getWorkspaceId(),
            bqTableResourceId,
            newDataTableName,
            newDataTableDescription,
            /* projectId= */ null,
            /* datasetId= */ null,
            /* tableId= */ null,
            /* cloningInstructions= */ null);
    assertEquals(newDataTableName, dataTableReferenceFirstUpdate.getMetadata().getName());
    assertEquals(
        newDataTableDescription, dataTableReferenceFirstUpdate.getMetadata().getDescription());
    assertEquals(
        table.getAttributes().getProjectId(),
        dataTableReferenceFirstUpdate.getAttributes().getProjectId());
    assertEquals(
        table.getAttributes().getDatasetId(),
        dataTableReferenceFirstUpdate.getAttributes().getDatasetId());
    assertEquals(
        table.getAttributes().getDataTableId(),
        dataTableReferenceFirstUpdate.getAttributes().getDataTableId());

    // Update bq data table target

    // Attempt to update bq data table reference but {@code userWithPartialAccess} does not have
    // access to the bq table 2.
    assertThrows(
        ApiException.class,
        () ->
            BqDataTableUtils.updateBigQueryDataTableReference(
                partialAccessApi,
                getWorkspaceId(),
                bqTableResourceId,
                /* name= */ null,
                /* description= */ null,
                /* projectId= */ null,
                bqTableFromAlternateDatasetAttributes.getDatasetId(),
                bqTableFromAlternateDatasetAttributes.getDataTableId(),
                /* cloningInstructions= */ null));
    // Successfully update the referencing target because the {@code userWithFullAccess} has
    // access to the bq table 2.
    GcpBigQueryDataTableResource dataTableReferenceSecondUpdate =
        BqDataTableUtils.updateBigQueryDataTableReference(
            fullAccessApi,
            getWorkspaceId(),
            bqTableResourceId,
            /* name= */ null,
            /* description= */ null,
            /* projectId= */ null,
            bqTableFromAlternateDatasetAttributes.getDatasetId(),
            bqTableFromAlternateDatasetAttributes.getDataTableId(),
            /* cloningInstructions= */ null);

    assertEquals(newDataTableName, dataTableReferenceSecondUpdate.getMetadata().getName());
    assertEquals(
        newDataTableDescription, dataTableReferenceSecondUpdate.getMetadata().getDescription());
    assertEquals(
        table.getAttributes().getProjectId(),
        dataTableReferenceSecondUpdate.getAttributes().getProjectId());
    assertEquals(
        bqTableFromAlternateDatasetAttributes.getDatasetId(),
        dataTableReferenceSecondUpdate.getAttributes().getDatasetId());
    assertEquals(
        bqTableFromAlternateDatasetAttributes.getDataTableId(),
        dataTableReferenceSecondUpdate.getAttributes().getDataTableId());

    GcpBigQueryDataTableResource dataTableReferenceThirdUpdate =
        BqDataTableUtils.updateBigQueryDataTableReference(
            fullAccessApi,
            getWorkspaceId(),
            bqTableResourceId,
            /* name= */ null,
            /* description= */ null,
            /* projectId= */ null,
            table.getAttributes().getDatasetId(),
            /* tableId= */ null,
            /* cloningInstructions= */ null);

    assertEquals(newDataTableName, dataTableReferenceThirdUpdate.getMetadata().getName());
    assertEquals(
        newDataTableDescription, dataTableReferenceThirdUpdate.getMetadata().getDescription());
    assertEquals(
        table.getAttributes().getProjectId(),
        dataTableReferenceThirdUpdate.getAttributes().getProjectId());
    assertEquals(
        table.getAttributes().getDatasetId(),
        dataTableReferenceThirdUpdate.getAttributes().getDatasetId());
    assertEquals(
        bqTableFromAlternateDatasetAttributes.getDataTableId(),
        dataTableReferenceThirdUpdate.getAttributes().getDataTableId());

    GcpBigQueryDataTableResource dataTableReferenceFourthUpdate =
        BqDataTableUtils.updateBigQueryDataTableReference(
            fullAccessApi,
            getWorkspaceId(),
            bqTableResourceId,
            /* name= */ null,
            /* description= */ null,
            /* projectId= */ null,
            /* datasetId= */ null,
            table.getAttributes().getDataTableId(),
            /*cloningInstructions*/ null);

    assertEquals(newDataTableName, dataTableReferenceFourthUpdate.getMetadata().getName());
    assertEquals(
        newDataTableDescription, dataTableReferenceFourthUpdate.getMetadata().getDescription());
    assertEquals(
        table.getAttributes().getProjectId(),
        dataTableReferenceFourthUpdate.getAttributes().getProjectId());
    assertEquals(
        table.getAttributes().getDatasetId(),
        dataTableReferenceFourthUpdate.getAttributes().getDatasetId());
    assertEquals(
        table.getAttributes().getDataTableId(),
        dataTableReferenceFourthUpdate.getAttributes().getDataTableId());
  }

  @Override
  public void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
    if (destinationWorkspaceId != null) {
      workspaceApi.deleteWorkspace(destinationWorkspaceId);
    }
  }
}
