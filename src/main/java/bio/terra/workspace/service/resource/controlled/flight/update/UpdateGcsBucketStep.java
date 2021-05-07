package bio.terra.workspace.service.resource.controlled.flight.update;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.workspace.WorkspaceService;

public class UpdateGcsBucketStep implements Step {

  private final ControlledGcsBucketResource bucketResource;
  private final CrlService crlService;
  private final WorkspaceService workspaceService;

  public UpdateGcsBucketStep(ControlledGcsBucketResource bucketResource, CrlService crlService, WorkspaceService workspaceService) {
    this.bucketResource = bucketResource;
    this.crlService = crlService;
    this.workspaceService = workspaceService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    return null;
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return null;
  }
}
