package bio.terra.workspace.service.resource.referenced.flight.update;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResource;
import bio.terra.workspace.service.resource.referenced.flight.create.ValidateReferenceStep;
import java.sql.Ref;

public class UpdateReferenceResourceFlight extends Flight {

  /**
   * Flight to update a reference resource target.
   *
   * @param inputParameters FlightMap of the inputs for the flight
   * @param beanBag Anonymous context meaningful to the application using Stairway
   */
  public UpdateReferenceResourceFlight(FlightMap inputParameters,
      Object beanBag) {
    super(inputParameters, beanBag);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(beanBag);
    final ReferencedResource resource =
        inputParameters.get(JobMapKeys.REQUEST.getKeyName(), ReferencedResource.class);

    // Perform access verification
    addStep(new ValidateReferenceStep(appContext), RetryRules.cloud());

    addStep(new RetrieveReferenceMetadataStep(
        appContext.getResourceDao(), resource.getWorkspaceId(), resource.getResourceId()),
        RetryRules.shortDatabase());

    addStep(new UpdateReferenceMetadataStep(appContext.getResourceDao(), resource), RetryRules.shortDatabase());
  }
}
