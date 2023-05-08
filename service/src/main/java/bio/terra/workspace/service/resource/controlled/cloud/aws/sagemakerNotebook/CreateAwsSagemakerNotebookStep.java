package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakerNotebook;

import bio.terra.common.iam.SamUser;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.generated.model.ApiAwsSagemakerNotebookCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import java.util.Collection;
import java.util.HashSet;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.sts.model.Tag;

public class CreateAwsSagemakerNotebookStep implements Step {

  private final ControlledAwsSagemakerNotebookResource resource;
  private final AwsCloudContextService awsCloudContextService;
  private final AuthenticatedUserRequest userRequest;
  private final SamService samService;

  public CreateAwsSagemakerNotebookStep(
      ControlledAwsSagemakerNotebookResource resource,
      AwsCloudContextService awsCloudContextService,
      AuthenticatedUserRequest userRequest,
      SamService samService) {
    this.resource = resource;
    this.awsCloudContextService = awsCloudContextService;
    this.userRequest = userRequest;
    this.samService = samService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    AwsCloudContext cloudContext =
        awsCloudContextService.getRequiredAwsCloudContext(resource.getWorkspaceId());

    AwsCredentialsProvider credentialsProvider =
        AwsUtils.createWsmCredentialProvider(
            awsCloudContextService.getRequiredAuthentication(),
            awsCloudContextService.discoverEnvironment());

    SamUser samUser = samService.getSamUser(userRequest);
    Collection<Tag> tags = new HashSet<>();
    AwsUtils.appendUserTags(tags, samUser);
    AwsUtils.appendResourceTags(tags, cloudContext);

    FlightMap inputParameters = flightContext.getInputParameters();
    // TODO(TERRA-550): creationParameters may be used later
    ApiAwsSagemakerNotebookCreationParameters creationParameters =
        inputParameters.get(
            ControlledResourceKeys.CREATE_NOTEBOOK_PARAMETERS,
            ApiAwsSagemakerNotebookCreationParameters.class);

    AwsUtils.createSageMakerNotebook(
        credentialsProvider,
        resource,
        Arn.fromString(
            inputParameters.get(
                ControlledResourceKeys.AWS_ENVIRONMENT_USER_ROLE_ARN, String.class)),
        Arn.fromString(
            inputParameters.get(ControlledResourceKeys.AWS_LANDING_ZONE_KMS_KEY_ARN, String.class)),
        Arn.fromString(
            inputParameters.get(
                ControlledResourceKeys.AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN,
                String.class)),
        tags);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // TODO(TERRA-312) add steps to stop, delete
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
  }
}
