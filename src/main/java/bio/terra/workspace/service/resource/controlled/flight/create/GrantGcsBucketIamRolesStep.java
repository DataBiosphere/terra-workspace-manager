package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import com.google.cloud.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A step for granting cloud permissions on resources to workspace members. */
public class GrantGcsBucketIamRolesStep implements Step {

  private final CrlService crlService;
  private final ControlledGcsBucketResource resource;
  private final WorkspaceService workspaceService;
  private final Logger logger = LoggerFactory.getLogger(GrantGcsBucketIamRolesStep.class);

  public GrantGcsBucketIamRolesStep(
      CrlService crlService,
      ControlledGcsBucketResource resource,
      WorkspaceService workspaceService) {
    this.crlService = crlService;
    this.resource = resource;
    this.workspaceService = workspaceService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    String projectId = workspaceService.getRequiredGcpProject(resource.getWorkspaceId());
    // Users do not have read or write access to IAM policies, so this request is executed via
    // WSM's service account.
    Policy currentPolicy =
        crlService.createStorageCow(projectId).getIamPolicy(resource.getBucketName());
    Policy updatedPolicy =
        ControlledResourceCloudSyncUtils.updatePolicyWithSamGroups(
            resource, projectId, currentPolicy, workingMap);
    logger.info(
        "Syncing workspace roles to GCP permissions on bucket {}", resource.getBucketName());

    crlService.createStorageCow(projectId).setIamPolicy(resource.getBucketName(), updatedPolicy);
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
