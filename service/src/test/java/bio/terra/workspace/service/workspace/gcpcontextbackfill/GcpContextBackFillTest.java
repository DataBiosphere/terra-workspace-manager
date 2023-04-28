package bio.terra.workspace.service.workspace.gcpcontextbackfill;

import static bio.terra.workspace.common.BaseConnectedTest.BUFFER_SERVICE_DISABLED_ENVS_REG_EX;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

// TODO: PF-2694 - remove this backfill when propagated to all environments
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("connected")
public class GcpContextBackFillTest extends BaseConnectedTest {
  @Autowired WorkspaceDao workspaceDao;
  @Autowired GcpCloudContextBackfill gcpCloudContextBackfill;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired WorkspaceConnectedTestUtils workspaceUtils;
  @Autowired JobService jobService;

  private UUID workspaceId1;
  private UUID workspaceId2;
  private String workspaceString1;
  private String workspaceString2;

  @BeforeAll
  public void setup() {
    workspaceId1 =
        workspaceUtils
            .createWorkspaceWithGcpContext(userAccessUtils.defaultUser().getAuthenticatedRequest())
            .getWorkspaceId();
    workspaceString1 = workspaceId1.toString();
    workspaceId2 =
        workspaceUtils
            .createWorkspaceWithGcpContext(userAccessUtils.defaultUser().getAuthenticatedRequest())
            .getWorkspaceId();
    workspaceString2 = workspaceId2.toString();
  }

  @AfterAll
  public void teardown() {
    if (workspaceId1 != null) {
      workspaceUtils.deleteWorkspaceAndCloudContext(
          userAccessUtils.defaultUser().getAuthenticatedRequest(), workspaceId1);
    }
    if (workspaceId2 != null) {
      workspaceUtils.deleteWorkspaceAndCloudContext(
          userAccessUtils.defaultUser().getAuthenticatedRequest(), workspaceId2);
    }
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  public void backfillTest() {
    // Test query - neither workspace should need updating
    List<String> backfillList = workspaceDao.getGcpContextBackfillWorkspaceList();
    assertFalse(backfillList.contains(workspaceString1));
    assertFalse(backfillList.contains(workspaceString2));

    // Rewrite one context to empty it
    clearContext(workspaceId1);

    // Test query - one workspace should need updating
    backfillList = workspaceDao.getGcpContextBackfillWorkspaceList();
    assertTrue(backfillList.contains(workspaceString1));
    assertFalse(backfillList.contains(workspaceString2));

    // Rewrite the other context to empty it
    clearContext(workspaceId2);

    // Test query - both workspace should need updating
    backfillList = workspaceDao.getGcpContextBackfillWorkspaceList();
    assertTrue(backfillList.contains(workspaceString1));
    assertTrue(backfillList.contains(workspaceString2));

    // Run the backfill flight
    String jobId = gcpCloudContextBackfill.gcpCloudContextBackfillAsync();
    jobService.waitForJob(jobId);

    // Run the backfill flight again - should return null - nothing to do
    jobId = gcpCloudContextBackfill.gcpCloudContextBackfillAsync();
    assertNull(jobId);

    // Test query - neither workspace should need updating
    backfillList = workspaceDao.getGcpContextBackfillWorkspaceList();
    assertTrue(backfillList.isEmpty());
  }

  private void clearContext(UUID workspaceId) {
    GcpCloudContext context =
        workspaceDao
            .getCloudContext(workspaceId, CloudPlatform.GCP)
            .map(GcpCloudContext::deserialize)
            .orElseThrow();
    context.setSamPolicyApplication(null);
    context.setSamPolicyOwner(null);
    context.setSamPolicyReader(null);
    context.setSamPolicyWriter(null);
    workspaceDao.updateCloudContext(workspaceId, CloudPlatform.GCP, context.serialize());
  }
}
