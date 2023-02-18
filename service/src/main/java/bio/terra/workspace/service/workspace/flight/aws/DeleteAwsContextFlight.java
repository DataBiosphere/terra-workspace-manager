package bio.terra.workspace.service.workspace.flight.aws;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.exception.FeatureNotSupportedException;

public class DeleteAwsContextFlight extends Flight {
  public DeleteAwsContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    throw new FeatureNotSupportedException("DeleteAwsContextFlight not implemented");
  }
}
