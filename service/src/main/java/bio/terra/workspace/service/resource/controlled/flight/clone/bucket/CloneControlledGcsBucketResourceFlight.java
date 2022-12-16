package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.RetrieveGcsBucketCloudAttributesStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.RetrieveGcsBucketCloudAttributesStep.RetrievalMode;
import bio.terra.workspace.service.resource.controlled.flight.clone.CheckControlledResourceAuthStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.ClonePolicyAttributesStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.flight.create.CreateReferenceMetadataStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.Optional;
import java.util.UUID;

// Flight Plan
// 0. If cloning instructions resolve to COPY_NOTHING, exit without any further steps.
// 1. Validate user has read access to the source object
// 2. Gather controlled resource metadata for source object
// 3. Gather creation parameters from existing object
// 4. Launch sub-flight to create appropriate resource
// 5. If cloning to referenced resource, do the clone and finish flight.
// Steps 6-10 are for cloning to controlled resource only
// 6. Set bucket roles for cloning service account
// 7. Create Storage Transfer Service transfer job
// 8. Listen for running operation in transfer job
// 9. Delete the storage transfer job
// 10. Clear bucket roles
public class CloneControlledGcsBucketResourceFlight extends Flight {

  public CloneControlledGcsBucketResourceFlight(
      FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    FlightUtils.validateRequiredEntries(
        inputParameters,
        ResourceKeys.RESOURCE,
        JobMapKeys.AUTH_USER_INFO.getKeyName(),
        ControlledResourceKeys.DESTINATION_RESOURCE_ID);

    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(applicationContext);
    var sourceResource = inputParameters.get(ResourceKeys.RESOURCE, ControlledResource.class);
    var userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    var destinationWorkspaceId =
        inputParameters.get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

    boolean mergePolicies =
        Optional.ofNullable(
                inputParameters.get(WorkspaceFlightMapKeys.MERGE_POLICIES, Boolean.class))
            .orElse(false);
    ControlledGcsBucketResource sourceBucket =
        sourceResource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    CloningInstructions resolvedCloningInstructions =
        Optional.ofNullable(
                inputParameters.get(
                    ControlledResourceKeys.CLONING_INSTRUCTIONS, CloningInstructions.class))
            .orElse(sourceBucket.getCloningInstructions());

    RetryRule cloudRetry = RetryRules.cloud();
    // We can't put the cloning instructions into the working map, because it's not available
    // from within a flight constructor. Instead, pass it in to the constructors of the steps
    // that need it.
    if (CloningInstructions.COPY_NOTHING == resolvedCloningInstructions) {
      addStep(new SetNoOpBucketCloneResponseStep(sourceBucket));
    } else {
      addStep(
          new CheckControlledResourceAuthStep(
              sourceResource, flightBeanBag.getControlledResourceMetadataManager(), userRequest),
          RetryRules.shortExponential());
      if (mergePolicies) {
        addStep(
            new ClonePolicyAttributesStep(
                sourceResource.getWorkspaceId(),
                destinationWorkspaceId,
                userRequest,
                flightBeanBag.getTpsApiDispatch()));
      }
      addStep(
          new RetrieveControlledResourceMetadataStep(
              flightBeanBag.getResourceDao(),
              sourceResource.getWorkspaceId(),
              sourceResource.getResourceId()),
          RetryRules.shortDatabase());
      addStep(
          new RetrieveGcsBucketCloudAttributesStep(
              sourceBucket,
              flightBeanBag.getCrlService(),
              flightBeanBag.getGcpCloudContextService(),
              RetrievalMode.CREATION_PARAMETERS),
          cloudRetry);

      if (CloningInstructions.COPY_REFERENCE == resolvedCloningInstructions) {
        // Destination bucket is referenced resource
        addStep(
            new SetReferencedDestinationGcsBucketInWorkingMapStep(
                flightBeanBag.getSamService(),
                userRequest,
                sourceBucket,
                flightBeanBag.getReferencedResourceService(),
                resolvedCloningInstructions),
            RetryRules.shortExponential());
        addStep(
            new CreateReferenceMetadataStep(flightBeanBag.getResourceDao()),
            RetryRules.shortDatabase());
        addStep(
            new SetReferencedDestinationGcsBucketResponseStep(
                flightBeanBag.getResourceDao(), flightBeanBag.getWorkspaceActivityLogService()),
            RetryRules.shortExponential());
        return;
      }
      // Destination bucket is controlled resource
      addStep(
          new CopyGcsBucketDefinitionStep(
              flightBeanBag.getSamService(),
              userRequest,
              sourceBucket,
              flightBeanBag.getControlledResourceService(),
              resolvedCloningInstructions,
              flightBeanBag.getWorkspaceActivityLogService()));

      if (CloningInstructions.COPY_RESOURCE == resolvedCloningInstructions) {
        addStep(
            new SetBucketRolesStep(
                sourceBucket,
                flightBeanBag.getGcpCloudContextService(),
                flightBeanBag.getBucketCloneRolesService(),
                flightBeanBag.getStoragetransfer()),
            cloudRetry);
        addStep(
            new CreateStorageTransferServiceJobStep(flightBeanBag.getStoragetransfer()),
            cloudRetry);
        addStep(new CompleteTransferOperationStep(flightBeanBag.getStoragetransfer()), cloudRetry);
        addStep(
            new DeleteStorageTransferServiceJobStep(flightBeanBag.getStoragetransfer()),
            cloudRetry);
        addStep(new RemoveBucketRolesStep(flightBeanBag.getBucketCloneRolesService()), cloudRetry);
      }
    }
  }
}
