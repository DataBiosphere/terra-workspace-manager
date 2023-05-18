package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;

public class SignedUrlListDataTransferFlight extends Flight {

  /**
   * All subclasses must provide a constructor with this signature.
   *
   * @param inputParameters FlightMap of the inputs for the flight
   * @param applicationContext Anonymous context meaningful to the application using Stairway
   */
  public SignedUrlListDataTransferFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(applicationContext);

    RetryRule cloudRetry = RetryRules.cloud();
    addStep(
        new RetrieveDataTransferMetadataStep(
            flightBeanBag.getStoragetransfer(), flightBeanBag.getGcpCloudContextService(), null));
    addStep(new SetBucketRolesStep(flightBeanBag.getBucketCloneRolesService()), cloudRetry);
    addStep(new TransferSignedUrlsToGcsBucketStep(flightBeanBag.getStoragetransfer()), cloudRetry);
    addStep(new CompleteTransferOperationStep(flightBeanBag.getStoragetransfer()), cloudRetry);
    addStep(
        new DeleteStorageTransferServiceJobStep(flightBeanBag.getStoragetransfer()), cloudRetry);
    addStep(new RemoveBucketRolesStep(flightBeanBag.getBucketCloneRolesService()), cloudRetry);
  }
}
