package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.NotFoundException;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.generated.model.ApiAwsSageMakerNotebookCreationParameters;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;

// Misc. connectedPlus tests
@Tag("aws-connected-plus")
public class AwsSageMakerNotebookFlightMiscTest extends BaseAwsSageMakerNotebookFlightTest {

  @Test
  void createSageMakerNotebook_undo_deletedTest() throws InterruptedException {
    ApiAwsSageMakerNotebookCreationParameters creationParameters =
        ControlledAwsResourceFixtures.makeAwsSageMakerNotebookCreationParameters(
            ControlledAwsResourceFixtures.getUniqueNotebookName());
    ControlledAwsSageMakerNotebookResource resource =
        ControlledAwsResourceFixtures.makeAwsSagemakerNotebookResource(
            workspaceUuid, creationParameters, userRequest.getEmail());

    // test idempotency of sagemaker-specific undo step by retrying once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        CreateAwsSageMakerNotebookStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);

    // final createResponseStep cannot fail, hence fail the create step
    Map<String, StepStatus> failureSteps = new HashMap<>();
    failureSteps.put(
        CreateAwsSageMakerNotebookStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_FATAL);

    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder()
            .doStepFailures(failureSteps)
            .undoStepFailures(retrySteps)
            .build());
    String jobId =
        controlledResourceService.createAwsSageMakerNotebookInstance(
            resource,
            creationParameters,
            environment,
            ControlledResourceIamRole.WRITER,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            "create-result-path",
            userRequest);
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.ERROR, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    // validate resource does not exist
    assertThrows(
        NotFoundException.class,
        () -> AwsUtils.getSageMakerNotebookStatus(awsCredentialsProvider, resource));
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                workspaceUuid, resource.getResourceId()));
  }

  @Test
  void forceDeleteSageMakerNotebook_undo_stillDeletedTest() throws InterruptedException {
    ApiAwsSageMakerNotebookCreationParameters creationParameters =
        ControlledAwsResourceFixtures.makeAwsSageMakerNotebookCreationParameters(
            ControlledAwsResourceFixtures.getUniqueNotebookName());
    ControlledAwsSageMakerNotebookResource resource =
        ControlledAwsResourceFixtures.makeAwsSagemakerNotebookResource(
            workspaceUuid, creationParameters, userRequest.getEmail());

    // create & verify resource status
    String jobId =
        controlledResourceService.createAwsSageMakerNotebookInstance(
            resource,
            creationParameters,
            environment,
            ControlledResourceIamRole.WRITER,
            new ApiJobControl().id(UUID.randomUUID().toString()),
            "create-result-path",
            userRequest);
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.SUCCESS, stairwayComponent.get().getFlightState(jobId).getFlightStatus());
    assertEquals(
        NotebookInstanceStatus.IN_SERVICE,
        AwsUtils.getSageMakerNotebookStatus(awsCredentialsProvider, resource));

    // None of the steps on this flight are undoable, so even with lastStepFailure set to true we
    // should expect the resource to really be deleted.
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().lastStepFailure(true).build());

    // delete resource
    jobId =
        controlledResourceService.deleteControlledResourceAsync(
            UUID.randomUUID().toString(),
            workspaceUuid,
            resource.getResourceId(),
            /* forceDelete= */ true,
            "force-delete-result-path",
            userRequest);
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.FATAL, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    // validate resource deleted
    assertThrows(
        NotFoundException.class,
        () -> AwsUtils.getSageMakerNotebookStatus(awsCredentialsProvider, resource));
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(
                workspaceUuid, resource.getResourceId()));
  }
}
