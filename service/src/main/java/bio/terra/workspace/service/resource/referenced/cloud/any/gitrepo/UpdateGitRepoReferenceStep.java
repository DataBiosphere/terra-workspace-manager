package bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.model.DbUpdater;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import org.apache.commons.lang3.StringUtils;

public class UpdateGitRepoReferenceStep implements Step {

  public UpdateGitRepoReferenceStep() {}

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    var dbUpdater =
        FlightUtils.getRequired(
            context.getWorkingMap(),
            WorkspaceFlightMapKeys.ResourceKeys.DB_UPDATER,
            DbUpdater.class);
    var attributes =
        FlightUtils.getRequired(
            context.getInputParameters(),
            WorkspaceFlightMapKeys.ResourceKeys.UPDATE_PARAMETERS,
            ReferencedGitRepoAttributes.class);
    var oldAttributes = dbUpdater.getOriginalAttributes(ReferencedGitRepoAttributes.class);

    String gitRepoUrl =
        StringUtils.isEmpty(attributes.getGitRepoUrl())
            ? oldAttributes.getGitRepoUrl()
            : attributes.getGitRepoUrl();

    dbUpdater.updateAttributes(new ReferencedGitRepoAttributes(gitRepoUrl));
    context.getWorkingMap().put(WorkspaceFlightMapKeys.ResourceKeys.DB_UPDATER, dbUpdater);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
