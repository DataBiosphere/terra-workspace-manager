package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.AwsUtils;
import io.vavr.collection.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;

@TestInstance(Lifecycle.PER_CLASS)
public class StopAwsSageMakerNotebookStepTest extends AwsSageMakerNotebookStepTest {
  @Test
  public void stopNotebook_InServiceTest() throws InterruptedException {
    // not resource deletion
    StopAwsSageMakerNotebookStep stopNotebookStep =
        new StopAwsSageMakerNotebookStep(notebookResource, mockAwsCloudContextService, false);

    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any()))
        .thenReturn(NotebookInstanceStatus.IN_SERVICE);
    mockAwsUtils
        .when(() -> AwsUtils.stopSageMakerNotebook(any(), any()))
        .thenAnswer(invocation -> null) /* success */
        .thenThrow(WorkspaceFixtures.API_EXCEPTION)
        .thenThrow(WorkspaceFixtures.NOT_FOUND_EXCEPTION)
        .thenThrow(WorkspaceFixtures.UNAUTHORIZED_EXCEPTION);
    mockAwsUtils
        .when(() -> AwsUtils.waitForSageMakerNotebookStatus(any(), any(), any()))
        .thenAnswer(invocation -> null); /* success */

    // do: stop success, wait success
    assertThat(
        stopNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // do: stop error
    StepResult stepResult = stopNotebookStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(
        WorkspaceFixtures.API_EXCEPTION.getClass(), stepResult.getException().get().getClass());

    // do: stop failure (not found)
    stepResult = stopNotebookStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(
        WorkspaceFixtures.NOT_FOUND_EXCEPTION.getClass(),
        stepResult.getException().get().getClass());

    // do: stop error
    assertThrows(
        WorkspaceFixtures.UNAUTHORIZED_EXCEPTION.getClass(),
        () -> stopNotebookStep.doStep(mockFlightContext));

    // resource deletion
    StopAwsSageMakerNotebookStep stopNotebookStepDeletion =
        new StopAwsSageMakerNotebookStep(notebookResource, mockAwsCloudContextService, true);

    mockAwsUtils
        .when(() -> AwsUtils.stopSageMakerNotebook(any(), any()))
        .thenThrow(WorkspaceFixtures.NOT_FOUND_EXCEPTION);

    // do: stop error (not found)
    assertThat(
        stopNotebookStepDeletion.doStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));

    // TODO-Dex undo
  }

  @Test
  public void stopNotebook_StoppingTest() throws InterruptedException {
    // not resource deletion
    StopAwsSageMakerNotebookStep stopNotebookStep =
        new StopAwsSageMakerNotebookStep(notebookResource, mockAwsCloudContextService, false);

    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any()))
        .thenReturn(NotebookInstanceStatus.STOPPING);
    mockAwsUtils
        .when(() -> AwsUtils.waitForSageMakerNotebookStatus(any(), any(), any()))
        .thenAnswer(invocation -> null) /* success */
        .thenThrow(WorkspaceFixtures.API_EXCEPTION)
        .thenThrow(WorkspaceFixtures.NOT_FOUND_EXCEPTION)
        .thenThrow(WorkspaceFixtures.UNAUTHORIZED_EXCEPTION);

    // do: wait success
    assertThat(
        stopNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // do: wait error
    StepResult stepResult = stopNotebookStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(
        WorkspaceFixtures.API_EXCEPTION.getClass(), stepResult.getException().get().getClass());

    // do: wait failure (not found)
    stepResult = stopNotebookStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(
        WorkspaceFixtures.NOT_FOUND_EXCEPTION.getClass(),
        stepResult.getException().get().getClass());

    // do: wait error
    assertThrows(
        WorkspaceFixtures.UNAUTHORIZED_EXCEPTION.getClass(),
        () -> stopNotebookStep.doStep(mockFlightContext));

    // resource deletion
    StopAwsSageMakerNotebookStep stopNotebookStepDeletion =
        new StopAwsSageMakerNotebookStep(notebookResource, mockAwsCloudContextService, true);

    mockAwsUtils
        .when(() -> AwsUtils.waitForSageMakerNotebookStatus(any(), any(), any()))
        .thenThrow(WorkspaceFixtures.NOT_FOUND_EXCEPTION);

    // do: wait failure (not found)
    assertThat(
        stopNotebookStepDeletion.doStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  public void stopNotebook_DeletingTest() throws InterruptedException {
    // not resource deletion
    StopAwsSageMakerNotebookStep stopNotebookStep =
        new StopAwsSageMakerNotebookStep(notebookResource, mockAwsCloudContextService, false);

    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any()))
        .thenReturn(NotebookInstanceStatus.DELETING);

    // do: error (not to be deleted)
    StepResult stepResult = stopNotebookStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(
        WorkspaceFixtures.API_EXCEPTION.getClass(), stepResult.getException().get().getClass());
    assertTrue(stepResult.getException().get().getMessage().contains("being deleted"));

    // resource deletion
    StopAwsSageMakerNotebookStep stopNotebookStepDeletion =
        new StopAwsSageMakerNotebookStep(notebookResource, mockAwsCloudContextService, true);

    // do: success (to be deleted)
    assertThat(
        stopNotebookStepDeletion.doStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));
  }

  public void stopNotebook_Error_execute(StopAwsSageMakerNotebookStep stopStep)
      throws InterruptedException {
    for (NotebookInstanceStatus status :
        List.of(
            NotebookInstanceStatus.PENDING,
            NotebookInstanceStatus.UPDATING,
            NotebookInstanceStatus.UNKNOWN_TO_SDK_VERSION)) {
      mockAwsUtils.when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any())).thenReturn(status);

      // do: always error
      StepResult stepResult = stopStep.doStep(mockFlightContext);
      assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
      assertEquals(
          WorkspaceFixtures.API_EXCEPTION.getClass(), stepResult.getException().get().getClass());
      assertTrue(
          stepResult
              .getException()
              .get()
              .getMessage()
              .contains("Cannot stop AWS SageMaker Notebook resource"));
    }
  }

  @Test
  public void stopNotebook_ErrorTest() throws InterruptedException {
    // not resource deletion
    stopNotebook_Error_execute(
        new StopAwsSageMakerNotebookStep(notebookResource, mockAwsCloudContextService, false));

    // resource deletion
    stopNotebook_Error_execute(
        new StopAwsSageMakerNotebookStep(notebookResource, mockAwsCloudContextService, true));
  }

  private void stopNotebook_NoOp_execute(StopAwsSageMakerNotebookStep stopStep)
      throws InterruptedException {
    for (NotebookInstanceStatus status :
        List.of(NotebookInstanceStatus.STOPPED, NotebookInstanceStatus.FAILED)) {
      mockAwsUtils.when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any())).thenReturn(status);

      // do: always success (no-op)
      assertThat(
          "Expected to succeed for status: " + status,
          stopStep.doStep(mockFlightContext),
          equalTo(StepResult.getStepResultSuccess()));
    }
  }

  @Test
  public void stopNotebook_NoOpTest() throws InterruptedException {
    // not resource deletion
    stopNotebook_NoOp_execute(
        new StopAwsSageMakerNotebookStep(notebookResource, mockAwsCloudContextService, false));

    // resource deletion
    stopNotebook_NoOp_execute(
        new StopAwsSageMakerNotebookStep(notebookResource, mockAwsCloudContextService, true));
  }

  private void stopNotebook_getStatusError_execute(StopAwsSageMakerNotebookStep stopStep)
      throws InterruptedException {
    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any()))
        .thenThrow(WorkspaceFixtures.API_EXCEPTION)
        .thenThrow(WorkspaceFixtures.UNAUTHORIZED_EXCEPTION);

    // do: getStatus error (other)
    StepResult stepResult = stopStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(
        WorkspaceFixtures.API_EXCEPTION.getClass(), stepResult.getException().get().getClass());

    // do: getStatus error
    assertThrows(
        WorkspaceFixtures.UNAUTHORIZED_EXCEPTION.getClass(),
        () -> stopStep.doStep(mockFlightContext));
  }

  @Test
  public void stopNotebook_getStatusErrorTest() throws InterruptedException {
    // not resource deletion
    StopAwsSageMakerNotebookStep stopNotebookStep =
        new StopAwsSageMakerNotebookStep(notebookResource, mockAwsCloudContextService, false);
    stopNotebook_getStatusError_execute(stopNotebookStep);
    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any()))
        .thenThrow(WorkspaceFixtures.NOT_FOUND_EXCEPTION);

    // do: getStatus error (not found)
    StepResult stepResult = stopNotebookStep.doStep(mockFlightContext);
    assertEquals(StepStatus.STEP_RESULT_FAILURE_FATAL, stepResult.getStepStatus());
    assertEquals(
        WorkspaceFixtures.NOT_FOUND_EXCEPTION.getClass(),
        stepResult.getException().get().getClass());

    // resource deletion
    StopAwsSageMakerNotebookStep stopNotebookStepDeletion =
        new StopAwsSageMakerNotebookStep(notebookResource, mockAwsCloudContextService, true);

    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any()))
        .thenThrow(WorkspaceFixtures.NOT_FOUND_EXCEPTION);

    // do: getStatus error (not found)
    assertThat(
        stopNotebookStepDeletion.doStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));
  }

  // TODO(BENCH-717) undo stop (start)
}
