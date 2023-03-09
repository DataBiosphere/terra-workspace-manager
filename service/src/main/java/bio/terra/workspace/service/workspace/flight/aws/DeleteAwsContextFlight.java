package bio.terra.workspace.service.workspace.flight.aws;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import java.util.*;

public class DeleteAwsContextFlight extends Flight {

  public DeleteAwsContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    // Sanity check to make sure AWS is enabled before kicking off flight
    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);
    var featureConfiguration = appContext.getFeatureConfiguration();
    featureConfiguration.awsEnabledCheck();

    throw new FeatureNotSupportedException("DeleteAwsContextFlight");
  }
}
