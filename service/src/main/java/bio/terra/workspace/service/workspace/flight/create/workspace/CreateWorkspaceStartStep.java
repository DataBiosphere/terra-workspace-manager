package bio.terra.workspace.service.workspace.flight.create.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import bio.terra.workspace.service.workspace.model.Workspace;

/** Step to create the workspace metadata in the CREATING state */
public class CreateWorkspaceStartStep implements Step {
  private final Workspace workspace;
  private final WorkspaceDao workspaceDao;
  private final WsmResourceStateRule wsmResourceStateRule;

  public CreateWorkspaceStartStep(
      Workspace workspace, WorkspaceDao workspaceDao, WsmResourceStateRule wsmResourceStateRule) {
    this.workspace = workspace;
    this.workspaceDao = workspaceDao;
    this.wsmResourceStateRule = wsmResourceStateRule;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    workspaceDao.createWorkspaceStart(workspace, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Complete the context create in accordance with the state rule
    workspaceDao.createWorkspaceFailure(
        workspace.workspaceId(),
        flightContext.getFlightId(),
        flightContext.getResult().getException().orElse(null),
        wsmResourceStateRule);
    return StepResult.getStepResultSuccess();
  }
}
