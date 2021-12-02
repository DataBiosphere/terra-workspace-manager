package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.CheckControlledResourceAuthStep;
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
    // 1. Validate user has read access to the source object
    // 2. Gather controlled resource metadata for source object
    // 3. Gather creation parameters from existing object
    // 4. Launch sub-flight to create appropriate resource
    // Steps 5-9 are for resource clone only
    // 5. Set bucket roles for cloning service account
    // 6. Create Storage Transfer Service transfer job
    // 7. Listen for running operation in transfer job
    // 8. Delete the storage transfer job
    // 9. Clear bucket roles
    addStep(
        new CheckControlledResourceAuthStep(
            sourceResource, flightBeanBag.getControlledResourceMetadataManager(), userRequest),
        RetryRules.shortExponential());
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
            flightBeanBag.getGcpCloudContextService(),
            RetrievalMode.CREATION_PARAMETERS));
    addStep(
        new CopyGcsBucketDefinitionStep(
            userRequest, sourceBucket, flightBeanBag.getControlledResourceService()));
    addStep(
        new SetBucketRolesStep(
            sourceBucket,
            flightBeanBag.getGcpCloudContextService(),
            flightBeanBag.getBucketCloneRolesComponent()));
    addStep(new CreateStorageTransferServiceJobStep());
    addStep(new CompleteTransferOperationStep());
    addStep(new DeleteStorageTransferServiceJobStep());
    addStep(new RemoveBucketRolesStep(flightBeanBag.getBucketCloneRolesComponent()));
  }
}
