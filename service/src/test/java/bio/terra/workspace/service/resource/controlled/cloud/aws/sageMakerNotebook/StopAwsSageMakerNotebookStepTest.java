package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_CREDENTIALS_PROVIDER;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.API_EXCEPTION;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.NOT_FOUND_EXCEPTION;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.UNAUTHORIZED_EXCEPTION;
import static bio.terra.workspace.common.utils.TestUtils.assertStepResultFatal;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.utils.AwsUtils;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;

public class StopAwsSageMakerNotebookStepTest extends BaseAwsSageMakerNotebookStepTest {

  @Test
  void executeStopAwsSageMakerNotebook_InService_Test() {
    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any()))
        .thenReturn(NotebookInstanceStatus.IN_SERVICE);
    mockAwsUtils
        .when(() -> AwsUtils.stopSageMakerNotebook(any(), any()))
        .thenAnswer(invocation -> null) /* success */
        .thenAnswer(invocation -> null) /* success */
        .thenAnswer(invocation -> null) /* success */
        .thenThrow(NOT_FOUND_EXCEPTION)
        .thenThrow(API_EXCEPTION);
    mockAwsUtils
        .when(() -> AwsUtils.waitForSageMakerNotebookStatus(any(), any(), any()))
        .thenAnswer(invocation -> null) /* success */
        .thenThrow(NOT_FOUND_EXCEPTION)
        .thenThrow(UNAUTHORIZED_EXCEPTION);

    // stop success, wait success
    assertThat(
        StopAwsSageMakerNotebookStep.executeStopAwsSageMakerNotebook(
            AWS_CREDENTIALS_PROVIDER, notebookResource),
        equalTo(StepResult.getStepResultSuccess()));

    // stop success, wait failure (not found)
    assertThrows(
        NotFoundException.class,
        () ->
            StopAwsSageMakerNotebookStep.executeStopAwsSageMakerNotebook(
                AWS_CREDENTIALS_PROVIDER, notebookResource));

    // stop success, wait error
    assertStepResultFatal(
        StopAwsSageMakerNotebookStep.executeStopAwsSageMakerNotebook(
            AWS_CREDENTIALS_PROVIDER, notebookResource),
        UnauthorizedException.class);

    // stop error (not found)
    assertThrows(
        NotFoundException.class,
        () ->
            StopAwsSageMakerNotebookStep.executeStopAwsSageMakerNotebook(
                AWS_CREDENTIALS_PROVIDER, notebookResource));

    // stop error
    assertStepResultFatal(
        StopAwsSageMakerNotebookStep.executeStopAwsSageMakerNotebook(
            AWS_CREDENTIALS_PROVIDER, notebookResource),
        ApiException.class);
  }

  @Test
  void executeStopAwsSageMakerNotebook_Stopping_Test() {
    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any()))
        .thenReturn(NotebookInstanceStatus.STOPPING);
    mockAwsUtils
        .when(() -> AwsUtils.waitForSageMakerNotebookStatus(any(), any(), any()))
        .thenAnswer(invocation -> null) /* success */
        .thenThrow(NOT_FOUND_EXCEPTION)
        .thenThrow(UNAUTHORIZED_EXCEPTION);

    // wait success
    assertThat(
        StopAwsSageMakerNotebookStep.executeStopAwsSageMakerNotebook(
            AWS_CREDENTIALS_PROVIDER, notebookResource),
        equalTo(StepResult.getStepResultSuccess()));

    // wait error (not found)
    assertThrows(
        NotFoundException.class,
        () ->
            StopAwsSageMakerNotebookStep.executeStopAwsSageMakerNotebook(
                AWS_CREDENTIALS_PROVIDER, notebookResource));

    // wait error
    assertStepResultFatal(
        StopAwsSageMakerNotebookStep.executeStopAwsSageMakerNotebook(
            AWS_CREDENTIALS_PROVIDER, notebookResource),
        UnauthorizedException.class);
  }

  @Test
  void executeStopAwsSageMakerNotebook_Deleting_Test() {
    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any()))
        .thenReturn(NotebookInstanceStatus.DELETING);

    // always error
    assertThrows(
        NotFoundException.class,
        () ->
            StopAwsSageMakerNotebookStep.executeStopAwsSageMakerNotebook(
                AWS_CREDENTIALS_PROVIDER, notebookResource));
  }

  @Test
  void executeStopAwsSageMakerNotebook_OtherError_Test() {
    for (NotebookInstanceStatus status :
        List.of(
            NotebookInstanceStatus.PENDING,
            NotebookInstanceStatus.UPDATING,
            NotebookInstanceStatus.UNKNOWN_TO_SDK_VERSION)) {
      mockAwsUtils.when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any())).thenReturn(status);

      // always error
      assertStepResultFatal(
          StopAwsSageMakerNotebookStep.executeStopAwsSageMakerNotebook(
              AWS_CREDENTIALS_PROVIDER, notebookResource),
          ApiException.class,
          "execute stop expected to fail on notebook with status: " + status);
    }
  }

  @Test
  void executeStopAwsSageMakerNotebook_OtherSuccess_Test() {
    for (NotebookInstanceStatus status :
        List.of(NotebookInstanceStatus.STOPPED, NotebookInstanceStatus.FAILED)) {
      mockAwsUtils.when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any())).thenReturn(status);

      // always success (no-op)
      assertThat(
          "execute stop expected to succeed on notebook with status: " + status,
          StopAwsSageMakerNotebookStep.executeStopAwsSageMakerNotebook(
              AWS_CREDENTIALS_PROVIDER, notebookResource),
          equalTo(StepResult.getStepResultSuccess()));
    }
  }

  @Test
  void executeStopAwsSageMakerNotebook_GetStatusError_Test() {
    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any()))
        .thenThrow(NOT_FOUND_EXCEPTION)
        .thenThrow(UNAUTHORIZED_EXCEPTION);

    // error (not found)
    assertThrows(
        NotFoundException.class,
        () ->
            StopAwsSageMakerNotebookStep.executeStopAwsSageMakerNotebook(
                AWS_CREDENTIALS_PROVIDER, notebookResource));

    // wait error
    assertStepResultFatal(
        StopAwsSageMakerNotebookStep.executeStopAwsSageMakerNotebook(
            AWS_CREDENTIALS_PROVIDER, notebookResource),
        UnauthorizedException.class);
  }

  @Test
  void stopAwsSageMakerNotebook_NotResourceDeletion_doTest() throws InterruptedException {
    // not part of resource deletion
    StopAwsSageMakerNotebookStep stopNotebookStep =
        new StopAwsSageMakerNotebookStep(
            notebookResource, mockAwsCloudContextService, mockSamService(), false);

    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any()))
        .thenReturn(NotebookInstanceStatus.STOPPED)
        .thenReturn(NotebookInstanceStatus.DELETING)
        .thenReturn(NotebookInstanceStatus.PENDING);

    // success
    assertThat(
        stopNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    // error (not found)
    assertStepResultFatal(stopNotebookStep.doStep(mockFlightContext), NotFoundException.class);

    // error
    assertStepResultFatal(stopNotebookStep.doStep(mockFlightContext), ApiException.class);
  }

  @Test
  void stopAwsSageMakerNotebook_ResourceDeletion_doTest() throws InterruptedException {
    // part of resource deletion
    StopAwsSageMakerNotebookStep stopNotebookStep =
        new StopAwsSageMakerNotebookStep(
            notebookResource, mockAwsCloudContextService, mockSamService(), true);

    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any()))
        .thenReturn(NotebookInstanceStatus.DELETING);

    // success (not found)
    assertThat(
        stopNotebookStep.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));
  }

  private void stopAwsSageMakerNotebook_StoppedFailed_undoTest(
      StopAwsSageMakerNotebookStep stopNotebookStep, String message) throws InterruptedException {
    for (NotebookInstanceStatus status :
        List.of(NotebookInstanceStatus.STOPPED, NotebookInstanceStatus.FAILED)) {
      mockAwsUtils.when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any())).thenReturn(status);

      mockAwsUtils
          .when(() -> AwsUtils.startSageMakerNotebook(any(), any()))
          .thenAnswer(invocation -> null) /* success */
          .thenAnswer(invocation -> null) /* success */
          .thenThrow(API_EXCEPTION);
      mockAwsUtils
          .when(() -> AwsUtils.waitForSageMakerNotebookStatus(any(), any(), any()))
          .thenAnswer(invocation -> null) /* success */
          .thenThrow(UNAUTHORIZED_EXCEPTION);

      // start success, wait success
      assertThat(
          String.format(
              "undo stop expected to succeed on notebook with status: %s for %s", status, message),
          stopNotebookStep.undoStep(mockFlightContext),
          equalTo(StepResult.getStepResultSuccess()));

      // start success, wait error
      assertStepResultFatal(
          stopNotebookStep.undoStep(mockFlightContext),
          UnauthorizedException.class,
          String.format(
              "undo stop expected to fail on notebook with status: %s for %s", status, message));

      // start error
      assertStepResultFatal(
          stopNotebookStep.undoStep(mockFlightContext),
          ApiException.class,
          String.format(
              "undo stop expected to fail on notebook with status: %s for %s", status, message));
    }
  }

  private void stopAwsSageMakerNotebook_PendingUpdating_undoTest(
      StopAwsSageMakerNotebookStep stopNotebookStep, String message) throws InterruptedException {
    for (NotebookInstanceStatus status :
        List.of(NotebookInstanceStatus.PENDING, NotebookInstanceStatus.UPDATING)) {
      mockAwsUtils.when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any())).thenReturn(status);

      mockAwsUtils
          .when(() -> AwsUtils.waitForSageMakerNotebookStatus(any(), any(), any()))
          .thenAnswer(invocation -> null) /* success */
          .thenThrow(UNAUTHORIZED_EXCEPTION);

      // wait success
      assertThat(
          String.format(
              "undo stop expected to succeed on notebook with status: %s for %s", status, message),
          stopNotebookStep.undoStep(mockFlightContext),
          equalTo(StepResult.getStepResultSuccess()));

      // wait error
      assertStepResultFatal(
          stopNotebookStep.undoStep(mockFlightContext),
          UnauthorizedException.class,
          String.format(
              "undo stop expected to fail on notebook with status: %s for %s", status, message));
    }
  }

  private void stopAwsSageMakerNotebook_OtherError_undoTest(
      StopAwsSageMakerNotebookStep stopNotebookStep, String message) throws InterruptedException {
    for (NotebookInstanceStatus status :
        List.of(
            NotebookInstanceStatus.STOPPED,
            NotebookInstanceStatus.DELETING,
            NotebookInstanceStatus.UNKNOWN_TO_SDK_VERSION)) {
      mockAwsUtils.when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any())).thenReturn(status);

      // always error
      assertStepResultFatal(
          stopNotebookStep.undoStep(mockFlightContext),
          ApiException.class,
          String.format(
              "undo stop expected to fail on notebook with status: %s for %s", status, message));
    }
  }

  private void stopAwsSageMakerNotebook_InService_undoTest(
      StopAwsSageMakerNotebookStep stopNotebookStep, String message) throws InterruptedException {
    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any()))
        .thenReturn(NotebookInstanceStatus.IN_SERVICE);

    // always success (no-op)
    assertThat(
        "undo stop expected to succeed on notebook for" + message,
        stopNotebookStep.undoStep(mockFlightContext),
        equalTo(StepResult.getStepResultSuccess()));
  }

  private void stopAwsSageMakerNotebook_GetStatusError_undoTest(
      StopAwsSageMakerNotebookStep stopNotebookStep, String message) throws InterruptedException {
    mockAwsUtils
        .when(() -> AwsUtils.getSageMakerNotebookStatus(any(), any()))
        .thenThrow(NOT_FOUND_EXCEPTION);

    // error
    assertStepResultFatal(
        stopNotebookStep.undoStep(mockFlightContext),
        NotFoundException.class,
        "undo stop expected to fail on notebook for " + message);
  }

  @Test
  void stopAwsSageMakerNotebook_undoTest() throws InterruptedException {
    for (Boolean resourceDeletion :
        List.of(/*  part of resource deletion */ true, /* not part of resource deletion */ false)) {
      StopAwsSageMakerNotebookStep stopNotebookStep =
          new StopAwsSageMakerNotebookStep(
              notebookResource, mockAwsCloudContextService, mockSamService(), resourceDeletion);

      String message = "resourceDeletion: " + resourceDeletion;
      stopAwsSageMakerNotebook_StoppedFailed_undoTest(stopNotebookStep, message);
      stopAwsSageMakerNotebook_PendingUpdating_undoTest(stopNotebookStep, message);
      stopAwsSageMakerNotebook_OtherError_undoTest(stopNotebookStep, message);
      stopAwsSageMakerNotebook_InService_undoTest(stopNotebookStep, message);
      stopAwsSageMakerNotebook_GetStatusError_undoTest(stopNotebookStep, message);
    }
  }
}
