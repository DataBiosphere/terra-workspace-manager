package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.*;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.GcsBucketUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class MergeGroupPolicies extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(MergeGroupPolicies.class);

  private static final String DATASET_RESOURCE_NAME = "wsmtest_mergegroups";

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ControlledGcpResourceApi resourceApi =
        ClientTestUtils.getControlledGcpResourceClient(testUser, server);
    ReferencedGcpResourceApi referencedGcpResourceApi =
        ClientTestUtils.getReferencedGcpResourceClient(testUser, server);

    String groupNameA = "wsm-test-group";
    String groupNameB = "wsm-test-group-alt";

    // Add a cloud context and bucket resource to the test workspace. We'll use this resource to
    // clone references in the scenarios in this journey.
    String projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
    logger.info("Created project {}", projectId);

    // Create a shared Bucket
    CreatedControlledGcpGcsBucket controlledBucket =
        GcsBucketUtils.makeControlledGcsBucketUserShared(
            resourceApi,
            getWorkspaceId(),
            DATASET_RESOURCE_NAME,
            CloningInstructionsEnum.REFERENCE);
    UUID resourceId = controlledBucket.getResourceId();
    GcpGcsBucketResource controlledBucketResource = controlledBucket.getGcpBucket();
    logger.info("Created controlled bucket {}", resourceId);

    var groupPolicyA =
        new WsmPolicyInputs()
            .addInputsItem(
                new WsmPolicyInput()
                    .name("group-constraint")
                    .namespace("terra")
                    .addAdditionalDataItem(new WsmPolicyPair().key("group").value(groupNameA)));
    var groupPolicyB =
        new WsmPolicyInputs()
            .addInputsItem(
                new WsmPolicyInput()
                    .name("group-constraint")
                    .namespace("terra")
                    .addAdditionalDataItem(new WsmPolicyPair().key("group").value(groupNameB)));

    // Scenario 1: WS(groupA) can merge DC(nogroup).
    CreatedWorkspace groupTestWorkspace =
        createWorkspaceWithPolicy(
            UUID.randomUUID(), getSpendProfileId(), workspaceApi, groupPolicyA);

    // data collection has no policies
    CreatedWorkspace noGroupDataCollection =
        createWorkspace(UUID.randomUUID(), getSpendProfileId(), workspaceApi);

    GcpGcsBucketResource groupTestReferenceResource =
        GcsBucketUtils.makeGcsBucketReference(
            controlledBucketResource.getAttributes(),
            referencedGcpResourceApi,
            noGroupDataCollection.getId(),
            "referenceBucket",
            CloningInstructionsEnum.REFERENCE);

    CloneReferencedGcpGcsBucketResourceResult referenceResult =
        referencedGcpResourceApi.cloneGcpGcsBucketReference(
            new CloneReferencedResourceRequestBody()
                .destinationWorkspaceId(groupTestWorkspace.getId()),
            groupTestReferenceResource.getMetadata().getWorkspaceId(),
            groupTestReferenceResource.getMetadata().getResourceId());

    validateWorkspaceContainsGroupPolicy(workspaceApi, groupTestWorkspace.getId(), groupNameA);
    workspaceApi.deleteWorkspace(noGroupDataCollection.getId());

    // Scenario 2: WS(groupA) can merge DC(groupA).
    CreatedWorkspace groupADataCollection =
        createWorkspaceWithPolicy(
            UUID.randomUUID(), getSpendProfileId(), workspaceApi, groupPolicyA);

    GcpGcsBucketResource groupATestReferenceResource =
        GcsBucketUtils.makeGcsBucketReference(
            controlledBucketResource.getAttributes(),
            referencedGcpResourceApi,
            groupADataCollection.getId(),
            "referenceBucket",
            CloningInstructionsEnum.REFERENCE);

    referencedGcpResourceApi.deleteBucketReference(
        referenceResult.getResource().getMetadata().getWorkspaceId(),
        referenceResult.getResource().getMetadata().getResourceId());

    referenceResult =
        referencedGcpResourceApi.cloneGcpGcsBucketReference(
            new CloneReferencedResourceRequestBody()
                .destinationWorkspaceId(groupTestWorkspace.getId()),
            groupATestReferenceResource.getMetadata().getWorkspaceId(),
            groupATestReferenceResource.getMetadata().getResourceId());

    validateWorkspaceContainsGroupPolicy(workspaceApi, groupTestWorkspace.getId(), groupNameA);
    workspaceApi.deleteWorkspace(groupADataCollection.getId());

    // Scenario 3: WS(groupA) cannot merge DC(groupB).
    CreatedWorkspace groupBDataCollection =
        createWorkspaceWithPolicy(
            UUID.randomUUID(), getSpendProfileId(), workspaceApi, groupPolicyB);
    GcpGcsBucketResource groupBTestReferenceResource =
        GcsBucketUtils.makeGcsBucketReference(
            controlledBucketResource.getAttributes(),
            referencedGcpResourceApi,
            groupBDataCollection.getId(),
            "referenceBucket",
            CloningInstructionsEnum.REFERENCE);

    // remove the old reference
    referencedGcpResourceApi.deleteBucketReference(
        referenceResult.getResource().getMetadata().getWorkspaceId(),
        referenceResult.getResource().getMetadata().getResourceId());

    ApiException exception =
        assertThrows(
            ApiException.class,
            () ->
                referencedGcpResourceApi.cloneGcpGcsBucketReference(
                    new CloneReferencedResourceRequestBody()
                        .destinationWorkspaceId(groupTestWorkspace.getId()),
                    groupBTestReferenceResource.getMetadata().getWorkspaceId(),
                    groupBTestReferenceResource.getMetadata().getResourceId()));
    assertEquals(HttpStatus.SC_CONFLICT, exception.getCode());
    assertTrue(exception.getMessage().contains("Cannot update group policies."));

    // group should still be A only
    validateWorkspaceContainsGroupPolicy(workspaceApi, groupTestWorkspace.getId(), groupNameA);

    // Scenario 4: WS(nopolicy) cannot merge DC(groupB).
    CreatedWorkspace noGroupPolicyWorkspace =
        createWorkspace(UUID.randomUUID(), getSpendProfileId(), workspaceApi);

    exception =
        assertThrows(
            ApiException.class,
            () ->
                referencedGcpResourceApi.cloneGcpGcsBucketReference(
                    new CloneReferencedResourceRequestBody()
                        .destinationWorkspaceId(noGroupPolicyWorkspace.getId()),
                    groupBTestReferenceResource.getMetadata().getWorkspaceId(),
                    groupBTestReferenceResource.getMetadata().getResourceId()));
    assertEquals(HttpStatus.SC_CONFLICT, exception.getCode());
    assertTrue(exception.getMessage().contains("Cannot update group policies."));

    WorkspaceDescription updatedWorkspace =
        workspaceApi.getWorkspace(noGroupPolicyWorkspace.getId(), null);
    List<WsmPolicyInput> updatedPolicies = updatedWorkspace.getPolicies();

    assertEquals(0, updatedPolicies.size());
    workspaceApi.deleteWorkspace(noGroupPolicyWorkspace.getId());

    // Scenario 5: Clone a workspace and add additional groups. WS(groupA), Clone(+groupB) =
    // WS(groupA, groupB)
    CreatedWorkspace workspaceToClone =
        createWorkspaceWithPolicy(
            UUID.randomUUID(), getSpendProfileId(), workspaceApi, groupPolicyA);

    UUID cloneWorkspaceId = UUID.randomUUID();
    CloneWorkspaceRequest request =
        new CloneWorkspaceRequest()
            .destinationWorkspaceId(cloneWorkspaceId)
            .additionalPolicies(groupPolicyB)
            .spendProfile(getSpendProfileId());

    workspaceApi.cloneWorkspace(request, workspaceToClone.getId());
    updatedWorkspace = workspaceApi.getWorkspace(cloneWorkspaceId, null);
    updatedPolicies = updatedWorkspace.getPolicies();

    assertEquals(1, updatedPolicies.size());
    var additionalData = updatedPolicies.get(0).getAdditionalData();
    assertEquals(2, additionalData.size());
    assertTrue(
        additionalData.stream()
            .map(data -> data.getValue())
            .toList()
            .containsAll(Arrays.asList(groupNameA, groupNameB)));

    // Clean up the data collections used in most of the scenarios.
    workspaceApi.deleteWorkspace(groupBDataCollection.getId());
    workspaceApi.deleteWorkspace(groupTestWorkspace.getId());
  }

  private void validateWorkspaceContainsGroupPolicy(
      WorkspaceApi workspaceApi, UUID workspaceId, String groupName) throws Exception {
    WorkspaceDescription updatedWorkspace = workspaceApi.getWorkspace(workspaceId, null);
    List<WsmPolicyInput> updatedPolicies = updatedWorkspace.getPolicies();

    List<WsmPolicyInput> groupPolicies =
        updatedPolicies.stream().filter(p -> p.getName().equals("group-constraint")).toList();
    assertEquals(1, groupPolicies.size());
    WsmPolicyPair groupPolicy = groupPolicies.get(0).getAdditionalData().get(0);
    assertEquals(1, groupPolicies.get(0).getAdditionalData().size());
    assertEquals("group", groupPolicy.getKey());
    assertEquals(groupName, groupPolicy.getValue());
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
