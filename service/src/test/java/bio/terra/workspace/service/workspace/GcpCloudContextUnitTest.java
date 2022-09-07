package bio.terra.workspace.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import bio.terra.workspace.common.MockBeanUnitTest;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.SamConstants.SamSpendProfileAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

public class GcpCloudContextUnitTest extends MockBeanUnitTest {
  private static final String GCP_PROJECT_ID = "terra-wsm-t-clean-berry-5152";
  private static final String POLICY_OWNER = "policy-owner";
  private static final String POLICY_WRITER = "policy-writer";
  private static final String POLICY_READER = "policy-reader";
  private static final String POLICY_APPLICATION = "policy-application";
  private static final String V1_JSON =
      String.format("{\"version\": 1, \"gcpProjectId\": \"%s\"}", GCP_PROJECT_ID);
  private static final AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest()
          .token(Optional.of("fake-token"))
          .email("fake@email.com")
          .subjectId("fakeID123");

  @Autowired private WorkspaceDao workspaceDao;
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
            getMockSamService()
                .isAuthorized(
                    Mockito.any(),
                    Mockito.any(),
                    Mockito.any(),
                    Mockito.eq(SamSpendProfileAction.LINK)))
        .thenReturn(true);

    // Fake groups
    Mockito.when(
            getMockSamService().getWorkspacePolicy(any(), Mockito.eq(WsmIamRole.READER), any()))
        .thenReturn(POLICY_READER);
    Mockito.when(
            getMockSamService().getWorkspacePolicy(any(), Mockito.eq(WsmIamRole.WRITER), any()))
        .thenReturn(POLICY_WRITER);
    Mockito.when(getMockSamService().getWorkspacePolicy(any(), Mockito.eq(WsmIamRole.OWNER), any()))
        .thenReturn(POLICY_OWNER);
    Mockito.when(
            getMockSamService()
                .getWorkspacePolicy(any(), Mockito.eq(WsmIamRole.APPLICATION), any()))
        .thenReturn(POLICY_APPLICATION);

    // Create a workspace record
    UUID workspaceUuid = UUID.randomUUID();
    var workspace =
        new Workspace(
            workspaceUuid,
            "cloud-context-user-facing-id",
            "gcpCloudContextAutoUpgradeTest",
            "cloud context description",
            new SpendProfileId("spend-profile"),
            Collections.emptyMap(),
            WorkspaceStage.MC_WORKSPACE);
    workspaceDao.createWorkspace(workspace);

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
}
