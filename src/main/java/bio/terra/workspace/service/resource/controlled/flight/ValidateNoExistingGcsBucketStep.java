package bio.terra.workspace.service.resource.controlled.flight;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.WorkspaceService;

/*
 * Before starting a step to create a bucket, ensure that there's not already a bucket with
 * the same name.
 */
public class ValidateNoExistingGcsBucketStep implements Step {

  private final CrlService crlService;
  private final ControlledGcsBucketResource resource;
  private final WorkspaceService workspaceService;

  public ValidateNoExistingGcsBucketStep(
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
    final String projectId = workspaceService.getRequiredGcpProject(resource.getWorkspaceId());
    final StorageCow storageCow = crlService.createStorageCow(projectId);
    final BucketCow existingBucket = storageCow.get(resource.getBucketName());
    if (existingBucket != null) {
      throw new DuplicateResourceException(
          String.format("GCS Bucket with name %s already exists.", resource.getBucketName()));
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
