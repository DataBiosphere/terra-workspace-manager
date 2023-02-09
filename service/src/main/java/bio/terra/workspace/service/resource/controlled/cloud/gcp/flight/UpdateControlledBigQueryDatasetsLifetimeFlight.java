package bio.terra.workspace.service.resource.controlled.cloud.gcp.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledBigQueryDatasetWithoutLifetimeStep;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledBigQueryDatasetsLifetimeStep;

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
        new RetrieveControlledBigQueryDatasetWithoutLifetimeStep(flightBeanBag.getResourceDao()),
        RetryRules.shortDatabase());

    addStep(
        new RetrieveControlledBigQueryDatasetLifetimeStep(flightBeanBag.getCrlService()),
        RetryRules.shortExponential());

    addStep(new UpdateControlledBigQueryDatasetsLifetimeStep(flightBeanBag.getResourceDao()));
  }
}
