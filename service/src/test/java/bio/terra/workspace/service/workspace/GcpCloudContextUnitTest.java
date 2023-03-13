package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.utils.MockMvcUtils.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.model.SamConstants.SamSpendProfileAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

public class GcpCloudContextUnitTest extends BaseUnitTest {
  private static final String GCP_PROJECT_ID = "terra-wsm-t-clean-berry-5152";
  private static final String POLICY_OWNER = "policy-owner";
  private static final String POLICY_WRITER = "policy-writer";
  private static final String POLICY_READER = "policy-reader";
  private static final String POLICY_APPLICATION = "policy-application";
  private static final String V1_JSON =
      String.format("{\"version\": 1, \"gcpProjectId\": \"%s\"}", GCP_PROJECT_ID);

  @Autowired private WorkspaceService workspaceService;
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private ResourceDao resourceDao;
  @Autowired private GcpCloudContextService gcpCloudContextService;

  @Test
  void serdesTest() {
    final String v2Json =
        String.format(
            "{\"version\": 2, \"gcpProjectId\": \"%s\", \"samPolicyOwner\": \"%s\", \"samPolicyWriter\": \"%s\", \"samPolicyReader\": \"%s\", \"samPolicyApplication\": \"%s\" }",
            GCP_PROJECT_ID, POLICY_OWNER, POLICY_WRITER, POLICY_READER, POLICY_APPLICATION);
    final String badV1Json =
        String.format("{\"version\": 3, \"gcpProjectId\": \"%s\"}", GCP_PROJECT_ID);
    final String badV2Json =
        String.format(
            "{\"version\": 3, \"gcpProjectId\": \"%s\", \"samPolicyOwner\": \"%s\", \"samPolicyWriter\": \"%s\", \"samPolicyReader\": \"%s\", \"samPolicyApplication\": \"%s\" }",
            GCP_PROJECT_ID, POLICY_OWNER, POLICY_WRITER, POLICY_READER, POLICY_APPLICATION);
    final String incompleteV2Json =
        String.format("{\"version\": 2, \"gcpProjectId\": \"%s\"}", GCP_PROJECT_ID);
    final String junkJson = "{\"foo\": 15, \"bar\": \"xyzzy\"}";

    // Case 1: successful V1 deserialization
    GcpCloudContext goodV1 = GcpCloudContext.deserialize(V1_JSON);
    assertEquals(goodV1.getGcpProjectId(), GCP_PROJECT_ID);
    assertTrue(goodV1.getSamPolicyOwner().isEmpty());
    assertTrue(goodV1.getSamPolicyWriter().isEmpty());
    assertTrue(goodV1.getSamPolicyReader().isEmpty());
    assertTrue(goodV1.getSamPolicyApplication().isEmpty());

    // Case 2: successful V2 deserialization
    GcpCloudContext goodV2 = GcpCloudContext.deserialize(v2Json);
    assertEquals(goodV2.getGcpProjectId(), GCP_PROJECT_ID);
    assertEquals(goodV2.getSamPolicyOwner().orElse(null), POLICY_OWNER);
    assertEquals(goodV2.getSamPolicyWriter().orElse(null), POLICY_WRITER);
    assertEquals(goodV2.getSamPolicyReader().orElse(null), POLICY_READER);
    assertEquals(goodV2.getSamPolicyApplication().orElse(null), POLICY_APPLICATION);

    // Case 3: bad V1 format
    assertThrows(
        InvalidSerializedVersionException.class,
        () -> GcpCloudContext.deserialize(badV1Json),
        "Bad V1 JSON should throw");

    // Case 4: bad V2 format
    assertThrows(
        InvalidSerializedVersionException.class,
        () -> GcpCloudContext.deserialize(badV2Json),
        "Bad V2 JSON should throw");

    // Case 5: incomplete V2
    assertThrows(
        InvalidSerializedVersionException.class,
        () -> GcpCloudContext.deserialize(incompleteV2Json),
        "Incomplete V2 JSON should throw");

    // Case 6: junk input
    assertThrows(
        InvalidSerializedVersionException.class,
        () -> GcpCloudContext.deserialize(junkJson),
        "Junk JSON should throw");
  }

