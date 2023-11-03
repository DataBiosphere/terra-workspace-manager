package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.generated.model.ApiAwsSageMakerNotebookCreationParameters;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CommonUpdateParameters;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;

// Basic connected tests
@Tag("aws-connected-plus")
public class AwsSageMakerNotebookFlightTest extends BaseAwsSageMakerNotebookFlightTest {

  @Test
  void createGetUpdateStartStopDeleteSageMakerNotebookTest() throws InterruptedException {
    ApiAwsSageMakerNotebookCreationParameters creationParameters =
        ControlledAwsResourceFixtures.makeAwsSageMakerNotebookCreationParameters(
            ControlledAwsResourceFixtures.getUniqueNotebookName());
    ControlledAwsSageMakerNotebookResource resource =
        ControlledAwsResourceFixtures.makeAwsSagemakerNotebookResource(
            workspaceUuid, creationParameters, userRequest.getEmail());

    // create resource
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

    // verify resource created
    assertEquals(
        NotebookInstanceStatus.IN_SERVICE,
        AwsUtils.getSageMakerNotebookStatus(awsCredentialsProvider, resource));
    ControlledAwsSageMakerNotebookResource fetchedResource =
        controlledResourceService
            .getControlledResource(workspaceUuid, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_AWS_SAGEMAKER_NOTEBOOK);
    assertTrue(resource.partialEqual(fetchedResource));
    assertEquals(resource.getInstanceName(), fetchedResource.getInstanceName());

    // update resource
    CommonUpdateParameters updateParameters =
        new CommonUpdateParameters().setName("updatedName").setDescription("updated description");
    wsmResourceService.updateResource(userRequest, fetchedResource, updateParameters, null);

    // verify resource updated
    ControlledAwsSageMakerNotebookResource updatedResource =
        controlledResourceService
            .getControlledResource(workspaceUuid, fetchedResource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_AWS_SAGEMAKER_NOTEBOOK);
    assertEquals(updateParameters.getName(), updatedResource.getName());
    assertEquals(updateParameters.getDescription(), updatedResource.getDescription());

    // stop, wait & verify resource status
    assertDoesNotThrow(() -> AwsUtils.stopSageMakerNotebook(awsCredentialsProvider, resource));
    AwsUtils.waitForSageMakerNotebookStatus(
        awsCredentialsProvider, updatedResource, NotebookInstanceStatus.STOPPED);
    assertEquals(
        NotebookInstanceStatus.STOPPED,
        AwsUtils.getSageMakerNotebookStatus(awsCredentialsProvider, resource));

    // stop resource again (implementation function NOT idempotent)
    Exception ex =
        assertThrows(
            BadRequestException.class,
            () -> AwsUtils.stopSageMakerNotebook(awsCredentialsProvider, resource));
    assertTrue(ex.getMessage().contains("Unable to perform resource lifecycle operation"));
    assertEquals(
        NotebookInstanceStatus.STOPPED,
        AwsUtils.getSageMakerNotebookStatus(awsCredentialsProvider, resource));

    // start, wait & verify resource status
    assertDoesNotThrow(() -> AwsUtils.startSageMakerNotebook(awsCredentialsProvider, resource));
    AwsUtils.waitForSageMakerNotebookStatus(
        awsCredentialsProvider, updatedResource, NotebookInstanceStatus.IN_SERVICE);
    assertEquals(
        NotebookInstanceStatus.IN_SERVICE,
        AwsUtils.getSageMakerNotebookStatus(awsCredentialsProvider, resource));

    // start resource again (implementation function NOT idempotent)
    assertThrows(
        BadRequestException.class,
        () -> AwsUtils.startSageMakerNotebook(awsCredentialsProvider, resource));
    assertTrue(ex.getMessage().contains("Unable to perform resource lifecycle operation"));
    assertEquals(
        NotebookInstanceStatus.IN_SERVICE,
        AwsUtils.getSageMakerNotebookStatus(awsCredentialsProvider, resource));

    // delete resource fails without stop
    jobId =
        controlledResourceService.deleteControlledResourceAsync(
            UUID.randomUUID().toString(),
            workspaceUuid,
            fetchedResource.getResourceId(),
            /* forceDelete= */ false,
            "delete-without-stop-result-path",
            userRequest);
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.FATAL, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    // stop, wait & delete resource
    assertDoesNotThrow(() -> AwsUtils.stopSageMakerNotebook(awsCredentialsProvider, resource));
    AwsUtils.waitForSageMakerNotebookStatus(
        awsCredentialsProvider, updatedResource, NotebookInstanceStatus.STOPPED);
    jobId =
        controlledResourceService.deleteControlledResourceAsync(
            UUID.randomUUID().toString(),
            workspaceUuid,
            fetchedResource.getResourceId(),
            /* forceDelete= */ false,
            "delete-after-stop-result-path",
            userRequest);
    jobService.waitForJob(jobId);
    assertEquals(
        FlightStatus.SUCCESS, stairwayComponent.get().getFlightState(jobId).getFlightStatus());

    // verify resource deleted
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
