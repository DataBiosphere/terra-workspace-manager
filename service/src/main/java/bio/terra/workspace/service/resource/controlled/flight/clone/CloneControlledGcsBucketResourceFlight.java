package bio.terra.workspace.service.resource.controlled.flight.clone;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.CopyGcsBucketDataStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.CopyGcsBucketDefinitionStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.SetBucketRolesStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveGcsBucketCloudAttributesStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveGcsBucketCloudAttributesStep.RetrievalMode;

public class CloneControlledGcsBucketResourceFlight extends Flight {

  public CloneControlledGcsBucketResourceFlight(
      FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(applicationContext);
    final ControlledResource sourceResource =
        inputParameters.get(JobMapKeys.REQUEST.getKeyName(), ControlledResource.class);
    final AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    // Flight Plan
    // 1. Gather controlled resource metadata for source object
    // 2. Gather creation parameters from existing object
    // 3. Launch sub-flight to create appropriate resource
    // 4. Set roles for cloning service account
    // 5. (for resource clone) Clone Data
    // 6. Copy data across resources
    //

    addStep(
        new RetrieveControlledResourceMetadataStep(
            flightBeanBag.getResourceDao(),
            sourceResource.getWorkspaceId(),
            sourceResource.getResourceId()));
    final ControlledGcsBucketResource sourceBucket = sourceResource.castToGcsBucketResource();
    addStep(
        new RetrieveGcsBucketCloudAttributesStep(
            sourceBucket,
            flightBeanBag.getCrlService(),
            flightBeanBag.getWorkspaceService(),
            RetrievalMode.CREATION_PARAMETERS));
    addStep(
        new CopyGcsBucketDefinitionStep(
            userRequest, sourceBucket, flightBeanBag.getControlledResourceService()));
    addStep(
        new SetBucketRolesStep(
            sourceBucket, flightBeanBag.getCrlService(), flightBeanBag.getWorkspaceService(),
            flightBeanBag.getBucketCloneRolesService()));
    addStep(new CopyGcsBucketDataStep());
  }
}
