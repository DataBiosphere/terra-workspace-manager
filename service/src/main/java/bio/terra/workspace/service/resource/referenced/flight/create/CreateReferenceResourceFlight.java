package bio.terra.workspace.service.resource.referenced.flight.create;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;

public class CreateReferenceResourceFlight extends Flight {

  public CreateReferenceResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(beanBag);

    // Perform access verification
    addStep(new ValidateReferenceStep(appContext));

    // If all is well, then store the reference metadata
    addStep(new CreateReferenceMetadataStep(appContext.getResourceDao()));
  }
}
