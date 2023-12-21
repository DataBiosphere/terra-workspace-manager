package bio.terra.workspace.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.db.WorkspaceActivityLogDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.admin.flights.cloudcontexts.gcp.GcpIamCustomRolePatchStep;
import bio.terra.workspace.service.admin.flights.cloudcontexts.gcp.RetrieveGcpIamCustomRoleStep;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRole;
import bio.terra.workspace.service.workspace.GcpCloudSyncRoleMapping;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import com.google.api.services.iam.v1.model.Role;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("connectedPlus")
public class AdminServiceTest extends BaseConnectedTest {

  private static final List<String> INCOMPLETE_READER_PERMISSIONS =
      ImmutableList.of(
          "serviceusage.services.get", "serviceusage.services.list", "storage.buckets.list");
  private static final CustomGcpIamRole INCOMPLETE_PROJECT_READER =
      CustomGcpIamRole.of("PROJECT_READER", INCOMPLETE_READER_PERMISSIONS);

  @Autowired AdminService adminService;
  @Autowired JobService jobService;
  @Autowired WorkspaceDao workspaceDao;
  @Autowired WorkspaceConnectedTestUtils connectedTestUtils;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired GcpCloudSyncRoleMapping gcpCloudSyncRoleMapping;
  @Autowired CrlService crlService;
  @Autowired WorkspaceActivityLogDao workspaceActivityLogDao;

  private IamCow iamCow;
  List<UUID> workspaceIds = new ArrayList<>();
  List<String> projectIds;

  @AfterEach
  void cleanUp() {
    jobService.setFlightDebugInfoForTest(null);
  }

