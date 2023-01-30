package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static scripts.utils.BqDatasetUtils.assertDatasetsAreEqualIgnoringLastUpdatedDate;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedAzureStorageContainerSasToken;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.Properties;
import bio.terra.workspace.model.Property;
import bio.terra.workspace.model.WsmPolicyInput;
import bio.terra.workspace.model.WsmPolicyInputs;
import bio.terra.workspace.model.WsmPolicyPair;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.BqDatasetUtils;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.GcsBucketUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class ImportDataCollection extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger =
    LoggerFactory.getLogger(ImportDataCollection.class);

  private static final String DATASET_RESOURCE_NAME = "wsmtest_dataset";

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
    throws Exception {
    ControlledGcpResourceApi resourceApi =
      ClientTestUtils.getControlledGcpResourceClient(testUser, server);

    // Create a cloud context
    String projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
    logger.info("Created project {}", projectId);

    // Create a shared Bucket
    CreatedControlledGcpGcsBucket controlledBucket =
      GcsBucketUtils.makeControlledGcsBucketUserShared(
        resourceApi, getWorkspaceId(), DATASET_RESOURCE_NAME, CloningInstructionsEnum.REFERENCE);
    UUID resourceId = controlledBucket.getResourceId();
    logger.info("Created controlled bucket {}", resourceId);

    ReferencedGcpResourceApi referenceApi = ClientTestUtils.getReferencedGcpResourceClient(testUser, server);


    CreatedWorkspace usCentralWorkspace = CreateWorkspaceWithRegionPolicy(workspaceApi, "gcp.us-central1");




    workspaceApi.deleteWorkspace(usCentralWorkspace.getId());

    assertNotEquals(usCentralWorkspace.getId(), getWorkspaceId());
  }

  private CreatedWorkspace CreateWorkspaceWithRegionPolicy(WorkspaceApi workspaceApi, String location) throws Exception {
    UUID workspaceId = UUID.randomUUID();

    WsmPolicyInputs policies = new WsmPolicyInputs()
      .addInputsItem(
        new WsmPolicyInput()
          .name("region-constraint")
          .namespace("terra")
          .additionalData(
            new ArrayList<WsmPolicyPair>() {{ add(new WsmPolicyPair().key("region-name").value(location)); }}
          ));

    Properties properties = new Properties();
    Property property1 = new Property().key("foo").value("bar");
    Property property2 = new Property().key("xyzzy").value("plohg");
    properties.add(property1);
    properties.add(property2);

    final var requestBody =
      new CreateWorkspaceRequestBody()
        .id(workspaceId)
        .stage(getStageModel())
        .policies(policies)
        .properties(properties);
    final CreatedWorkspace workspace = workspaceApi.createWorkspace(requestBody);
    assertThat(workspace.getId(), equalTo(workspaceId));
    return workspace;
  }

  @Override
  public void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
    throws Exception {
    try {
      workspaceApi.deleteWorkspace(getWorkspaceId());
    } catch (ApiException e) {
      if (e.getCode() != HttpStatus.SC_NOT_FOUND) {
        throw e;
      }
    }
  }
}
