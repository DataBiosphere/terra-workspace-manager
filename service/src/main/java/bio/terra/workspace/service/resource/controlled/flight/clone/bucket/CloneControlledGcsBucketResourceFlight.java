package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
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
    // Steps 4-8 are for resource clone only
    // 4. Set bucket roles for cloning service account
    // 5. Create Storage Transfer Service transfer job
    // 6. Listen for running operation in transfer job
    // 7. Delete the storage transfer job
    // 8. Clear bucket roles
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
            sourceBucket,
            flightBeanBag.getWorkspaceService(),
            flightBeanBag.getBucketCloneRolesService()));
    addStep(new CreateStorageTransferServiceJobStep());
    addStep(new CompleteTransferOperationStep());
    addStep(new DeleteStorageTransferServiceJobStep());
    addStep(new RemoveBucketRolesStep(flightBeanBag.getBucketCloneRolesService()));
  }
}
