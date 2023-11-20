package bio.terra.workspace.service.policy.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.policy.model.TpsComponent;
import bio.terra.policy.model.TpsObjectType;
import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.policy.model.TpsUpdateMode;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.policy.exception.PolicyServiceAPIException;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class LinkSpendProfilePolicyAttributesStepTest extends BaseUnitTest {
  @Mock private TpsApiDispatch tpsApiDispatch;
  @Mock private FlightContext flightContext;

  @Test
  public void doStep_linkSuccess() throws InterruptedException, RetryException {
    var workspaceId = UUID.randomUUID();
    var spendProfileId = UUID.randomUUID();
    var workingMap = new FlightMap();
    var linkPolicyStep =
        new LinkSpendProfilePolicyAttributesStep(
            workspaceId, new SpendProfileId(spendProfileId.toString()), tpsApiDispatch);

    when(tpsApiDispatch.getOrCreatePao(any(), any(), any()))
        .thenReturn(new TpsPaoGetResult().effectiveAttributes(new TpsPolicyInputs()));
    when(tpsApiDispatch.linkPao(workspaceId, spendProfileId, TpsUpdateMode.FAIL_ON_CONFLICT))
        .thenReturn(
            new TpsPaoUpdateResult().updateApplied(true).resultingPao(new TpsPaoGetResult()));
    when(flightContext.getWorkingMap()).thenReturn(workingMap);

    var stepResult = linkPolicyStep.doStep(flightContext);

    assertEquals(StepResult.getStepResultSuccess(), stepResult);
    verify(tpsApiDispatch, times(1))
        .getOrCreatePao(workspaceId, TpsComponent.WSM, TpsObjectType.WORKSPACE);
    verify(tpsApiDispatch, times(1))
        .getOrCreatePao(spendProfileId, TpsComponent.BPM, TpsObjectType.BILLING_PROFILE);
    verify(tpsApiDispatch, times(1))
        .linkPao(workspaceId, spendProfileId, TpsUpdateMode.FAIL_ON_CONFLICT);
  }

  @Test
  public void doStep_nonUuidSpendProfileSuccess() throws InterruptedException, RetryException {
    var workspaceId = UUID.randomUUID();
    var spendProfileId = "wm-default-spend-profile";
    var linkPolicyStep =
        new LinkSpendProfilePolicyAttributesStep(
            workspaceId, new SpendProfileId(spendProfileId), tpsApiDispatch);

    var stepResult = linkPolicyStep.doStep(flightContext);

    assertEquals(StepResult.getStepResultSuccess(), stepResult);
    verify(tpsApiDispatch, times(0)).getOrCreatePao(any(), any(), any());
    verify(tpsApiDispatch, times(0)).linkPao(any(), any(), any());
  }

  @Test
  public void doStep_getPaoFailure() throws InterruptedException, RetryException {
    var workspaceId = UUID.randomUUID();
    var spendProfileId = UUID.randomUUID();
    var linkPolicyStep =
        new LinkSpendProfilePolicyAttributesStep(
            workspaceId, new SpendProfileId(spendProfileId.toString()), tpsApiDispatch);

    doThrow(new PolicyServiceAPIException("failure"))
        .when(tpsApiDispatch)
        .getOrCreatePao(any(), any(), any());

    var stepResult = linkPolicyStep.doStep(flightContext);

    assertEquals(StepStatus.STEP_RESULT_FAILURE_RETRY, stepResult.getStepStatus());
    verify(tpsApiDispatch, times(1)).getOrCreatePao(any(), any(), any());
    verify(tpsApiDispatch, times(0)).linkPao(any(), any(), any());
  }

  @Test
  public void doStep_policyLinkUnappliedFailure() throws InterruptedException, RetryException {
    var workspaceId = UUID.randomUUID();
    var spendProfileId = UUID.randomUUID();
    var workingMap = new FlightMap();
    var linkPolicyStep =
        new LinkSpendProfilePolicyAttributesStep(
            workspaceId, new SpendProfileId(spendProfileId.toString()), tpsApiDispatch);

    when(tpsApiDispatch.getOrCreatePao(any(), any(), any()))
        .thenReturn(new TpsPaoGetResult().effectiveAttributes(new TpsPolicyInputs()));
    when(tpsApiDispatch.linkPao(workspaceId, spendProfileId, TpsUpdateMode.FAIL_ON_CONFLICT))
        .thenReturn(
            new TpsPaoUpdateResult().updateApplied(false).resultingPao(new TpsPaoGetResult()));
    when(flightContext.getWorkingMap()).thenReturn(workingMap);

    assertThrows(PolicyConflictException.class, () -> linkPolicyStep.doStep(flightContext));

    verify(tpsApiDispatch, times(1))
        .getOrCreatePao(workspaceId, TpsComponent.WSM, TpsObjectType.WORKSPACE);
    verify(tpsApiDispatch, times(1))
        .getOrCreatePao(spendProfileId, TpsComponent.BPM, TpsObjectType.BILLING_PROFILE);
    verify(tpsApiDispatch, times(1))
        .linkPao(workspaceId, spendProfileId, TpsUpdateMode.FAIL_ON_CONFLICT);
  }

  @Test
  public void doStep_policyLinkFailure() throws InterruptedException, RetryException {
    var workspaceId = UUID.randomUUID();
    var spendProfileId = UUID.randomUUID();
    var workingMap = new FlightMap();
    var linkPolicyStep =
        new LinkSpendProfilePolicyAttributesStep(
            workspaceId, new SpendProfileId(spendProfileId.toString()), tpsApiDispatch);

    when(tpsApiDispatch.getOrCreatePao(any(), any(), any()))
        .thenReturn(new TpsPaoGetResult().effectiveAttributes(new TpsPolicyInputs()));
    when(flightContext.getWorkingMap()).thenReturn(workingMap);
    doThrow(new PolicyServiceAPIException("failure"))
        .when(tpsApiDispatch)
        .linkPao(any(), any(), any());

    var stepResult = linkPolicyStep.doStep(flightContext);

    assertEquals(StepStatus.STEP_RESULT_FAILURE_RETRY, stepResult.getStepStatus());
    verify(tpsApiDispatch, times(2)).getOrCreatePao(any(), any(), any());
    verify(tpsApiDispatch, times(1)).linkPao(any(), any(), any());
  }

  @Test
  public void undoStep_noLinkFoundSuccess() throws InterruptedException, RetryException {
    var workspaceId = UUID.randomUUID();
    var spendProfileId = UUID.randomUUID();
    var workingMap = new FlightMap();
    var linkPolicyStep =
        new LinkSpendProfilePolicyAttributesStep(
            workspaceId, new SpendProfileId(spendProfileId.toString()), tpsApiDispatch);

    when(flightContext.getWorkingMap()).thenReturn(workingMap);

    var stepResult = linkPolicyStep.undoStep(flightContext);

    assertEquals(StepResult.getStepResultSuccess(), stepResult);
    verify(tpsApiDispatch, times(0)).replacePao(any(), any(), any());
  }

  @Test
  public void undoStep_replacePaoSuccess() throws InterruptedException, RetryException {
    var workspaceId = UUID.randomUUID();
    var spendProfileId = UUID.randomUUID();
    var workingMap = new FlightMap();
    var linkPolicyStep =
        new LinkSpendProfilePolicyAttributesStep(
            workspaceId, new SpendProfileId(spendProfileId.toString()), tpsApiDispatch);

    workingMap.put(WorkspaceFlightMapKeys.POLICIES, new TpsPolicyInputs());
    when(flightContext.getWorkingMap()).thenReturn(workingMap);
    when(tpsApiDispatch.replacePao(any(), any(), any()))
        .thenReturn(new TpsPaoUpdateResult().updateApplied(true));

    var stepResult = linkPolicyStep.undoStep(flightContext);

    assertEquals(StepResult.getStepResultSuccess(), stepResult);
    verify(tpsApiDispatch, times(1)).replacePao(any(), any(), any());
  }

  @Test
  public void undoStep_replacePaoUnappliedFailure() throws InterruptedException, RetryException {
    var workspaceId = UUID.randomUUID();
    var spendProfileId = UUID.randomUUID();
    var workingMap = new FlightMap();
    var linkPolicyStep =
        new LinkSpendProfilePolicyAttributesStep(
            workspaceId, new SpendProfileId(spendProfileId.toString()), tpsApiDispatch);

    workingMap.put(WorkspaceFlightMapKeys.POLICIES, new TpsPolicyInputs());
    when(flightContext.getWorkingMap()).thenReturn(workingMap);
    when(tpsApiDispatch.replacePao(any(), any(), any()))
        .thenReturn(new TpsPaoUpdateResult().updateApplied(false));

    var stepResult = linkPolicyStep.undoStep(flightContext);

    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    verify(tpsApiDispatch, times(1)).replacePao(any(), any(), any());
  }

  @Test
  public void undoStep_replacePaoFailure() throws InterruptedException, RetryException {
    var workspaceId = UUID.randomUUID();
    var spendProfileId = UUID.randomUUID();
    var workingMap = new FlightMap();
    var linkPolicyStep =
        new LinkSpendProfilePolicyAttributesStep(
            workspaceId, new SpendProfileId(spendProfileId.toString()), tpsApiDispatch);

    workingMap.put(WorkspaceFlightMapKeys.POLICIES, new TpsPolicyInputs());
    when(flightContext.getWorkingMap()).thenReturn(workingMap);
    doThrow(new PolicyServiceAPIException("failure"))
        .when(tpsApiDispatch)
        .replacePao(any(), any(), any());

    var stepResult = linkPolicyStep.undoStep(flightContext);

    assertEquals(StepStatus.STEP_RESULT_FAILURE_RETRY, stepResult.getStepStatus());
    verify(tpsApiDispatch, times(1)).replacePao(any(), any(), any());
  }
}
