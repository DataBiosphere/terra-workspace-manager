package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidateAwsS3StorageFolderCreationStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(ValidateAwsS3StorageFolderCreationStep.class);
  private final ControlledAwsS3StorageFolderResource resource;

  private final AwsCloudContextService awsCloudContextService;

  public ValidateAwsS3StorageFolderCreationStep(
      ControlledAwsS3StorageFolderResource resource,
      AwsCloudContextService awsCloudContextService) {
    this.resource = resource;
    this.awsCloudContextService = awsCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputParameters = flightContext.getInputParameters();
    /*
        // TODO-Dex: validate creationParameters: Name & region
    // TODO-Dex: validate folder exists
        ApiAwsS3StorageFolderCreationParameters creationParameters =
            flight
                .getInputParameters()
                .get(
                    ControlledResourceKeys.CREATION_PARAMETERS,
                    ApiAwsS3StorageFolderCreationParameters.class);


        // TODO: get default region from user profile

        // TODO-Dex: validate creationParameters: Name & region
        Region region = Region.of(creationParameters.getRegion());

        AwsCloudContext awsCloudContext = FlightBeanBag.

            flightBeanBag.getAwsCloudContextService().getLandingZone(aws)
        LandingZone landingZone =



        // TODO-Dex: validate folder exists

        // Discovery discovery = awsConfiguration.getDiscovery();

        // flight.addStep(new ValidateAwsS3BucketCreationStep(this), cloudRetry);
        // flight.addStep(new CreateAwsS3BucketStep(this), cloudRetry);

        // throw new MissingRequiredFieldException(
        //  "Missing required field datasetName for BigQuery dataset");

        final AwsCloudContext awsCloudContext =
            FlightUtils.getRequired(
                flightContext.getWorkingMap(),
                ControlledResourceKeys.AWS_CLOUD_CONTEXT,
                AwsCloudContext.class);

        /*
        Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);
        AwsUtils.deleteFolder(
            awsCredentials,
            Region.of(resource.getRegion()),
            resource.getS3BucketName(),
            resource.getPrefix());
        // TODO-Dex
         */

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo delete of AWS S3 Storage Folder resource {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
