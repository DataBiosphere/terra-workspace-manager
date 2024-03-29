package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.SamUser;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.CliConfiguration;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import java.util.Collection;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;
import software.amazon.awssdk.services.sts.model.Tag;

public class CreateAwsSageMakerNotebookStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(CreateAwsSageMakerNotebookStep.class);

  private final ControlledAwsSageMakerNotebookResource resource;
  private final AwsCloudContextService awsCloudContextService;
  private final SamService samService;
  private final CliConfiguration cliConfiguration;

  public CreateAwsSageMakerNotebookStep(
      ControlledAwsSageMakerNotebookResource resource,
      AwsCloudContextService awsCloudContextService,
      SamService samService,
      CliConfiguration cliConfiguration) {
    this.resource = resource;
    this.awsCloudContextService = awsCloudContextService;
    this.samService = samService;
    this.cliConfiguration = cliConfiguration;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputParameters = flightContext.getInputParameters();
    SamUser samUser = FlightUtils.getRequiredSamUser(inputParameters, samService);
    AwsCloudContext cloudContext =
        awsCloudContextService.getRequiredAwsCloudContext(resource.getWorkspaceId());

    Collection<Tag> tags = new HashSet<>();
    AwsUtils.appendUserTags(tags, samUser);
    AwsUtils.appendResourceTags(tags, cloudContext, resource);
    tags.add(Tag.builder().key("CliServerName").value(cliConfiguration.getServerName()).build());

    AwsCredentialsProvider credentialsProvider =
        AwsUtils.createWsmCredentialProvider(
            awsCloudContextService.getRequiredAuthentication(),
            awsCloudContextService.discoverEnvironment(samUser.getEmail()));

    try {
      AwsUtils.createSageMakerNotebook(
          credentialsProvider,
          resource,
          Arn.fromString(
              FlightUtils.getRequired(
                  inputParameters,
                  ControlledResourceKeys.AWS_ENVIRONMENT_NOTEBOOK_ROLE_ARN,
                  String.class)),
          Arn.fromString(
              FlightUtils.getRequired(
                  inputParameters,
                  ControlledResourceKeys.AWS_LANDING_ZONE_KMS_KEY_ARN,
                  String.class)),
          Arn.fromString(
              FlightUtils.getRequired(
                  inputParameters,
                  ControlledResourceKeys.AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN,
                  String.class)),
          tags);
      AwsUtils.waitForSageMakerNotebookStatus(
          credentialsProvider, resource, NotebookInstanceStatus.IN_SERVICE);

    } catch (ApiException | NotFoundException | UnauthorizedException | BadRequestException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    AwsCredentialsProvider credentialsProvider =
        AwsUtils.createWsmCredentialProvider(
            awsCloudContextService.getRequiredAuthentication(),
            awsCloudContextService.discoverEnvironment(
                FlightUtils.getRequiredUserEmail(flightContext.getInputParameters(), samService)));

    try {
      StepResult result =
          StopAwsSageMakerNotebookStep.executeStopAwsSageMakerNotebook(
              credentialsProvider, resource);

      return result.isSuccess()
          ? DeleteAwsSageMakerNotebookStep.executeDeleteAwsSageMakerNotebook(
              credentialsProvider, resource)
          : result;

    } catch (NotFoundException e) {
      logger.debug("No notebook instance {} to delete.", resource.getName());
    }

    return StepResult.getStepResultSuccess();
  }
}
