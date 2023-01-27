package bio.terra.workspace.service.resource.controlled.cloud.gcp.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledBigQueryDatasetWithoutLifetimeStep;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledBigQueryDatasetsLifetimeStep;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateResourcesRegionStep;
import bio.terra.workspace.service.workspace.model.CloudPlatform;

// TODO (PF-2269): Clean this up once the back-fill is done in all Terra environments.
public class UpdateGcpControlledBigQueryDatasetsLifetimeFlight extends Flight {
  /**
   * All subclasses must provide a constructor with this signature.
   *
   * @param inputParameters FlightMap of the inputs for the flight
   * @param beanBag Anonymous context meaningful to the application using Stairway
   */
  public UpdateGcpControlledBigQueryDatasetsLifetimeFlight(
      FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);
    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);
    // Get BQ datasets without lifetime.
    addStep(
        new RetrieveControlledBigQueryDatasetWithoutLifetimeStep(
            CloudPlatform.GCP, flightBeanBag.getResourceDao()),
        RetryRules.shortDatabase());
    // Then retrieve the BQ datasets lifetime.
    addStep(
        new RetrieveGcpControlledBigQueryDatasetLifetimeStep(
            flightBeanBag.getCrlService(), flightBeanBag.getGcpCloudContextService()),
        RetryRules.shortExponential());

    addStep(new UpdateControlledBigQueryDatasetsLifetimeStep(flightBeanBag.getResourceDao()));
  }
}
