package bio.terra.workspace.service.workspace.flight.aws;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;

public class CreateAwsContextFlightV1 extends Flight {

  public CreateAwsContextFlightV1(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);
    var featureConfiguration = appContext.getFeatureConfiguration();
    featureConfiguration.awsEnabledCheck();

    UUID workspaceUuid =
        UUID.fromString(inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));

    RetryRule dbRetry = RetryRules.shortDatabase();

    // write the incomplete DB row to prevent concurrent creates
    addStep(
        new CreateDbAwsCloudContextStartStep(workspaceUuid, appContext.getAwsCloudContextService()),
        dbRetry);

    // Basic WLZ sanity checks
    addStep(new ValidateWLZStep());

    // update the DB row filling in the cloud context
    addStep(
        new CreateDbAwsCloudContextFinishStep(
            workspaceUuid, appContext.getAwsCloudContextService()),
        dbRetry);
  }
}
