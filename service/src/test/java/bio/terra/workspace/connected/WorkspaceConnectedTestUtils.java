package bio.terra.workspace.connected;

import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Utilities for working with workspaces in connected tests. */
@Component
public class WorkspaceConnectedTestUtils {
  private @Autowired WorkspaceService workspaceService;
  private @Autowired JobService jobService;
  private @Autowired SpendConnectedTestUtils spendUtils;

  /**
   * Creates a workspace with a GCP cloud context.
   *
   * <p>Note: To delete workspace and cloud context, call workspaceService.deleteWorkspace(). This
   * automatically deletes cloud context.
   */
  public Workspace createWorkspaceWithGcpContext(AuthenticatedUserRequest userRequest) {
    Workspace workspace = createWorkspace(userRequest);
    UUID workspaceUuid = workspace.getWorkspaceId();
    String gcpContextJobId = UUID.randomUUID().toString();
    workspaceService.createGcpCloudContext(
        workspace, gcpContextJobId, userRequest, "fakeResultPath");
    jobService.waitForJob(gcpContextJobId);
    assertNull(jobService.retrieveJobResult(gcpContextJobId, Object.class).getException());
    return workspaceService.getWorkspace(workspaceUuid);
  }

  public Workspace createWorkspace(AuthenticatedUserRequest userRequest) {
    UUID workspaceUuid = UUID.randomUUID();
    Workspace workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(workspaceUuid)
            .spendProfileId(spendUtils.defaultSpendId())
            .build();
    workspaceService.createWorkspace(workspace, null, null, userRequest);

    return workspaceService.getWorkspace(workspaceUuid);
  }

  public void deleteWorkspaceAndCloudContext(
      AuthenticatedUserRequest userRequest, UUID workspaceId) {
    workspaceService.deleteWorkspace(workspaceService.getWorkspace(workspaceId), userRequest);
  }
}
