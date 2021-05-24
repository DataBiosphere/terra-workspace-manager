package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;

/** A {@link Flight} for deleting a Google cloud context for a workspace. */
public class DeleteGcpContextFlight extends Flight {

  public DeleteGcpContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);

    RetryRule retryRule = RetryRules.cloudLongRunning();

    addStep(
        new DeleteProjectStep(appContext.getCrlService(), appContext.getWorkspaceDao()), retryRule);
    addStep(new DeleteGcpContextStep(appContext.getWorkspaceDao()), retryRule);
  }
}
