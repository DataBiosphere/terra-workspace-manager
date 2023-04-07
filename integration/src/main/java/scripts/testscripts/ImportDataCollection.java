package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;
import static scripts.utils.BqDatasetUtils.makeControlledBigQueryDatasetUserShared;
import static scripts.utils.TestUtils.appendRandomNumber;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.GcsBucketUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class ImportDataCollection extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(ImportDataCollection.class);

  private static final String DATASET_RESOURCE_NAME = "wsmtest_dataset";

  // TODO [PF-2407] Once we can patch workspace policies we won't have to create so many workspaces
  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ControlledGcpResourceApi resourceApi =
        ClientTestUtils.getControlledGcpResourceClient(testUser, server);
    ReferencedGcpResourceApi referencedGcpResourceApi =
        ClientTestUtils.getReferencedGcpResourceClient(testUser, server);
    ControlledGcpResourceApi controlledGcpResourceApi =
        ClientTestUtils.getControlledGcpResourceClient(testUser, server);

    String gcpCentralLocation = "gcp.us-central1";
    String gcpEastLocation = "gcp.us-east1";
    String usaLocation = "usa";
    String groupNameA = "wsm-test-group";
    String groupNameB = "wsm-test-group-alt";

    /*
     Create a workspace with us-central1 policy and a reference bucket. This will act as our data
     collection for most of the scenarios covered in this journey.
    */
    CreatedWorkspace centralDataCollection =
        createWorkspaceWithRegionPolicy(workspaceApi, gcpCentralLocation);

    // Create a cloud context
    String projectId =
        CloudContextMaker.createGcpCloudContext(centralDataCollection.getId(), workspaceApi);
    logger.info("Created project {}", projectId);

    // Create a shared Bucket
    CreatedControlledGcpGcsBucket controlledBucket =
        GcsBucketUtils.makeControlledGcsBucketUserShared(
            resourceApi,
            centralDataCollection.getId(),
            DATASET_RESOURCE_NAME,
            CloningInstructionsEnum.REFERENCE);
    UUID resourceId = controlledBucket.getResourceId();
    GcpGcsBucketResource controlledBucketResource = controlledBucket.getGcpBucket();
    logger.info("Created controlled bucket {}", resourceId);

    // Create a shared BigQuery Dataset
    GcpBigQueryDatasetResource controlledBqDataset =
        makeControlledBigQueryDatasetUserShared(
            controlledGcpResourceApi,
            centralDataCollection.getId(),
            appendRandomNumber("import_data_collection_bq_dataset"),
            null,
            CloningInstructionsEnum.DEFINITION);

    // make a reference to the shared Bucket
    GcpGcsBucketResource dataCollectionReferenceResource =
        GcsBucketUtils.makeGcsBucketReference(
            controlledBucketResource.getAttributes(),
            referencedGcpResourceApi,
            centralDataCollection.getId(),
            "referenceBucket",
            CloningInstructionsEnum.REFERENCE);

    /*
     Scenario 1: Workspace without region should gain region from data collection. Workspace
     (policy=empty) + Data Collection (policy=us-central1). Result: OK & Workspace
     (policy=us-central1)
    */
    // We'll use the workspace allocated by the Base class as the target workspace.

    referencedGcpResourceApi.cloneGcpGcsBucketReference(
        new CloneReferencedResourceRequestBody().destinationWorkspaceId(getWorkspaceId()),
        dataCollectionReferenceResource.getMetadata().getWorkspaceId(),
        dataCollectionReferenceResource.getMetadata().getResourceId());

    validateWorkspaceContainsRegionPolicy(workspaceApi, getWorkspaceId(), gcpCentralLocation);

    /*
     Scenario 2: Workspace region should be reduced to data collection region. Workspace
     (policy=usa) + Data Collection (policy=us-central1). Result: OK & Workspace
     (policy=us-central1)
    */
    CreatedWorkspace scenario2Workspace =
        createWorkspaceWithRegionPolicy(workspaceApi, usaLocation);

    referencedGcpResourceApi.cloneGcpGcsBucketReference(
        new CloneReferencedResourceRequestBody().destinationWorkspaceId(scenario2Workspace.getId()),
        dataCollectionReferenceResource.getMetadata().getWorkspaceId(),
        dataCollectionReferenceResource.getMetadata().getResourceId());

    validateWorkspaceContainsRegionPolicy(
        workspaceApi, scenario2Workspace.getId(), gcpCentralLocation);
    workspaceApi.deleteWorkspace(scenario2Workspace.getId());

    /*
     Scenario 3: Workspace region isn't updated if data collection doesn't specify a region.
     Workspace (policy=east) + Data Collection (policy=empty). Result: OK & Workspace
     (policy=east)
    */
    CreatedWorkspace eastWorkspace = createWorkspaceWithRegionPolicy(workspaceApi, gcpEastLocation);

    CreatedWorkspace noPolicyDataCollection =
        createWorkspace(UUID.randomUUID(), getSpendProfileId(), workspaceApi);

    GcpGcsBucketResource noPolicyReferenceResource =
        GcsBucketUtils.makeGcsBucketReference(
            controlledBucketResource.getAttributes(),
            referencedGcpResourceApi,
            noPolicyDataCollection.getId(),
            "referenceBucket",
            CloningInstructionsEnum.REFERENCE);

    referencedGcpResourceApi.cloneGcpGcsBucketReference(
        new CloneReferencedResourceRequestBody().destinationWorkspaceId(eastWorkspace.getId()),
        noPolicyReferenceResource.getMetadata().getWorkspaceId(),
        noPolicyReferenceResource.getMetadata().getResourceId());

    validateWorkspaceContainsRegionPolicy(workspaceApi, eastWorkspace.getId(), gcpEastLocation);
    // don't delete this workspace because we'll reuse it in the next scenario.

    /*
     Scenario 4: Workspace and data collection have incompatible region policies. Workspace
     (policy=east) + Data Collection (policy=central). Result: Policy Exception
    */
    ApiException exception =
        assertThrows(
            ApiException.class,
            () ->
                referencedGcpResourceApi.cloneGcpGcsBucketReference(
                    new CloneReferencedResourceRequestBody()
                        .destinationWorkspaceId(eastWorkspace.getId()),
                    dataCollectionReferenceResource.getMetadata().getWorkspaceId(),
                    dataCollectionReferenceResource.getMetadata().getResourceId()));
    workspaceApi.deleteWorkspace(eastWorkspace.getId());
    assertEquals(exception.getCode(), HttpStatus.SC_CONFLICT);
    assertTrue(exception.getMessage().contains("Policy merge has conflicts"));

    /*
     Scenario 5: Workspace has compatible policy but an incompatible resource. Workspace
     (policy=usa,resource=east) + Data Collection (policy=central). Result: Policy Exception
    */
    CreatedWorkspace scenario5Workspace =
        createWorkspaceWithRegionPolicy(workspaceApi, usaLocation);

    List<Property> properties = new ArrayList<>();
    properties.add(new Property().key("terra-default-location").value("us-east1"));
    workspaceApi.updateWorkspaceProperties(properties, scenario5Workspace.getId());

    // Create a cloud context
    projectId = CloudContextMaker.createGcpCloudContext(scenario5Workspace.getId(), workspaceApi);
    logger.info("Created project {}", projectId);

    // Create a shared Bucket
    controlledBucket =
        GcsBucketUtils.makeControlledGcsBucketUserShared(
            resourceApi,
            scenario5Workspace.getId(),
            DATASET_RESOURCE_NAME,
            CloningInstructionsEnum.REFERENCE);
    resourceId = controlledBucket.getResourceId();

    logger.info("Created controlled bucket {}", resourceId);

    exception =
        assertThrows(
            ApiException.class,
            () ->
                referencedGcpResourceApi.cloneGcpGcsBucketReference(
                    new CloneReferencedResourceRequestBody()
                        .destinationWorkspaceId(scenario5Workspace.getId()),
                    dataCollectionReferenceResource.getMetadata().getWorkspaceId(),
                    dataCollectionReferenceResource.getMetadata().getResourceId()));
    logger.info(
        "Captured exception - code: {} message: '%{}'",
        exception.getCode(), exception.getMessage());
    assertEquals(exception.getCode(), HttpStatus.SC_CONFLICT);
    assertTrue(exception.getMessage().contains("violation of policy"));

    workspaceApi.deleteWorkspace(scenario5Workspace.getId());

    /*
     Scenario 6: Same as Scenario1 but cloning a controlled resource to a reference. Workspace without region should
     gain region from data collection. Workspace (policy=empty) + Data Collection (policy=us-central1).
     Result: OK & Workspace (policy=us-central1)
    */
    CreatedWorkspace noPolicyWorkspace =
        createWorkspace(UUID.randomUUID(), getSpendProfileId(), workspaceApi);

    final CloneControlledGcpGcsBucketRequest cloneRequest =
        new CloneControlledGcpGcsBucketRequest()
            .destinationWorkspaceId(noPolicyWorkspace.getId())
            .cloningInstructions(CloningInstructionsEnum.REFERENCE)
            .jobControl(new JobControl().id(UUID.randomUUID().toString()));

    CloneControlledGcpGcsBucketResult cloneResult =
        controlledGcpResourceApi.cloneGcsBucket(
            cloneRequest,
            controlledBucketResource.getMetadata().getWorkspaceId(),
            controlledBucketResource.getMetadata().getResourceId());

    cloneResult =
        ClientTestUtils.pollWhileRunning(
            cloneResult,
            () ->
                resourceApi.getCloneGcsBucketResult(
                    controlledBucketResource.getMetadata().getWorkspaceId(),
                    cloneRequest.getJobControl().getId()),
            CloneControlledGcpGcsBucketResult::getJobReport,
            Duration.ofSeconds(5));

    ClientTestUtils.assertJobSuccess(
        "clone bucket", cloneResult.getJobReport(), cloneResult.getErrorReport());

    validateWorkspaceContainsRegionPolicy(
        workspaceApi, noPolicyWorkspace.getId(), gcpCentralLocation);
    workspaceApi.deleteWorkspace(noPolicyWorkspace.getId());

    /*
     Scenario 7: Same as Scenario6 but cloning a controlled resource to a resource. Workspace without region should
     gain region from data collection. Workspace (policy=empty) + Data Collection (policy=us-central1).
     Result: OK & Workspace (policy=us-central1)
    */
    CreatedWorkspace scenario7Workspace =
        createWorkspace(UUID.randomUUID(), getSpendProfileId(), workspaceApi);

    // Need to have a cloud context in order to clone a resource.
    projectId = CloudContextMaker.createGcpCloudContext(scenario7Workspace.getId(), workspaceApi);
    logger.info("Created project {}", projectId);

    final CloneControlledGcpGcsBucketRequest cloneResourceRequest =
        new CloneControlledGcpGcsBucketRequest()
            .destinationWorkspaceId(scenario7Workspace.getId())
            .cloningInstructions(CloningInstructionsEnum.RESOURCE)
            .jobControl(new JobControl().id(UUID.randomUUID().toString()));

    cloneResult =
        controlledGcpResourceApi.cloneGcsBucket(
            cloneResourceRequest,
            controlledBucketResource.getMetadata().getWorkspaceId(),
            controlledBucketResource.getMetadata().getResourceId());

    cloneResult =
        ClientTestUtils.pollWhileRunning(
            cloneResult,
            () ->
                resourceApi.getCloneGcsBucketResult(
                    controlledBucketResource.getMetadata().getWorkspaceId(),
                    cloneResourceRequest.getJobControl().getId()),
            CloneControlledGcpGcsBucketResult::getJobReport,
            Duration.ofSeconds(5));

    ClientTestUtils.assertJobSuccess(
        "clone bucket", cloneResult.getJobReport(), cloneResult.getErrorReport());

    validateWorkspaceContainsRegionPolicy(
        workspaceApi, scenario7Workspace.getId(), gcpCentralLocation);

    // try to clone the resource to a different location - one outside of policy.
    final CloneControlledGcpGcsBucketRequest cloneToAltLocationRequest =
        new CloneControlledGcpGcsBucketRequest()
            .destinationWorkspaceId(scenario7Workspace.getId())
            .cloningInstructions(CloningInstructionsEnum.RESOURCE)
            .location("us-east1")
            .jobControl(new JobControl().id(UUID.randomUUID().toString()));

    CloneControlledGcpGcsBucketResult cloneToAltLocationResult =
        controlledGcpResourceApi.cloneGcsBucket(
            cloneToAltLocationRequest,
            controlledBucketResource.getMetadata().getWorkspaceId(),
            controlledBucketResource.getMetadata().getResourceId());

    cloneToAltLocationResult =
        ClientTestUtils.pollWhileRunning(
            cloneToAltLocationResult,
            () ->
                resourceApi.getCloneGcsBucketResult(
                    controlledBucketResource.getMetadata().getWorkspaceId(),
                    cloneToAltLocationRequest.getJobControl().getId()),
            CloneControlledGcpGcsBucketResult::getJobReport,
            Duration.ofSeconds(5));

    assertEquals(JobReport.StatusEnum.FAILED, cloneToAltLocationResult.getJobReport().getStatus());
    assertEquals(HttpStatus.SC_CONFLICT, cloneToAltLocationResult.getJobReport().getStatusCode());
    assertTrue(
        cloneToAltLocationResult
            .getErrorReport()
            .getMessage()
            .contains("violates region policies"));

    workspaceApi.deleteWorkspace(scenario7Workspace.getId());

    /*
     Scenario 8: Cloning BigQuery Datasets. Workspace without region should
     gain region from data collection. Workspace (policy=empty) + Data Collection (policy=us-central1).
     Result: OK & Workspace (policy=us-central1)
    */
    CreatedWorkspace scenario8Workspace =
        createWorkspace(UUID.randomUUID(), getSpendProfileId(), workspaceApi);

    // Need to have a cloud context in order to clone a resource.
    projectId = CloudContextMaker.createGcpCloudContext(scenario8Workspace.getId(), workspaceApi);
    logger.info("Created project {}", projectId);

    var cloneBqRequest =
        new CloneControlledGcpBigQueryDatasetRequest()
            .name("importDataCollection8")
            .jobControl(new JobControl().id(UUID.randomUUID().toString()))
            .destinationDatasetName("cloned_dataset8")
            .cloningInstructions(CloningInstructionsEnum.DEFINITION)
            .destinationWorkspaceId(scenario8Workspace.getId());
    CloneControlledGcpBigQueryDatasetResult cloneBqResult =
        controlledGcpResourceApi.cloneBigQueryDataset(
            cloneBqRequest,
            controlledBqDataset.getMetadata().getWorkspaceId(),
            controlledBqDataset.getMetadata().getResourceId());

    CloneControlledGcpBigQueryDatasetRequest finalCloneBqRequest1 = cloneBqRequest;
    cloneBqResult =
        ClientTestUtils.pollWhileRunning(
            cloneBqResult,
            () ->
                resourceApi.getCloneBigQueryDatasetResult(
                    controlledBqDataset.getMetadata().getWorkspaceId(),
                    finalCloneBqRequest1.getJobControl().getId()),
            CloneControlledGcpBigQueryDatasetResult::getJobReport,
            Duration.ofSeconds(5));

    ClientTestUtils.assertJobSuccess(
        "clone controlled BigQuery dataset 8",
        cloneBqResult.getJobReport(),
        cloneBqResult.getErrorReport());

    validateWorkspaceContainsRegionPolicy(
        workspaceApi, scenario8Workspace.getId(), gcpCentralLocation);

    // try to clone the resource to a different location - one outside of policy.
    cloneBqRequest =
        new CloneControlledGcpBigQueryDatasetRequest()
            .name("importDataCollection8b")
            .jobControl(new JobControl().id(UUID.randomUUID().toString()))
            .destinationDatasetName("cloned_dataset8b")
            .location("us-east1")
            .cloningInstructions(CloningInstructionsEnum.DEFINITION)
            .destinationWorkspaceId(scenario8Workspace.getId());
    cloneBqResult =
        controlledGcpResourceApi.cloneBigQueryDataset(
            cloneBqRequest,
            controlledBqDataset.getMetadata().getWorkspaceId(),
            controlledBqDataset.getMetadata().getResourceId());

    CloneControlledGcpBigQueryDatasetRequest finalCloneBqRequest = cloneBqRequest;
    cloneBqResult =
        ClientTestUtils.pollWhileRunning(
            cloneBqResult,
            () ->
                resourceApi.getCloneBigQueryDatasetResult(
                    controlledBqDataset.getMetadata().getWorkspaceId(),
                    finalCloneBqRequest.getJobControl().getId()),
            CloneControlledGcpBigQueryDatasetResult::getJobReport,
            Duration.ofSeconds(5));

    assertEquals(JobReport.StatusEnum.FAILED, cloneBqResult.getJobReport().getStatus());
    assertEquals(HttpStatus.SC_CONFLICT, cloneBqResult.getJobReport().getStatusCode());
    assertTrue(cloneBqResult.getErrorReport().getMessage().contains("violates region policies"));

    workspaceApi.deleteWorkspace(scenario8Workspace.getId());

    // Clean up the data collections used in most of the scenarios.
    workspaceApi.deleteWorkspace(centralDataCollection.getId());
  }

  private WsmPolicyInputs getRegionPolicyInputs(String location) {
    return new WsmPolicyInputs()
        .addInputsItem(
            new WsmPolicyInput()
                .name("region-constraint")
                .namespace("terra")
                .additionalData(
                    new ArrayList<>() {
                      {
                        add(new WsmPolicyPair().key("region-name").value(location));
                      }
                    }));
  }

  private CreatedWorkspace createWorkspaceWithRegionPolicy(
      WorkspaceApi workspaceApi, String location) throws Exception {
    UUID workspaceId = UUID.randomUUID();
    WsmPolicyInputs policies = getRegionPolicyInputs(location);

    Properties properties = new Properties();
    properties.add(
        new Property().key("terra-default-location").value(location.replace("gcp.", "")));

    final var requestBody =
        new CreateWorkspaceRequestBody()
            .id(workspaceId)
            .spendProfile(getSpendProfileId())
            .stage(getStageModel())
            .policies(policies)
            .properties(properties);
    final CreatedWorkspace workspace = workspaceApi.createWorkspace(requestBody);
    assertThat(workspace.getId(), equalTo(workspaceId));
    return workspace;
  }

  private void validateWorkspaceContainsRegionPolicy(
      WorkspaceApi workspaceApi, UUID workspaceId, String region) throws Exception {
    WorkspaceDescription updatedWorkspace = workspaceApi.getWorkspace(workspaceId, null);
    List<WsmPolicyInput> updatedPolicies = updatedWorkspace.getPolicies();

    assertEquals(1, updatedPolicies.size());
    assertEquals("region-constraint", updatedPolicies.get(0).getName());
    WsmPolicyPair regionPolicy = updatedPolicies.get(0).getAdditionalData().get(0);
    assertEquals("region-name", regionPolicy.getKey());
    assertEquals(region, regionPolicy.getValue());
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
