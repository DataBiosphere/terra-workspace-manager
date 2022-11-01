package bio.terra.workspace.common.logging.model;

import bio.terra.workspace.common.exception.UnknownFlightClassNameException;
import bio.terra.workspace.service.admin.flights.cloudcontexts.gcp.SyncGcpIamRolesFlight;
import bio.terra.workspace.service.folder.flights.DeleteFolderFlight;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.UpdateControlledAiNotebookResourceFlight;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.UpdateControlledBigQueryDatasetResourceFlight;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.UpdateControlledGcsBucketResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.container.CloneControlledAzureStorageContainerResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.CloneControlledGcsBucketResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.CloneControlledGcpBigQueryDatasetResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.CloneAllResourcesFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.CloneGcpWorkspaceFlight;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.referenced.flight.create.CreateReferenceResourceFlight;
import bio.terra.workspace.service.resource.referenced.flight.update.UpdateReferenceResourceFlight;
import bio.terra.workspace.service.workspace.flight.CreateGcpContextFlightV2;
import bio.terra.workspace.service.workspace.flight.DeleteAzureContextFlight;
import bio.terra.workspace.service.workspace.flight.DeleteGcpContextFlight;
import bio.terra.workspace.service.workspace.flight.RemoveUserFromWorkspaceFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceCreateFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceDeleteFlight;
import bio.terra.workspace.service.workspace.flight.application.able.ApplicationAbleFlight;
import bio.terra.workspace.service.workspace.flight.create.aws.CreateAwsContextFlight;
import bio.terra.workspace.service.workspace.flight.create.azure.CreateAzureContextFlight;
import java.util.Arrays;

/**
 * All the workspace manager flights, contains the flight class name and the change target of the
 * corresponding flight. When a new flight is added in WSM, a new entry should be added here.
 */
public enum ActivityFlight {
  APPLICATION_ABLE_FLIGHT(
      ApplicationAbleFlight.class.getName(), ActivityLogChangedTarget.APPLICATION),
  WORKSPACE_CREATE_FLIGHT(
      WorkspaceCreateFlight.class.getName(), ActivityLogChangedTarget.WORKSPACE),
  WORKSPACE_DELETE_FLIGHT(
      WorkspaceDeleteFlight.class.getName(), ActivityLogChangedTarget.WORKSPACE),
  CONTROLLED_RESOURCE_CREATE_FLIGHT(
      CreateControlledResourceFlight.class.getName(), ActivityLogChangedTarget.RESOURCE),
  CONTROLLED_AI_NOTEBOOK_UPDATE_FLIGHT(
      UpdateControlledAiNotebookResourceFlight.class.getName(), ActivityLogChangedTarget.RESOURCE),
  CONTROLLED_GCS_BUCKET_UPDATE_FLIGHT(
      UpdateControlledGcsBucketResourceFlight.class.getName(), ActivityLogChangedTarget.RESOURCE),
  CONTROLLED_GCS_BUCKET_CLONE_FLIGHT(
      CloneControlledGcsBucketResourceFlight.class.getName(), ActivityLogChangedTarget.RESOURCE),
  CONTROLLED_BQ_DATASET_UPDATE_FLIGHT(
      UpdateControlledBigQueryDatasetResourceFlight.class.getName(),
      ActivityLogChangedTarget.RESOURCE),
  CONTROLLED_BQ_DATASET_CLONE_FLIGHT(
      CloneControlledGcpBigQueryDatasetResourceFlight.class.getName(),
      ActivityLogChangedTarget.RESOURCE),
  CONTROLLED_RESOURCE_DELETE_FLIGHT(
      DeleteControlledResourcesFlight.class.getName(), ActivityLogChangedTarget.RESOURCE),
  AZURE_CLOUD_CONTEXT_CREATE_FLIGHT(
      CreateAzureContextFlight.class.getName(), ActivityLogChangedTarget.AZURE_CLOUD_CONTEXT),
  AZURE_CLOUD_CONTEXT_DELETE_FLIGHT(
      DeleteAzureContextFlight.class.getName(), ActivityLogChangedTarget.AZURE_CLOUD_CONTEXT),
  GCP_CLOUD_CONTEXT_CREATE_FLIGHT(
      CreateGcpContextFlightV2.class.getName(), ActivityLogChangedTarget.GCP_CLOUD_CONTEXT),
  GCP_CLOUD_CONTEXT_DELETE_FLIGHT(
      DeleteGcpContextFlight.class.getName(), ActivityLogChangedTarget.GCP_CLOUD_CONTEXT),
  AWS_CLOUD_CONTEXT_CREATE_FLIGHT(
      CreateAwsContextFlight.class.getName(), ActivityLogChangedTarget.AWS_CLOUD_CONTEXT),
  REMOVE_USER_FROM_WORKSPACE_FLIGHT(
      RemoveUserFromWorkspaceFlight.class.getName(), ActivityLogChangedTarget.USER),
  REFERENCED_RESOURCE_UPDATE_FLIGHT(
      UpdateReferenceResourceFlight.class.getName(), ActivityLogChangedTarget.RESOURCE),
  REFERENCED_RESOURCE_CREATE_FLIGHT(
      CreateReferenceResourceFlight.class.getName(), ActivityLogChangedTarget.RESOURCE),
  ALL_RESOURCES_CLONE_FLIGHT(
      CloneAllResourcesFlight.class.getName(), ActivityLogChangedTarget.RESOURCE),
  GCP_WORKSPACE_CLONE_FLIGHT(
      CloneGcpWorkspaceFlight.class.getName(), ActivityLogChangedTarget.WORKSPACE),
  FOLDER_DELETE_FLIGHT(DeleteFolderFlight.class.getName(), ActivityLogChangedTarget.FOLDER),
  SYNC_GCP_IAM_ROLES_FLIGHT(
      SyncGcpIamRolesFlight.class.getName(), ActivityLogChangedTarget.GCP_CLOUD_CONTEXT),
  CONTROLLED_AZURE_STORAGE_CONTAINER_CLONE_FLIGHT(
      CloneControlledAzureStorageContainerResourceFlight.class.getName(),
      ActivityLogChangedTarget.RESOURCE);

  private final String flightClassName;
  private final ActivityLogChangedTarget changedTarget;

  ActivityFlight(String flightClassName, ActivityLogChangedTarget changedTarget) {
    this.flightClassName = flightClassName;
    this.changedTarget = changedTarget;
  }

  public static ActivityFlight fromFlightClassName(String flightClassName) {
    return Arrays.stream(values())
        .filter(value -> value.flightClassName.equals(flightClassName))
        .findFirst()
        .orElseThrow(
            () ->
                new UnknownFlightClassNameException(
                    String.format(
                        "Flight class %s is unknown, add another enum to ActivityFlight",
                        flightClassName)));
  }

  public ActivityLogChangedTarget getActivityLogChangedTarget() {
    return changedTarget;
  }
}
