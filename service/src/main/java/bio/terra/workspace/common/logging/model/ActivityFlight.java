package bio.terra.workspace.common.logging.model;

import bio.terra.workspace.common.exception.UnknownFlightClassNameException;
import bio.terra.workspace.service.admin.flights.cloudcontexts.gcp.SyncGcpIamRolesFlight;
import bio.terra.workspace.service.folder.flights.DeleteFolderFlight;
import bio.terra.workspace.service.grant.flight.RevokeTemporaryGrantFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.container.CloneControlledAzureStorageContainerResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.database.CloneControlledAzureDatabaseResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.managedIdentity.CloneControlledAzureManagedIdentityResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.CloneControlledGcsBucketResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.SignedUrlListDataTransferFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.CloneControlledGcpBigQueryDatasetResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.flexibleresource.CloneControlledFlexibleResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.CloneAllResourcesFlight;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.CloneWorkspaceFlight;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.flight.UpdateResourceFlight;
import bio.terra.workspace.service.resource.referenced.flight.clone.CloneReferencedResourceFlight;
import bio.terra.workspace.service.workspace.flight.application.ApplicationAbleFlight;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.DeleteCloudContextResourceFlight;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.RemoveUserFromWorkspaceFlight;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFlight;
import bio.terra.workspace.service.workspace.flight.create.workspace.CreateWorkspaceV2Flight;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextFlight;
import bio.terra.workspace.service.workspace.flight.delete.workspace.WorkspaceDeleteFlight;
import java.util.Arrays;

/**
 * All the workspace manager flights, contains the flight class name and the change target of the
 * corresponding flight. When a new flight is added in WSM, a new entry should be added here.
 */
public enum ActivityFlight {
  // Workspace
  WORKSPACE_DELETE_FLIGHT(
      WorkspaceDeleteFlight.class.getName(), ActivityLogChangedTarget.WORKSPACE),
  WORKSPACE_CREATE_V2_FLIGHT(
      CreateWorkspaceV2Flight.class.getName(), ActivityLogChangedTarget.WORKSPACE),

  // Cloud context
  CLOUD_CONTEXT_CREATE_FLIGHT(
      CreateCloudContextFlight.class.getName(),
      ActivityLogChangedTarget.CLOUD_CONTEXT,
      /* logInFlight= */ true),
  CLOUD_CONTEXT_DELETE_FLIGHT(
      DeleteCloudContextFlight.class.getName(),
      ActivityLogChangedTarget.CLOUD_CONTEXT,
      /* logInFlight= */ true),
  DELETE_CLOUD_CONTEXT_RESOURCE_FLIGHT(
      DeleteCloudContextResourceFlight.class.getName(), ActivityLogChangedTarget.RESOURCE),

  // Resources
  ALL_RESOURCES_CLONE_FLIGHT(
      CloneAllResourcesFlight.class.getName(), ActivityLogChangedTarget.RESOURCE),
  CONTROLLED_RESOURCE_CREATE_FLIGHT(
      CreateControlledResourceFlight.class.getName(),
      ActivityLogChangedTarget.RESOURCE,
      /* logInFlight= */ true),
  CONTROLLED_RESOURCE_DELETE_FLIGHT(
      DeleteControlledResourcesFlight.class.getName(), ActivityLogChangedTarget.RESOURCE),
  RESOURCE_UPDATE_FLIGHT(
      UpdateResourceFlight.class.getName(),
      ActivityLogChangedTarget.RESOURCE,
      /* logInFlight= */ true),
  CLONE_REFERENCED_RESOURCE_FLIGHT(
      CloneReferencedResourceFlight.class.getName(),
      ActivityLogChangedTarget.RESOURCE,
      /* logInFlight= */ true),
  CLONE_FLEX_RESOURCE_FLIGHT(
      CloneControlledFlexibleResourceFlight.class.getName(),
      ActivityLogChangedTarget.CONTROLLED_FLEXIBLE_RESOURCE),
  FOLDER_DELETE_FLIGHT(DeleteFolderFlight.class.getName(), ActivityLogChangedTarget.FOLDER),

  // User
  REMOVE_USER_FROM_WORKSPACE_FLIGHT(
      RemoveUserFromWorkspaceFlight.class.getName(), ActivityLogChangedTarget.USER),
  REVOKE_TEMPORARY_GRANT_FLIGHT(
      RevokeTemporaryGrantFlight.class.getName(), ActivityLogChangedTarget.WORKSPACE),

  // Application
  APPLICATION_ABLE_FLIGHT(
      ApplicationAbleFlight.class.getName(), ActivityLogChangedTarget.APPLICATION),

  // GCP
  GCP_WORKSPACE_CLONE_FLIGHT(
      CloneWorkspaceFlight.class.getName(), ActivityLogChangedTarget.WORKSPACE),
  SYNC_GCP_IAM_ROLES_FLIGHT(
      SyncGcpIamRolesFlight.class.getName(), ActivityLogChangedTarget.GCP_CLOUD_CONTEXT),
  CONTROLLED_GCS_BUCKET_CLONE_FLIGHT(
      CloneControlledGcsBucketResourceFlight.class.getName(),
      ActivityLogChangedTarget.CONTROLLED_GCP_GCS_BUCKET),
  CONTROLLED_BQ_DATASET_CLONE_FLIGHT(
      CloneControlledGcpBigQueryDatasetResourceFlight.class.getName(),
      ActivityLogChangedTarget.CONTROLLED_GCP_BIG_QUERY_DATASET),

  SIGNED_URL_LIST_DATA_TRANSFER_FLIGHT(
      SignedUrlListDataTransferFlight.class.getName(),
      ActivityLogChangedTarget.CONTROLLED_GCP_GCS_BUCKET),

  // AZURE
  CONTROLLED_AZURE_STORAGE_CONTAINER_CLONE_FLIGHT(
      CloneControlledAzureStorageContainerResourceFlight.class.getName(),
      ActivityLogChangedTarget.CONTROLLED_AZURE_STORAGE_CONTAINER),
  CONTROLLED_AZURE_MANAGED_IDENTITY_CLONE_FLIGHT(
      CloneControlledAzureManagedIdentityResourceFlight.class.getName(),
      ActivityLogChangedTarget.CONTROLLED_AZURE_MANAGED_IDENTITY),
  CONTROLLED_AZURE_DATABASE_CLONE_FLIGHT(
      CloneControlledAzureDatabaseResourceFlight.class.getName(),
      ActivityLogChangedTarget.CONTROLLED_AZURE_DATABASE);

  private final String flightClassName;
  private final ActivityLogChangedTarget changedTarget;

  /**
   * If true, write to activity log at the last step of the flight. If false, write to activity log
   * in the activity log hook.
   */
  private final boolean logInFlight;

  ActivityFlight(String flightClassName, ActivityLogChangedTarget changedTarget) {
    this.flightClassName = flightClassName;
    this.changedTarget = changedTarget;
    this.logInFlight = false;
  }

  ActivityFlight(
      String flightClassName, ActivityLogChangedTarget changedTarget, boolean logInFlight) {
    this.flightClassName = flightClassName;
    this.changedTarget = changedTarget;
    this.logInFlight = logInFlight;
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

  public boolean isResourceFlight() {
    return changedTarget.isResource();
  }

  /** Should skip logging in {@link bio.terra.workspace.common.logging.WorkspaceActivityLogHook}. */
  public boolean shouldSkipLogInHook() {
    return logInFlight;
  }
}
