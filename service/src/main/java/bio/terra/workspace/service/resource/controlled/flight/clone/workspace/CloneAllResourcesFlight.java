package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This flight uses a dynamic list of steps depending on ControlledResourceKeys.RESOURCES_TO_CLONE
 * in the input parameters list. Each resource type requires a different subflight to be launched.
 */
public class CloneAllResourcesFlight extends Flight {

  private static final Logger logger = LoggerFactory.getLogger(CloneAllResourcesFlight.class);

  public CloneAllResourcesFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(applicationContext);

    List<ResourceCloneInputs> resourceCloneInputsList =
        inputParameters.get(ControlledResourceKeys.RESOURCES_TO_CLONE, new TypeReference<>() {});

    AuthenticatedUserRequest userRequest =
        FlightUtils.getRequired(
            inputParameters,
            JobMapKeys.AUTH_USER_INFO.getKeyName(),
            AuthenticatedUserRequest.class);
    // Each entry in the list corresponds to a new step in this flight
    for (ResourceCloneInputs resourceCloneInputs : resourceCloneInputsList) {
      addFlightLaunchStepsForResource(resourceCloneInputs, flightBeanBag, userRequest);
    }
  }

  private void addFlightLaunchStepsForResource(
      ResourceCloneInputs resourceCloneInputs,
      FlightBeanBag flightBeanBag,
      AuthenticatedUserRequest userRequest) {
    WsmResource resource = resourceCloneInputs.getResource();

    switch (resource.getStewardshipType()) {
      case REFERENCED:
        addStep(
            new CloneReferencedResourceStep(
                userRequest,
                flightBeanBag.getSamService(),
                flightBeanBag.getReferencedResourceService(),
                resourceCloneInputs.getResource().castToReferencedResource(),
                resourceCloneInputs.getDestinationResourceId(),
                resourceCloneInputs.getDestinationFolderId()),
            RetryRules.shortDatabase());
        break;
      case CONTROLLED:
        switch (resourceCloneInputs.getResource().getResourceType()) {
            // GCP
          case CONTROLLED_GCP_GCS_BUCKET -> {
            addStep(
                new LaunchCloneGcsBucketResourceFlightStep(
                    resource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET),
                    resourceCloneInputs.getFlightId(),
                    resourceCloneInputs.getDestinationResourceId(),
                    resourceCloneInputs.getDestinationFolderId()));
            addStep(
                new AwaitCloneGcsBucketResourceFlightStep(
                    resource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET),
                    resourceCloneInputs.getFlightId()),
                RetryRules.cloudLongRunning());
          }
          case CONTROLLED_GCP_BIG_QUERY_DATASET -> {
            addStep(
                new LaunchCloneControlledGcpBigQueryDatasetResourceFlightStep(
                    resource.castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET),
                    resourceCloneInputs.getFlightId(),
                    resourceCloneInputs.getDestinationResourceId(),
                    resourceCloneInputs.getDestinationFolderId()));
            addStep(
                new AwaitCloneControlledGcpBigQueryDatasetResourceFlightStep(
                    resource.castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET),
                    resourceCloneInputs.getFlightId()),
                RetryRules.cloudLongRunning());
          }
            // CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE: not supported
            // CONTROLLED_GCP_GCE_INSTANCE: not supported
            // CONTROLLED_GCP_DATAPROC_CLUSTER: not supported

            // Azure
          case CONTROLLED_AZURE_STORAGE_CONTAINER -> {
            addStep(
                new LaunchCloneControlledAzureStorageContainerResourceFlightStep(
                    resource.castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER),
                    resourceCloneInputs.getFlightId(),
                    resourceCloneInputs.getDestinationResourceId(),
                    resourceCloneInputs.getDestinationFolderId()));
            addStep(
                new AwaitCloneControlledAzureStorageContainerResourceFlightStep(
                    resource.castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER),
                    resourceCloneInputs.getFlightId()),
                RetryRules.cloudLongRunning());
          }

          case CONTROLLED_AZURE_MANAGED_IDENTITY -> {
            addStep(
                new LaunchCloneControlledAzureManagedIdentityResourceFlightStep(
                    resource.castByEnum(WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY),
                    resourceCloneInputs.getFlightId(),
                    resourceCloneInputs.getDestinationResourceId()));
            addStep(
                    new AwaitCloneControlledAzureStorageContainerResourceFlightStep(
                            resource.castByEnum(WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY),
                            resourceCloneInputs.getFlightId()),
                    RetryRules.cloudLongRunning());
          }

            // CONTROLLED_AZURE_DATABASE, CONTROLLED_AZURE_DISK
            // CONTROLLED_AZURE_VM, CONTROLLED_AZURE_BATCH_POOL: not supported / implemented

            // AWS
            // TODO(BENCH-694): support clone CONTROLLED_AWS_S3_STORAGE_FOLDER
            // CONTROLLED_AWS_SAGEMAKER_NOTEBOOK: not supported

            // Flexible
          case CONTROLLED_FLEXIBLE_RESOURCE -> {
            addStep(
                new LaunchCloneControlledFlexibleResourceFlightStep(
                    resource.castByEnum(WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE),
                    resourceCloneInputs.getFlightId(),
                    resourceCloneInputs.getDestinationResourceId(),
                    resourceCloneInputs.getDestinationFolderId()),
                RetryRules.shortDatabase());
            addStep(
                new AwaitCloneControlledFlexibleResourceFlightStep(
                    resource.castByEnum(WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE),
                    resourceCloneInputs.getFlightId()),
                RetryRules.shortDatabase());
          }

          default ->
          // Can't throw in a flight constructor
          logger.error(
              "Unsupported controlled resource type {}",
              resourceCloneInputs.getResource().getResourceType());
        }
        break;

      default:
        logger.error(
            "Unsupported stewardship type {}",
            resourceCloneInputs.getResource().getStewardshipType());
        break;
    }
  }
}
