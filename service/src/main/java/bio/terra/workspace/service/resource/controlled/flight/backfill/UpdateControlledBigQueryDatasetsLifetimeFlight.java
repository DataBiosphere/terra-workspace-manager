package bio.terra.workspace.service.resource.controlled.flight.backfill;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;

// TODO (PF-2269): Clean this up once the back-fill is done in all Terra environments.
public class UpdateControlledBigQueryDatasetsLifetimeFlight extends Flight {
  /**
   * All subclasses must provide a constructor with this signature.
   *
   * @param inputParameters FlightMap of the inputs for the flight
   * @param beanBag Anonymous context meaningful to the application using Stairway
   */
  public UpdateControlledBigQueryDatasetsLifetimeFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);
    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);

    addStep(
        new RetrieveControlledBigQueryDatasetsWithoutLifetimeStep(flightBeanBag.getResourceDao()));

    addStep(
        new RetrieveControlledBigQueryDatasetsLifetimeStep(flightBeanBag.getCrlService()),
        RetryRules.shortExponential());

    addStep(new UpdateControlledBigQueryDatasetsLifetimeStep(flightBeanBag.getResourceDao()));
  }
}
