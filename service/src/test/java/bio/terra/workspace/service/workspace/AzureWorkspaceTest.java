package bio.terra.workspace.service.workspace;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.app.configuration.external.AzureTestConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class AzureWorkspaceTest extends BaseAzureTest {

  @Autowired private AzureTestConfiguration azureTestConfiguration;
  @Autowired private JobService jobService;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private WorkspaceConnectedTestUtils testUtils;
  @Autowired private WorkspaceService workspaceService;
  @MockBean private SamService mockSamService;

  @Test
  void createGetDeleteAzureContext() {
    AuthenticatedUserRequest userRequest =
        new AuthenticatedUserRequest()
            .token(Optional.of("fake-token"))
            .email("fake@email.com")
            .subjectId("fakeID123");

    Workspace request =
        Workspace.builder()
            .workspaceUuid(UUID.randomUUID())
            .spendProfileId(spendUtils.defaultSpendId())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();

    workspaceService.createWorkspace(request, userRequest);

    String jobId = UUID.randomUUID().toString();
    AzureCloudContext azureCloudContext =
        new AzureCloudContext(
            azureTestConfiguration.getTenantId(),
            azureTestConfiguration.getSubscriptionId(),
            azureTestConfiguration.getManagedResourceGroupId());
    workspaceService.createAzureCloudContext(
        request.getWorkspaceId(), jobId, userRequest, "/fake/value", azureCloudContext);
    jobService.waitForJob(jobId);

    assertNull(jobService.retrieveJobResult(jobId, Object.class, userRequest).getException());
    assertTrue(
        testUtils
            .getAuthorizedAzureCloudContext(request.getWorkspaceId(), userRequest)
            .isPresent());
    workspaceService.deleteAzureCloudContext(request.getWorkspaceId(), userRequest);
    assertTrue(
        testUtils.getAuthorizedAzureCloudContext(request.getWorkspaceId(), userRequest).isEmpty());
  }
}
