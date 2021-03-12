package bio.terra.workspace.service.resource.referenced.flight.create;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;

public class CreateReferenceResourceFlight extends Flight {

  public CreateReferenceResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(beanBag);

    // Perform access verification separately by resource type
    WsmResourceType resourceType =
        inputParameters.get(
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_TYPE, WsmResourceType.class);
    switch (resourceType) {
      case BIG_QUERY_DATASET:
        addStep(new CreateReferenceVerifyAccessBigQueryDatasetStep(appContext.getCrlService()));
        break;

      case DATA_REPO_SNAPSHOT:
        addStep(
            new CreateReferenceVerifyAccessDataRepoSnapshotStep(appContext.getDataRepoService()));
        break;

      case GCS_BUCKET:
        addStep(new CreateReferenceVerifyAccessGcsBucketStep(appContext.getCrlService()));
        break;

      default:
        throw new InternalLogicException("Unknown resource type");
    }

    // If all is well, then store the reference metadata
    addStep(new CreateReferenceMetadataStep(appContext.getResourceDao()));
  }
}