  @Test
  public void autoUpgradeTest() throws Exception {
    // By default, allow all spend link calls as authorized. (All other isAuthorized calls return
    // false by Mockito default.
    Mockito.when(
            mockSamService()
                .isAuthorized(
                    Mockito.any(),
                    Mockito.any(),
                    Mockito.any(),
                    Mockito.eq(SamSpendProfileAction.LINK)))
        .thenReturn(true);

    // Fake groups
    Mockito.when(mockSamService().getWorkspacePolicy(any(), Mockito.eq(WsmIamRole.READER), any()))
        .thenReturn(POLICY_READER);
    Mockito.when(mockSamService().getWorkspacePolicy(any(), Mockito.eq(WsmIamRole.WRITER), any()))
        .thenReturn(POLICY_WRITER);
    Mockito.when(mockSamService().getWorkspacePolicy(any(), Mockito.eq(WsmIamRole.OWNER), any()))
        .thenReturn(POLICY_OWNER);
    Mockito.when(
            mockSamService().getWorkspacePolicy(any(), Mockito.eq(WsmIamRole.APPLICATION), any()))
        .thenReturn(POLICY_APPLICATION);

    // Create a workspace record
    UUID workspaceUuid = UUID.randomUUID();
    var workspace = WorkspaceFixtures.buildMcWorkspace(workspaceUuid);
    workspaceDao.createWorkspace(workspace, /* applicationIds= */ null);

    // Create a cloud context in the database with a V1 format
    final String flightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(workspaceUuid, CloudPlatform.GCP, flightId);
    workspaceDao.createCloudContextFinish(workspaceUuid, CloudPlatform.GCP, V1_JSON, flightId);

    // Run the service call that should do the upgrade
    GcpCloudContext updatedContext =
        gcpCloudContextService.getRequiredGcpCloudContext(workspaceUuid, USER_REQUEST);
    assertEquals(updatedContext.getSamPolicyOwner().orElse(null), POLICY_OWNER);
    assertEquals(updatedContext.getSamPolicyWriter().orElse(null), POLICY_WRITER);
    assertEquals(updatedContext.getSamPolicyReader().orElse(null), POLICY_READER);
    assertEquals(updatedContext.getSamPolicyApplication().orElse(null), POLICY_APPLICATION);
  }

  @Test
  public void deleteGcpContext_deletesControlledResourcesInDb() throws Exception {
    UUID workspaceUuid = UUID.randomUUID();
    var workspace =
        new Workspace(
            workspaceUuid,
            "my-user-facing-id",
            "deleteGcpContextDeletesControlledResources",
            "description",
            new SpendProfileId("spend-profile"),
            Collections.emptyMap(),
            WorkspaceStage.MC_WORKSPACE,
            DEFAULT_USER_EMAIL,
            null);
    workspaceDao.createWorkspace(workspace, /* applicationIds= */ null);
    // Create a cloud context record in the DB
    String projectId = "fake-project-id";
    GcpCloudContext fakeContext =
        new GcpCloudContext(projectId, "fakeOwner", "fakeWriter", "fakeReader", "fakeApplication");
    final String flightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(workspaceUuid, CloudPlatform.GCP, flightId);
    workspaceDao.createCloudContextFinish(
        workspaceUuid, CloudPlatform.GCP, fakeContext.serialize(), flightId);
    // Create a controlled resource in the DB
    ControlledBigQueryDatasetResource bqDataset =
        ControlledResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid)
            .projectId(projectId)
            .build();
    resourceDao.createControlledResource(bqDataset);
    // Also create a reference pointing to the same "cloud" resource
    ReferencedBigQueryDatasetResource referencedDataset =
        ReferenceResourceFixtures.makeReferencedBqDatasetResource(
            workspaceUuid, projectId, bqDataset.getDatasetName());
    resourceDao.createReferencedResource(referencedDataset);

    setupCrlMocks();

    // Delete the GCP context through the service
    workspaceService.deleteGcpCloudContext(workspace, USER_REQUEST);

    // Verify the context and resource have both been deleted from the DB
    assertTrue(workspaceDao.getCloudContext(workspaceUuid, CloudPlatform.GCP).isEmpty());
    assertThrows(
        ResourceNotFoundException.class,
        () -> resourceDao.getResource(workspaceUuid, bqDataset.getResourceId()));
    // Verify the reference still exists, even though the underlying "cloud" resource was deleted
    var referenceFromDb =
        (ReferencedBigQueryDatasetResource)
            resourceDao.getResource(workspaceUuid, referencedDataset.getResourceId());
    assertTrue(referencedDataset.partialEqual(referenceFromDb));
  }

  // Set up mocks for interacting with GCP to delete a project.
  // TODO(PF-1872): this should use shared mock code from CRL.
  private void setupCrlMocks() throws Exception {
    CloudResourceManagerCow mockResourceManager =
        Mockito.mock(CloudResourceManagerCow.class, Mockito.RETURNS_DEEP_STUBS);
    // Pretend the project is already being deleted.
    Mockito.when(mockResourceManager.projects().get(any()).execute())
        .thenReturn(new Project().setState("DELETE_IN_PROGRESS"));
    Mockito.when(mockCrlService().getCloudResourceManagerCow()).thenReturn(mockResourceManager);
  }
}
