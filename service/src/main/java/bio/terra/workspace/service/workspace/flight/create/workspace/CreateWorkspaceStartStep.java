package bio.terra.workspace.service.workspace.flight.create.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import bio.terra.workspace.service.workspace.exceptions.DuplicateCloudContextException;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.List;

/** Step to create the workspace metadata in the CREATING state */
public class CreateWorkspaceStartStep implements Step {
  private final Workspace workspace;
  private final WorkspaceDao workspaceDao;
  private final WsmResourceStateRule wsmResourceStateRule;
  private final List<String> applicationIds;

  public CreateWorkspaceStartStep(
      Workspace workspace,
      WorkspaceDao workspaceDao,
      WsmResourceStateRule wsmResourceStateRule,
      List<String> applicationIds) {
    this.workspace = workspace;
    this.workspaceDao = workspaceDao;
    this.wsmResourceStateRule = wsmResourceStateRule;
    this.applicationIds = applicationIds;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    try {
      workspaceDao.createWorkspaceStart(workspace, applicationIds, flightContext.getFlightId());
    } catch (DuplicateCloudContextException e) {
      // On a retry or restart, we may have already started the cloud context create,
      // so we ignore the duplicate exception.
    }
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
