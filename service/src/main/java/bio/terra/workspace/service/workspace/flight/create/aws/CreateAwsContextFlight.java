package bio.terra.workspace.service.workspace.flight.create.aws;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;

public class CreateAwsContextFlight extends Flight {

  public CreateAwsContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    // Sanity check to make sure AWS is enabled before kicking off flight
    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);
    var featureConfiguration = appContext.getFeatureConfiguration();
    featureConfiguration.awsEnabledCheck();

    UUID workspaceUuid =
        UUID.fromString(inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    RetryRule dbRetry = RetryRules.shortDatabase();

    // write the incomplete DB row to prevent concurrent creates
    addStep(
        new CreateDbAwsCloudContextStartStep(workspaceUuid, appContext.getAwsCloudContextService()),
        dbRetry);

    // Basic WLZ sanity checks
    // TODO: retry?
    // addStep(new ValidateMRGStep(appContext.getCrlService(), appContext.getAzureConfig()));

    // update the DB row filling in the cloud context
    addStep(
        new CreateDbAwsCloudContextFinishStep(
            workspaceUuid, appContext.getAwsCloudContextService()),
        dbRetry);
  }
}
