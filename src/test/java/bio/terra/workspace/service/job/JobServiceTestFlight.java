package bio.terra.workspace.service.job;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import org.jetbrains.annotations.NotNull;

public class JobServiceTestFlight extends Flight {

  public JobServiceTestFlight(@NotNull FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    // Pull out our parameters and feed them in to the step classes.
    String description = inputParameters.get("description", String.class);

    // Just one step for this test
    addStep(new JobServiceTestStep(description));
  }
}
