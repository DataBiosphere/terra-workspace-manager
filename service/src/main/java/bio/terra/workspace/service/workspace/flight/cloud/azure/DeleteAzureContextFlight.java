package bio.terra.workspace.service.workspace.flight.cloud.azure;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;

public class DeleteAzureContextFlight extends Flight {
  public DeleteAzureContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);
    UUID workspaceUuid =
        UUID.fromString(inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    final AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    RetryRule retryRule = RetryRules.cloudLongRunning();

    addStep(
        new DeleteControlledAzureResourcesStep(
            appContext.getResourceDao(),
            appContext.getControlledResourceService(),
            appContext.getSamService(),
            workspaceUuid,
            userRequest));

    addStep(
        new DeleteAzureContextStep(appContext.getAzureCloudContextService(), workspaceUuid),
        retryRule);
  }
}
