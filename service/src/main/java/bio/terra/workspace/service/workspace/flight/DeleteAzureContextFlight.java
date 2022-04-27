package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import java.util.UUID;

public class DeleteAzureContextFlight extends Flight {
  public DeleteAzureContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);
    UUID workspaceUuid =
        UUID.fromString(inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));

    RetryRule retryRule = RetryRules.cloudLongRunning();

    // TODO cleanup any azure resources here

    addStep(
        new DeleteAzureContextStep(appContext.getAzureCloudContextService(), workspaceUuid),
        retryRule);
  }
}