  void setup() {
    iamCow = crlService.getIamCow();
    workspaceIds.add(
        connectedTestUtils
            .createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest())
            .getWorkspaceId());
    workspaceIds.add(
        connectedTestUtils
            .createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest())
            .getWorkspaceId());
    workspaceIds.add(
        connectedTestUtils
            .createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest())
            .getWorkspaceId());
    projectIds =
        workspaceDao.getWorkspaceIdToGcpCloudContextMap().values().stream()
            .map(cloudContext -> GcpCloudContext.deserialize(cloudContext).getGcpProjectId())
            .toList();
  }

  void cleanUpWorkspace() {
    jobService.setFlightDebugInfoForTest(null);
    for (UUID workspaceId : workspaceIds) {
      connectedTestUtils.deleteWorkspaceAndCloudContext(
          userAccessUtils.defaultUserAuthRequest(), workspaceId);
    }
  }

  @Test
  public void syncIamRole_updateAppliedAndLogged() {
    setup();

    // Test idempotency of steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        RetrieveGcpIamCustomRoleStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(GcpIamCustomRolePatchStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    OffsetDateTime lastChangeTimestampOfWorkspace1 =
        workspaceActivityLogDao.getLastUpdatedDetails(workspaceIds.get(0)).get().changeDate();
    // First update. No change will be applied.
    String jobId =
        adminService.syncIamRoleForAllGcpProjects(
            userAccessUtils.defaultUserAuthRequest(), /* wetRun= */ true);
    jobService.waitForJob(jobId);
    for (String projectId : projectIds) {
      assertProjectReaderRoleMatchesExpected(
          projectId,
          gcpCloudSyncRoleMapping
              .getCustomGcpProjectIamRoles()
              .get(WsmIamRole.READER)
              .getIncludedPermissions());
    }
    OffsetDateTime newChangeTimestampOfWorkspace1 =
        workspaceActivityLogDao.getLastUpdatedDetails(workspaceIds.get(0)).get().changeDate();
    assertTrue(newChangeTimestampOfWorkspace1.isEqual(lastChangeTimestampOfWorkspace1));

    // Change the existing project to have incomplete permissions on PROJECT_READER
    for (String project : projectIds) {
      updateCustomRole(INCOMPLETE_PROJECT_READER, project);
    }
    OffsetDateTime lastChangeTimestampOfWorkspace2 =
        workspaceActivityLogDao.getLastUpdatedDetails(workspaceIds.get(1)).get().changeDate();
    OffsetDateTime lastChangeTimestampOfWorkspace3 =
        workspaceActivityLogDao.getLastUpdatedDetails(workspaceIds.get(2)).get().changeDate();

    // Second update, dry run
    jobId =
        adminService.syncIamRoleForAllGcpProjects(
            userAccessUtils.defaultUserAuthRequest(), /* wetRun= */ false);
    jobService.waitForJob(jobId);

    for (String projectId : projectIds) {
      assertProjectReaderRoleMatchesExpected(
          projectId, INCOMPLETE_PROJECT_READER.getIncludedPermissions());
    }

    // Third update, wet run
    jobId =
        adminService.syncIamRoleForAllGcpProjects(
            userAccessUtils.defaultUserAuthRequest(), /* wetRun= */ true);
    jobService.waitForJob(jobId);
    for (String projectId : projectIds) {
      assertProjectReaderRoleMatchesExpected(
          projectId,
          gcpCloudSyncRoleMapping
              .getCustomGcpProjectIamRoles()
              .get(WsmIamRole.READER)
              .getIncludedPermissions());
    }
    assertTrue(
        workspaceActivityLogDao
            .getLastUpdatedDetails(workspaceIds.get(0))
            .get()
            .changeDate()
            .isAfter(lastChangeTimestampOfWorkspace1));
    assertTrue(
        workspaceActivityLogDao
            .getLastUpdatedDetails(workspaceIds.get(1))
            .get()
            .changeDate()
            .isAfter(lastChangeTimestampOfWorkspace2));
    assertTrue(
        workspaceActivityLogDao
            .getLastUpdatedDetails(workspaceIds.get(2))
            .get()
            .changeDate()
            .isAfter(lastChangeTimestampOfWorkspace3));

    cleanUpWorkspace();
  }

  @Test
  public void syncIamRole_undo_permissionsRemainsTheSame() {
    setup();

    // The existing project has incomplete permissions on PROJECT_READER
    for (String project : projectIds) {
      updateCustomRole(INCOMPLETE_PROJECT_READER, project);
    }
    OffsetDateTime lastChangeTimestampOfWorkspace1 =
        workspaceActivityLogDao.getLastUpdatedDetails(workspaceIds.get(0)).get().changeDate();
    // Test idempotency of steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        RetrieveGcpIamCustomRoleStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(GcpIamCustomRolePatchStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).lastStepFailure(true).build());

    String jobId =
        adminService.syncIamRoleForAllGcpProjects(
            userAccessUtils.defaultUserAuthRequest(), /* wetRun= */ true);
    jobService.waitForJob(jobId);
    for (String projectId : projectIds) {
      assertProjectReaderRoleMatchesExpected(
          projectId, INCOMPLETE_PROJECT_READER.getIncludedPermissions());
    }
    OffsetDateTime newChangeTimestampOfWorkspace1 =
        workspaceActivityLogDao.getLastUpdatedDetails(workspaceIds.get(0)).get().changeDate();
    assertTrue(newChangeTimestampOfWorkspace1.isEqual(lastChangeTimestampOfWorkspace1));

    cleanUpWorkspace();
  }

  private void updateCustomRole(CustomGcpIamRole customRole, String projectId)
      throws RetryException {
    try {
      // projects/{PROJECT_ID}/roles/{CUSTOM_ROLE_ID}
      String fullyQualifiedRoleName = customRole.getFullyQualifiedRoleName(projectId);
      Role role = iamCow.projects().roles().get(fullyQualifiedRoleName).execute();
      role.setIncludedPermissions(customRole.getIncludedPermissions());
      iamCow.projects().roles().patch(fullyQualifiedRoleName, role).execute();
    } catch (IOException e) {
      // do nothing
    }
  }

  private Role retrieveCustomRoles(CustomGcpIamRole customGcpIamRole, String projectId) {
    try {
      return iamCow
          .projects()
          .roles()
          .get(customGcpIamRole.getFullyQualifiedRoleName(projectId))
          .execute();
    } catch (IOException e) {
      return null;
    }
  }

  private void assertProjectReaderRoleMatchesExpected(
      String projectId, List<String> expectedPermissions) {
    Role role = retrieveCustomRoles(INCOMPLETE_PROJECT_READER, projectId);
    assertNotNull(role);
    List<String> actualPermissions = role.getIncludedPermissions();
    assertTrue(expectedPermissions.containsAll(actualPermissions));
    assertEquals(expectedPermissions.size(), actualPermissions.size());
  }
}
