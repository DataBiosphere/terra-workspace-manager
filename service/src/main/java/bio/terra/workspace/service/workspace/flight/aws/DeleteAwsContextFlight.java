package bio.terra.workspace.service.workspace.flight.aws;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;

public class DeleteAwsContextFlight extends Flight {

  public DeleteAwsContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);
    UUID workspaceUuid =
        UUID.fromString(inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    RetryRule retryRule = RetryRules.cloudLongRunning();

    addStep(
        new DeleteControlledAwsResourcesStep(
            appContext.getControlledResourceService(), workspaceUuid, userRequest));

    addStep(
        new DeleteAwsContextStep(appContext.getAwsCloudContextService(), workspaceUuid), retryRule);
  }
}
