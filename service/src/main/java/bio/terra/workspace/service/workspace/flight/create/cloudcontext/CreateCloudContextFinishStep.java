package bio.terra.workspace.service.workspace.flight.create.cloudcontext;

import static java.lang.Boolean.TRUE;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.CloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Finish the cloud context creation we expect in the working map we expect:
 *
 * <p>Optional CLOUD_CONTEXT_STRING - serialized string of cloud-specific data stored with the cloud
 * context row
 *
 * <p>Required CLOUD_CONTEXT_HOLDER - the cloud context holder filled in with the cloud-specific
 * context object.
 */
public class CreateCloudContextFinishStep implements Step {

  private final WorkspaceDao workspaceDao;
  private final UUID workspaceUuid;
  private final CloudPlatform cloudPlatform;

  public CreateCloudContextFinishStep(
      UUID workspaceUuid, WorkspaceDao workspaceDao, CloudPlatform cloudPlatform) {
    this.workspaceDao = workspaceDao;
    this.cloudPlatform = cloudPlatform;
    this.workspaceUuid = workspaceUuid;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap workingMap = flightContext.getWorkingMap();

    workingMap.put(ResourceKeys.UPDATE_COMPLETE, Boolean.FALSE);
    CloudContext cloudContext =
        FlightUtils.getRequired(
            workingMap, WorkspaceFlightMapKeys.CLOUD_CONTEXT, CloudContext.class);
    String contextJson = cloudContext.serialize();
    workspaceDao.createCloudContextSuccess(
        workspaceUuid, cloudPlatform, contextJson, flightContext.getFlightId());
    workingMap.put(ResourceKeys.UPDATE_COMPLETE, TRUE);

    // Retrieve the stored cloud context with proper state and error
    DbCloudContext dbCloudContext =
        workspaceDao.getCloudContext(workspaceUuid, cloudContext.getCloudPlatform()).orElseThrow();
    CloudContext fullCloudContext =
        dbCloudContext
            .getCloudPlatform()
            .getCloudContextService()
            .makeCloudContextFromDb(dbCloudContext);

    FlightUtils.setResponse(flightContext, fullCloudContext, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    Boolean didUpdate =
        flightContext.getWorkingMap().get(ResourceKeys.UPDATE_COMPLETE, Boolean.class);
    if (TRUE.equals(didUpdate)) {
      // If the update is complete, then we cannot undo it. This is a teeny tiny window
      // where the error occurs after the update, but before the success return. However,
      // the DebugInfo.lastStepFailure will always hit it.
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL, new RuntimeException("dismal failure"));
    }
    // Failed before update - perform undo
    return StepResult.getStepResultSuccess();
  }
}
