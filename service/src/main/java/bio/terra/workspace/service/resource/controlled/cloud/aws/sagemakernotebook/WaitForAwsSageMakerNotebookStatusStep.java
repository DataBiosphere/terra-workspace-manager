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
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;
import software.amazon.awssdk.services.sts.model.Credentials;

public class WaitForAwsSageMakerNotebookStatusStep implements Step {

  private static final Logger logger =
      LoggerFactory.getLogger(WaitForAwsSageMakerNotebookStatusStep.class);

  private final ControlledAwsSageMakerNotebookResource resource;
  private final NotebookInstanceStatus notebookStatus;

  public WaitForAwsSageMakerNotebookStatusStep(
      ControlledAwsSageMakerNotebookResource resource, NotebookInstanceStatus notebookStatus) {
    this.resource = resource;
    this.notebookStatus = notebookStatus;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap workingMap = flightContext.getWorkingMap();

    AwsCloudContext awsCloudContext =
        workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT, AwsCloudContext.class);
    Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);

    AwsUtils.waitForSageMakerNotebookStatus(
        awsCredentials, Region.of(resource.getRegion()), resource.getInstanceId(), notebookStatus);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
