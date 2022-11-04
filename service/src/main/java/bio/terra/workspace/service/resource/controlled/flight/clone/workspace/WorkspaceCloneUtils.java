package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.model.WsmCloneResourceResult;
import java.util.UUID;
import javax.annotation.Nullable;

public class WorkspaceCloneUtils {

  private WorkspaceCloneUtils() {}

  public static WsmCloneResourceResult flightStatusToCloneResult(
      FlightStatus subflightStatus, WsmResource resource) {
    switch (subflightStatus) {
      default:
      case WAITING:
      case READY:
      case QUEUED:
      case READY_TO_RESTART:
      case RUNNING:
        throw new IllegalStateException(
            String.format("Unexpected status %s for finished flight", subflightStatus));
      case SUCCESS:
        if (CloningInstructions.COPY_NOTHING == resource.getCloningInstructions()) {
          return WsmCloneResourceResult.SKIPPED;
        } else {
          return WsmCloneResourceResult.SUCCEEDED;
        }
      case ERROR:
      case FATAL:
        return WsmCloneResourceResult.FAILED;
    }
  }

  // TODO: PF-2107 as part of the refactor, these will move into the object hierarchy
  public static ControlledBigQueryDatasetResource buildDestinationControlledBigQueryDataset(
      ControlledBigQueryDatasetResource sourceDataset,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId,
      String name,
      @Nullable String description,
      String cloudInstanceName,
      String destinationProjectId) {
    return ControlledBigQueryDatasetResource.builder()
        .projectId(destinationProjectId)
        .datasetName(cloudInstanceName)
        .common(
            sourceDataset.buildControlledResourceCommonFields(
                destinationWorkspaceId,
                destinationResourceId,
                destinationFolderId,
                name,
                description))
        .build();
  }

  public static ControlledGcsBucketResource buildDestinationControlledGcsBucket(
      ControlledGcsBucketResource sourceBucket,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId,
      String name,
      @Nullable String description,
      String cloudInstanceName) {
    return ControlledGcsBucketResource.builder()
        .bucketName(cloudInstanceName)
        .common(
            sourceBucket.buildControlledResourceCommonFields(
                destinationWorkspaceId,
                destinationResourceId,
                destinationFolderId,
                name,
                description))
        .build();
  }

  /**
   * Builds an Azure storage container resource object from a source container
   *
   * @param sourceContainer Source container from which to derive common resource fields
   * @param storageAccountId Destination Azure storage account resource ID
   * @param destinationWorkspaceId Destination workspace ID
   * @param destinationResourceId Destination resource ID for the new container object
   * @param name WSM-internal resource name
   * @param description Human-friendly description for the container resource
   * @param cloudInstanceName Name that the resource will receive when created in Azure
   * @return An Azure storage container object
   */
  public static ControlledAzureStorageContainerResource buildDestinationControlledAzureContainer(
      ControlledAzureStorageContainerResource sourceContainer,
      UUID storageAccountId,
      UUID destinationWorkspaceId,
      UUID destinationResourceId,
      String name,
      @Nullable String description,
      String cloudInstanceName) {
    return ControlledAzureStorageContainerResource.builder()
        .storageContainerName(cloudInstanceName)
        .storageAccountId(storageAccountId)
        .common(
            sourceContainer.buildControlledResourceCommonFields(
                destinationWorkspaceId,
                destinationResourceId,
                /* destinationFolderId */ null,
                name,
                description))
        .build();
  }
}
