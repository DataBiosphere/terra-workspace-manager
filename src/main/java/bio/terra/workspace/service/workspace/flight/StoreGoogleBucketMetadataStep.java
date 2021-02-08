package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.WorkspaceDao;

public class StoreGoogleBucketMetadataStep implements Step {

  private WorkspaceDao workspaceDao;

  public StoreGoogleBucketMetadataStep(WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
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
