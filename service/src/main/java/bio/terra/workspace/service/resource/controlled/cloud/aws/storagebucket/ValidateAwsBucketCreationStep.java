package bio.terra.workspace.service.resource.controlled.cloud.aws.storagebucket;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiAwsBucketCreationParameters;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import com.amazonaws.regions.Regions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ValidateAwsBucketCreationStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(ValidateAwsBucketCreationStep.class);

  private final ControlledAwsBucketResource resource;

  ValidateAwsBucketCreationStep(ControlledAwsBucketResource resource) {
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final String awsCloudContextString =
        flightContext
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT, String.class);

    final AwsCloudContext awsCloudContext = AwsCloudContext.deserialize(awsCloudContextString);

    FlightMap inputMap = flightContext.getInputParameters();
    FlightUtils.validateRequiredEntries(
        inputMap, WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS);

    ApiAwsBucketCreationParameters creationParameters =
        inputMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS,
            ApiAwsBucketCreationParameters.class);

    Regions requestedRegion;
    try {
      requestedRegion = Regions.fromName(creationParameters.getLocation());
    } catch (IllegalArgumentException e) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new BadRequestException(
              String.format(
                  "Region '%s' is not a valid AWS region.", creationParameters.getLocation())));
    }
    String bucketName = awsCloudContext.getBucketNameForRegion(requestedRegion);

    if (bucketName == null) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new NotFoundException(
              String.format(
                  "Configured AWS Landing zone does not support S3 buckets in region '%s'.",
                  requestedRegion)));
    }

    //    resource.setS3BucketName(bucketName);
    //
    //    UUID resourceId = resource.getResourceId();
    //    resource.setPrefix(resourceId.toString());

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
