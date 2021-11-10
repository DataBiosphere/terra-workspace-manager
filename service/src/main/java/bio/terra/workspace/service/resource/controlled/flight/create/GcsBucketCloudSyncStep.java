package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.exceptions.RetryableCrlException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.cloud.Policy;
import com.google.cloud.storage.StorageException;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A step for granting cloud permissions on resources to workspace members. */
public class GcsBucketCloudSyncStep implements Step {

  private final CrlService crlService;
  private final ControlledGcsBucketResource resource;
  private final GcpCloudContextService gcpCloudContextService;
  private final Logger logger = LoggerFactory.getLogger(GcsBucketCloudSyncStep.class);

  public GcsBucketCloudSyncStep(
      CrlService crlService,
      ControlledGcsBucketResource resource,
      GcpCloudContextService gcpCloudContextService) {
    this.crlService = crlService;
    this.resource = resource;
    this.gcpCloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    String projectId = gcpCloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    // Users do not have read or write access to IAM policies, so requests are executed via
    // WSM's service account.
    StorageCow wsmSaStorageCow = crlService.createStorageCow(projectId);
    try {
    Policy currentPolicy = wsmSaStorageCow.getIamPolicy(resource.getBucketName());
    GcpPolicyBuilder updatedPolicyBuilder =
        new GcpPolicyBuilder(resource, projectId, currentPolicy);

    // Read Sam groups for each workspace role.
    Map<WsmIamRole, String> workspaceRoleGroupsMap =
        workingMap.get(WorkspaceFlightMapKeys.IAM_GROUP_EMAIL_MAP, new TypeReference<>() {});
    for (Map.Entry<WsmIamRole, String> entry : workspaceRoleGroupsMap.entrySet()) {
      updatedPolicyBuilder.addWorkspaceBinding(entry.getKey(), entry.getValue());
    }

    // Resources with permissions given to individual users (private or application managed) use
    // the resource's Sam policies to manage those individuals, so they must be synced here.
    // This section should also run for application managed resources, once those are supported.
    if (resource.getAccessScope() == AccessScopeType.ACCESS_SCOPE_PRIVATE) {
      Map<ControlledResourceIamRole, String> resourceRoleGroupsMap =
          workingMap.get(
              ControlledResourceKeys.IAM_RESOURCE_GROUP_EMAIL_MAP, new TypeReference<>() {});
      for (Map.Entry<ControlledResourceIamRole, String> entry : resourceRoleGroupsMap.entrySet()) {
        updatedPolicyBuilder.addResourceBinding(entry.getKey(), entry.getValue());
      }
    }

    logger.info(
        "Syncing workspace roles to GCP permissions on bucket {}", resource.getBucketName());

      wsmSaStorageCow.setIamPolicy(resource.getBucketName(), updatedPolicyBuilder.build());
    } catch (StorageException e) {
      if (e.getCode() == HttpStatus.SC_BAD_REQUEST || e.getCode() == HttpStatus.SC_NOT_FOUND) {
        throw new RetryableCrlException("Error setting IAM permission", e);
      }
    }

    return StepResult.getStepResultSuccess();
  }

  /**
   * Because the resource will be deleted when other steps are undone, we don't need to undo
   * permissions.
   */
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
