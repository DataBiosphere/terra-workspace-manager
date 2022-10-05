package bio.terra.workspace.service.workspace.flight.create.azure;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.CheckSpendProfileStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.UUID;

/**
 * A {@link Flight} for creating a Google cloud context for a workspace using Buffer Service to
 * create the project.
 */
public class CreateAzureContextFlight extends Flight {

  public CreateAzureContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);

    UUID workspaceUuid =
        UUID.fromString(inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    RetryRule dbRetry = RetryRules.shortDatabase();

    // check that we are allowed to link to this spend profile
    if (appContext.getFeatureConfiguration().isBpmEnabled()) {
      addStep(
          new CheckSpendProfileStep(
              appContext.getWorkspaceDao(),
              appContext.getSpendProfileService(),
              workspaceUuid,
              userRequest,
              CloudPlatform.AZURE));
    }

    // 0. Write the incomplete DB row to prevent concurrent creates
    addStep(
        new CreateDbAzureCloudContextStartStep(
            workspaceUuid, appContext.getAzureCloudContextService()),
        dbRetry);

    // 1. validate the MRG
    // TODO: retry?
    addStep(new ValidateMRGStep(appContext.getCrlService(), appContext.getAzureConfig()));

    // 2. Update the DB row filling in the cloud context
    var featureConfiguration = appContext.getFeatureConfiguration();
    addStep(
        new CreateDbAzureCloudContextFinishStep(
            workspaceUuid, appContext.getAzureCloudContextService(), featureConfiguration),
        dbRetry);
  }
}
