package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.common.exception.SerializationException;
import bio.terra.policy.model.TpsComponent;
import bio.terra.policy.model.TpsObjectType;
import bio.terra.policy.model.TpsPaoConflict;
import bio.terra.policy.model.TpsPaoDescription;
import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.policy.model.TpsUpdateMode;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.db.StateDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.policy.PolicyValidator;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class WorkspaceUnitTest extends BaseUnitTest {
  private static final Logger logger = LoggerFactory.getLogger(WorkspaceUnitTest.class);
  @MockBean private WorkspaceDao mockWorkspaceDao;
  @MockBean private PolicyValidator mockPolicyValidator;
  @MockBean private WorkspaceActivityLogService mockWorkspaceActivityLogService;

  @Autowired private WorkspaceService workspaceService;

  @Test
  void testErrorSerdes_errorReportExceptionWorks() {
    // Normal case exercising the exception serdes code
    var fex =
        new ForbiddenException(
            "User mnemosyne.ninetynine@gmail.com is not authorized to perform action delete on controlled-application-private-workspace-resource ef5ee667-48f1-4407-8ca1-0efe109591c7");
    String errorJson = StateDao.normalizeException(fex);

    var ex = StateDao.deserializeException(errorJson);
    assertEquals(fex.getStatusCode(), ex.getStatusCode());
    assertTrue(StringUtils.contains(ex.getMessage(), fex.getMessage()));
  }

  @Test
  void testErrorSerdes_junkExceptionWorks() {
    // Case where junk is in the exception
    var exrex = StateDao.deserializeException("bad bad bad");
    assertTrue(exrex instanceof SerializationException);
  }

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
    doThrow(new PolicyConflictException("conflict"))
        .when(mockPolicyValidator)
        .validateWorkspaceConformsToPolicy(any(), any(), any());

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

  @Test
  void updatePolicies_dryRun() {
    Workspace workspace = WorkspaceFixtures.buildMcWorkspace();
    when(mockWorkspaceDao.getWorkspace(workspace.workspaceId())).thenReturn(workspace);

    when(mockTpsApiDispatch()
            .updatePao(eq(workspace.workspaceId()), any(), any(), eq(TpsUpdateMode.DRY_RUN)))
        .thenReturn(new TpsPaoUpdateResult().conflicts(List.of()).updateApplied(false));

    var results =
        workspaceService.updatePolicy(
            workspace.workspaceId(),
            new TpsPolicyInputs(),
            new TpsPolicyInputs(),
            TpsUpdateMode.DRY_RUN,
            USER_REQUEST);

    assertFalse(results.isUpdateApplied());
  }

  @Test
  void updatePolicies_policyConflict() {
    Workspace workspace = WorkspaceFixtures.buildMcWorkspace();
    when(mockWorkspaceDao.getWorkspace(workspace.workspaceId())).thenReturn(workspace);

    when(mockTpsApiDispatch()
            .updatePao(eq(workspace.workspaceId()), any(), any(), eq(TpsUpdateMode.DRY_RUN)))
        .thenReturn(
            new TpsPaoUpdateResult().conflicts(List.of(new TpsPaoConflict())).updateApplied(false));

    assertThrows(
        PolicyConflictException.class,
        () ->
            workspaceService.updatePolicy(
                workspace.workspaceId(),
                new TpsPolicyInputs(),
                new TpsPolicyInputs(),
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
  void updatePolicies_workspaceConflict() {
    Workspace workspace = WorkspaceFixtures.buildMcWorkspace();
    when(mockWorkspaceDao.getWorkspace(workspace.workspaceId())).thenReturn(workspace);

    when(mockTpsApiDispatch()
            .updatePao(eq(workspace.workspaceId()), any(), any(), eq(TpsUpdateMode.DRY_RUN)))
        .thenReturn(new TpsPaoUpdateResult().conflicts(List.of()).updateApplied(false));
    doThrow(new PolicyConflictException("conflict"))
        .when(mockPolicyValidator)
        .validateWorkspaceConformsToPolicy(any(), any(), any());

    assertThrows(
        PolicyConflictException.class,
        () ->
            workspaceService.updatePolicy(
                workspace.workspaceId(),
                new TpsPolicyInputs(),
                new TpsPolicyInputs(),
                TpsUpdateMode.DRY_RUN,
                USER_REQUEST));
  }

  @Test
  void updatePolicies_applied() {
    Workspace workspace = WorkspaceFixtures.buildMcWorkspace();
    when(mockWorkspaceDao.getWorkspace(workspace.workspaceId())).thenReturn(workspace);

    when(mockTpsApiDispatch()
            .updatePao(eq(workspace.workspaceId()), any(), any(), eq(TpsUpdateMode.DRY_RUN)))
        .thenReturn(new TpsPaoUpdateResult().conflicts(List.of()).updateApplied(false));
    when(mockTpsApiDispatch()
            .updatePao(
                eq(workspace.workspaceId()), any(), any(), eq(TpsUpdateMode.FAIL_ON_CONFLICT)))
        .thenReturn(new TpsPaoUpdateResult().conflicts(List.of()).updateApplied(true));

    var results =
        workspaceService.updatePolicy(
            workspace.workspaceId(),
            new TpsPolicyInputs(),
            new TpsPolicyInputs(),
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
