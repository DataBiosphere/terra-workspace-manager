package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.datareference.flight.GenerateReferenceIdStep;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.gcp.CreateGcsBucketStep;

/**
 * Flight for creation of a controlled resource. Some steps are resource-type-agnostic, and others
 * depend on the resource type. The latter must be passed in via the input parameters map with keys
 */
public class CreateControlledResourceFlight extends Flight {

  public CreateControlledResourceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(applicationContext);

    // Generate a new resource UUID
    addStep(new GenerateControlledResourceIdStep());
    // Genderate reference ID for the workspace_data_reference table
    addStep(new GenerateReferenceIdStep());

    // store the resource metadata in CloudSQL
    addStep(
        new StoreControlledResourceMetadataStep(
            flightBeanBag.getControlledResourceDao(), flightBeanBag.getDataReferenceDao()));

    final ControlledResource resource =
        inputParameters.get(JobMapKeys.REQUEST.getKeyName(), ControlledResource.class);

    switch (resource.getCloudPlatform()) {
      case GCP:
        switch (resource.getResourceType()) {
          case BUCKET:
            addStep(new CreateGcsBucketStep(flightBeanBag.getCrlService()));
            break;
          case BIGQUERY_DATASET:
          default:
            throw new IllegalStateException(
                String.format("Unrecognized resource type %s", resource.getResourceType()));
        }
        break;
      case AZURE:
      default:
        throw new IllegalStateException(
            String.format("Unrecognized cloud platform %s", resource.getCloudPlatform()));
    }
    // Step 2: create the cloud resource via CRL
    // Step 3: create the Sam resource associated with the resource

    // Step 4: assign custom roles to the resource based on Sam policies
    // TODO: can this step be the same for all resource types?
  }
}
