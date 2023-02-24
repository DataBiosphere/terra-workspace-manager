package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakernotebook;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.MultiCloudUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.model.Credentials;

/** A step for deleting a controlled SageMaker notebook instance. */
public class DeleteAwsSageMakerNotebookStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(DeleteAwsSageMakerNotebookStep.class);

  private final ControlledAwsSageMakerNotebookResource resource;

  public DeleteAwsSageMakerNotebookStep(ControlledAwsSageMakerNotebookResource resource) {
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap workingMap = flightContext.getWorkingMap();

    final String awsCloudContextString =
        workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT, String.class);

    final AwsCloudContext awsCloudContext = AwsCloudContext.deserialize(awsCloudContextString);
    final Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);

    String notebookName = resource.getInstanceId();
    Region region = Region.of(resource.getRegion());

    AwsUtils.deleteSageMakerNotebook(awsCredentials, region, notebookName);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo delete of AWS SageMaker Notebook instance {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
