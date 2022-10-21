package bio.terra.workspace.service.admin;

import static bio.terra.workspace.service.workspace.CloudSyncRoleMapping.CUSTOM_GCP_PROJECT_IAM_ROLES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.admin.flights.cloudcontexts.gcp.GcpIamCustomRolePatchStep;
import bio.terra.workspace.service.admin.flights.cloudcontexts.gcp.RetrieveGcpIamCustomRoleStep;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRole;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import com.google.api.services.iam.v1.model.Role;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
  @Autowired CrlService crlService;

  private IamCow iamCow;
  List<UUID> workspaceIds = new ArrayList<>();
  List<String> projectIds;

  @BeforeEach
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
        workspaceDao.listCloudContexts(CloudPlatform.GCP).stream()
            .map(cloudContext -> GcpCloudContext.deserialize(cloudContext).getGcpProjectId())
            .toList();
    // The existing project has incomplete permissions on PROJECT_READER
    for (String project : projectIds) {
      updateCustomRole(INCOMPLETE_PROJECT_READER, project);
    }
  }

  @AfterEach
  void cleanUp() {
    jobService.setFlightDebugInfoForTest(null);
    for (UUID workspaceId : workspaceIds) {
      connectedTestUtils.deleteWorkspaceAndGcpContext(
          userAccessUtils.defaultUserAuthRequest(), workspaceId);
    }
  }

  @Test
  public void syncIamRole_newPermissionsAddedToCustomRoleProjectReader() {
    // Test idempotency of steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        RetrieveGcpIamCustomRoleStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(GcpIamCustomRolePatchStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    String jobId =
        adminService.syncIamRoleForAllGcpProjects(
            userAccessUtils.defaultUserAuthRequest(), /*wetRun=*/ true);
    jobService.waitForJob(jobId);
    for (String projectId : projectIds) {
      assertProjectReaderRoleIsUpdated(
          projectId, CUSTOM_GCP_PROJECT_IAM_ROLES.get(WsmIamRole.READER).getIncludedPermissions());
    }
  }

  @Test
  public void syncIamRole_undo() {
    // Test idempotency of steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        RetrieveGcpIamCustomRoleStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(GcpIamCustomRolePatchStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).lastStepFailure(true).build());

    String jobId =
        adminService.syncIamRoleForAllGcpProjects(
            userAccessUtils.defaultUserAuthRequest(), /*wetRun=*/ true);
    jobService.waitForJob(jobId);
    for (String projectId : projectIds) {
      assertProjectReaderRoleIsUpdated(
          projectId, INCOMPLETE_PROJECT_READER.getIncludedPermissions());
    }
  }

  @Test
  public void syncIamRole_dryRun_permissionsNotUpdated() {
    // Test idempotency of steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        RetrieveGcpIamCustomRoleStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    String jobId =
        adminService.syncIamRoleForAllGcpProjects(
            userAccessUtils.defaultUserAuthRequest(), /*wetRun=*/ false);
    jobService.waitForJob(jobId);

    for (String projectId : projectIds) {
      assertProjectReaderRoleIsUpdated(
          projectId, INCOMPLETE_PROJECT_READER.getIncludedPermissions());
    }
  }

  @Test
  public void syncIamRoles_noProjectsFound_throwsInternalServerErrorException() {
    for (UUID workspaceId : workspaceIds) {
      connectedTestUtils.deleteWorkspaceAndGcpContext(
          userAccessUtils.defaultUserAuthRequest(), workspaceId);
    }

    assertThrows(
        InternalServerErrorException.class,
        () ->
            adminService.syncIamRoleForAllGcpProjects(
                userAccessUtils.defaultUserAuthRequest(), /*wetRun=*/ false));
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

  private void assertProjectReaderRoleIsUpdated(
      String projectId, List<String> expectedPermissions) {
    Role role = retrieveCustomRoles(INCOMPLETE_PROJECT_READER, projectId);
    assertNotNull(role);
    List<String> actualPermissions = role.getIncludedPermissions();
    assertTrue(expectedPermissions.containsAll(actualPermissions));
    assertEquals(expectedPermissions.size(), actualPermissions.size());
  }
}
