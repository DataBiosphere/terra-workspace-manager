package bio.terra.workspace.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.app.configuration.external.AzureTestConfiguration;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class AzureWorkspaceTest extends BaseAzureConnectedTest {

  @Autowired private AzureTestConfiguration azureTestConfiguration;
  @Autowired private JobService jobService;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private WorkspaceConnectedTestUtils testUtils;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private AzureCloudContextService azureCloudContextService;
  @Autowired private AzureTestUtils azureTestUtils;
  @MockBean private SamService mockSamService;

  @Test
  void createGetDeleteAzureContext() {
    AuthenticatedUserRequest userRequest =
        new AuthenticatedUserRequest()
            .token(Optional.of("fake-token"))
            .email("fake@email.com")
            .subjectId("fakeID123");

    Workspace workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(null)
            .spendProfileId(spendUtils.defaultSpendId())
            .build();

    workspaceService.createWorkspace(workspace, null, null, userRequest);

    String jobId = UUID.randomUUID().toString();
    AzureCloudContext azureCloudContext =
        new AzureCloudContext(
            azureTestConfiguration.getTenantId(),
            azureTestConfiguration.getSubscriptionId(),
            azureTestConfiguration.getManagedResourceGroupId());
    workspaceService.createAzureCloudContext(
        workspace, jobId, userRequest, "/fake/value", azureCloudContext);
    jobService.waitForJob(jobId);

    assertNull(jobService.retrieveJobResult(jobId, Object.class).getException());
    assertTrue(
        azureCloudContextService.getAzureCloudContext(workspace.getWorkspaceId()).isPresent());
    workspaceService.deleteAzureCloudContext(workspace, userRequest);
    assertTrue(azureCloudContextService.getAzureCloudContext(workspace.getWorkspaceId()).isEmpty());
  }

  @Test
  void cloneAzureWorkspace() {
    AuthenticatedUserRequest userRequest =
        new AuthenticatedUserRequest()
            .token(Optional.of("fake-token"))
            .email("fake@email.com")
            .subjectId("fakeID123");

    SpendProfileId spendProfileId = new SpendProfileId(UUID.randomUUID().toString());

    UUID sourceUUID = UUID.randomUUID();
    Workspace sourceWorkspace =
        Workspace.builder()
            .workspaceId(sourceUUID)
            .userFacingId("a" + sourceUUID)
            .spendProfileId(spendProfileId)
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .createdByEmail(userRequest.getEmail())
            .build();

    workspaceService.createWorkspace(sourceWorkspace, null, null, userRequest);

    String jobId = UUID.randomUUID().toString();

    workspaceService.createAzureCloudContext(
        sourceWorkspace, jobId, userRequest, "/fake/value", azureTestUtils.getAzureCloudContext());
    jobService.waitForJob(jobId);

    assertTrue(
        azureCloudContextService
            .getAzureCloudContext(sourceWorkspace.getWorkspaceId())
            .isPresent());

    UUID destUUID = UUID.randomUUID();
    Workspace destWorkspace =
        Workspace.builder()
            .workspaceId(destUUID)
            .userFacingId("a" + destUUID)
            .spendProfileId(spendProfileId)
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .createdByEmail(userRequest.getEmail())
            .build();
    String cloneJobId =
        workspaceService.cloneWorkspace(
            sourceWorkspace,
            userRequest,
            null,
            destWorkspace,
            azureTestUtils.getAzureCloudContext());
    jobService.waitForJob(cloneJobId);

    assertEquals(workspaceService.getWorkspace(destUUID), destWorkspace);
    assertTrue(
        azureCloudContextService.getAzureCloudContext(destWorkspace.getWorkspaceId()).isPresent());
  }
}
