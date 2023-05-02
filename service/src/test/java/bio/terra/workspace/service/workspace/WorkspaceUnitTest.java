package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.policy.model.TpsComponent;
import bio.terra.policy.model.TpsObjectType;
import bio.terra.policy.model.TpsPaoConflict;
import bio.terra.policy.model.TpsPaoDescription;
import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsUpdateMode;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.policy.PolicyValidator;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class WorkspaceUnitTest extends BaseUnitTest {
  @MockBean private WorkspaceDao mockWorkspaceDao;
  @MockBean private PolicyValidator mockPolicyValidator;
  @MockBean private WorkspaceActivityLogService mockWorkspaceActivityLogService;

  @Autowired private WorkspaceService workspaceService;

  private AuthenticatedUserRequest userRequest =
      new AuthenticatedUserRequest("email", "id", Optional.of("token"));

  @Test
  void workspaceRequiredFields() {
    assertThrows(
        MissingRequiredFieldsException.class, () -> Workspace.builder().displayName("abc").build());

    assertThrows(
        MissingRequiredFieldsException.class,
        () -> Workspace.builder().workspaceId(UUID.randomUUID()).build());

    assertThrows(
        MissingRequiredFieldsException.class,
        () -> Workspace.builder().workspaceStage(WorkspaceStage.MC_WORKSPACE).build());
  }

  @Test
  void validateWorkspaceConformsToPolicy() {
    // should not throw exception
    workspaceService.validateWorkspaceConformsToPolicy(
        WorkspaceFixtures.buildMcWorkspace(), new TpsPaoGetResult(), userRequest);
  }

  @Test
  void validateWorkspaceConformsToPolicy_reportsErrors() {
    final String protectedError = "protected";
    final String regionError = "region";
    final String groupError = "group";

    when(mockPolicyValidator.validateWorkspaceConformsToProtectedDataPolicy(any(), any(), any()))
        .thenReturn(List.of(protectedError));
    when(mockPolicyValidator.validateWorkspaceConformsToRegionPolicy(any(), any(), any()))
        .thenReturn(List.of(regionError));
    when(mockPolicyValidator.validateWorkspaceConformsToGroupPolicy(any(), any(), any()))
        .thenReturn(List.of(groupError));

    var exception =
        assertThrows(
            PolicyConflictException.class,
            () ->
                workspaceService.validateWorkspaceConformsToPolicy(
                    WorkspaceFixtures.buildMcWorkspace(), new TpsPaoGetResult(), userRequest));

    assertIterableEquals(List.of(regionError, protectedError, groupError), exception.getCauses());
  }

  @Test
  void linkPolicies_dryRun() {
    Workspace workspace = WorkspaceFixtures.buildMcWorkspace();
    when(mockWorkspaceDao.getWorkspace(workspace.workspaceId())).thenReturn(workspace);

    final UUID sourceId = UUID.randomUUID();
    when(mockTpsApiDispatch().linkPao(workspace.workspaceId(), sourceId, TpsUpdateMode.DRY_RUN))
        .thenReturn(new TpsPaoUpdateResult().conflicts(List.of()).updateApplied(false));

    var results =
        workspaceService.linkPolicies(
            workspace.workspaceId(),
            new TpsPaoDescription()
                .objectId(sourceId)
                .component(TpsComponent.TDR)
                .objectType(TpsObjectType.SNAPSHOT),
            TpsUpdateMode.DRY_RUN,
            USER_REQUEST);

    assertFalse(results.isUpdateApplied());
  }

  @Test
  void linkPolicies_policyConflict() {
    Workspace workspace = WorkspaceFixtures.buildMcWorkspace();
    when(mockWorkspaceDao.getWorkspace(workspace.workspaceId())).thenReturn(workspace);

    final UUID sourceId = UUID.randomUUID();
    when(mockTpsApiDispatch().linkPao(workspace.workspaceId(), sourceId, TpsUpdateMode.DRY_RUN))
        .thenReturn(
            new TpsPaoUpdateResult().conflicts(List.of(new TpsPaoConflict())).updateApplied(false));

    assertThrows(
        PolicyConflictException.class,
        () ->
            workspaceService.linkPolicies(
                workspace.workspaceId(),
                new TpsPaoDescription()
                    .objectId(sourceId)
                    .component(TpsComponent.TDR)
                    .objectType(TpsObjectType.SNAPSHOT),
                TpsUpdateMode.FAIL_ON_CONFLICT,
                USER_REQUEST));

    verify(mockWorkspaceActivityLogService, never())
        .writeActivity(
            USER_REQUEST,
            workspace.workspaceId(),
            OperationType.UPDATE,
            workspace.workspaceId().toString(),
            ActivityLogChangedTarget.POLICIES);
  }

  @Test
  void linkPolicies_workspaceConflict() {
    Workspace workspace = WorkspaceFixtures.buildMcWorkspace();
    when(mockWorkspaceDao.getWorkspace(workspace.workspaceId())).thenReturn(workspace);

    final UUID sourceId = UUID.randomUUID();
    when(mockTpsApiDispatch().linkPao(workspace.workspaceId(), sourceId, TpsUpdateMode.DRY_RUN))
        .thenReturn(new TpsPaoUpdateResult().conflicts(List.of()).updateApplied(false));
    when(mockPolicyValidator.validateWorkspaceConformsToProtectedDataPolicy(any(), any(), any()))
        .thenReturn(List.of("conflict"));

    assertThrows(
        PolicyConflictException.class,
        () ->
            workspaceService.linkPolicies(
                workspace.workspaceId(),
                new TpsPaoDescription()
                    .objectId(sourceId)
                    .component(TpsComponent.TDR)
                    .objectType(TpsObjectType.SNAPSHOT),
                TpsUpdateMode.DRY_RUN,
                USER_REQUEST));
  }

  @Test
  void linkPolicies_applied() {
    Workspace workspace = WorkspaceFixtures.buildMcWorkspace();
    when(mockWorkspaceDao.getWorkspace(workspace.workspaceId())).thenReturn(workspace);

    final UUID sourceId = UUID.randomUUID();
    when(mockTpsApiDispatch().linkPao(workspace.workspaceId(), sourceId, TpsUpdateMode.DRY_RUN))
        .thenReturn(new TpsPaoUpdateResult().conflicts(List.of()).updateApplied(false));
    when(mockTpsApiDispatch()
            .linkPao(workspace.workspaceId(), sourceId, TpsUpdateMode.FAIL_ON_CONFLICT))
        .thenReturn(new TpsPaoUpdateResult().conflicts(List.of()).updateApplied(true));

    var results =
        workspaceService.linkPolicies(
            workspace.workspaceId(),
            new TpsPaoDescription()
                .objectId(sourceId)
                .component(TpsComponent.TDR)
                .objectType(TpsObjectType.SNAPSHOT),
            TpsUpdateMode.FAIL_ON_CONFLICT,
            USER_REQUEST);

    assertTrue(results.isUpdateApplied());
    verify(mockWorkspaceActivityLogService)
        .writeActivity(
            USER_REQUEST,
            workspace.workspaceId(),
            OperationType.UPDATE,
            workspace.workspaceId().toString(),
            ActivityLogChangedTarget.POLICIES);
  }
}
