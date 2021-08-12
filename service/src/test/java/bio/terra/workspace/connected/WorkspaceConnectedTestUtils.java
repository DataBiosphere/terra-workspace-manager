package bio.terra.workspace.connected;

import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Utilities for working with workspaces in connected tests. */
@Component
public class WorkspaceConnectedTestUtils {
  private @Autowired WorkspaceService workspaceService;
  private @Autowired JobService jobService;
  private @Autowired SpendConnectedTestUtils spendUtils;

  /** Creates a workspace with a GCP cloud context. */
  public Workspace createWorkspaceWithGcpContext(AuthenticatedUserRequest userRequest) {
    UUID workspaceId =
        workspaceService.createWorkspace(
            WorkspaceRequest.builder()
                .workspaceId(UUID.randomUUID())
                .spendProfileId(Optional.of(spendUtils.defaultSpendId()))
                .workspaceStage(WorkspaceStage.MC_WORKSPACE)
                .build(),
            userRequest);
    String gcpContextJobId = UUID.randomUUID().toString();
    workspaceService.createGcpCloudContext(
        workspaceId, gcpContextJobId, userRequest, "fakeResultPath");
    jobService.waitForJob(gcpContextJobId);
    assertNull(
        jobService.retrieveJobResult(gcpContextJobId, Object.class, userRequest).getException());
    return workspaceService.getWorkspace(workspaceId, userRequest);
  }
}
