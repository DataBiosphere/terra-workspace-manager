package bio.terra.workspace.service.workspace.flight.create.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.DuplicateFlightIdException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.workspace.WsmApplicationService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.application.AbleEnum;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;

public class EnableApplicationsStep implements Step {
  public static final String FLIGHT_ID_KEY = "enableApplicationsFlightId";
  private final List<String> applicationIds;
  private final WsmApplicationService applicationService;
  private final AuthenticatedUserRequest userRequest;
  private final Workspace workspace;

  public EnableApplicationsStep(
      List<String> applicationIds,
      WsmApplicationService applicationService,
      AuthenticatedUserRequest userRequest,
      Workspace workspace) {
    this.applicationIds = applicationIds;
    this.applicationService = applicationService;
    this.userRequest = userRequest;
    this.workspace = workspace;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    Map<String, String> flightIds =
        FlightUtils.getRequired(
            context.getWorkingMap(), WorkspaceFlightMapKeys.FLIGHT_IDS, new TypeReference<>() {});
    var flightId = flightIds.get(FLIGHT_ID_KEY);
    try {
      applicationService.launchApplicationAbleJobAndWait(
          userRequest, workspace, applicationIds, AbleEnum.ENABLE, flightId);
      return StepResult.getStepResultSuccess();
    } catch (DuplicateFlightIdException e) {
      // this happens on retry, just wait for the job
      return FlightUtils.waitForSubflightCompletion(context.getStairway(), flightId)
          .convertToStepResult();
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // sub flights undo themselves
    return StepResult.getStepResultSuccess();
  }
}
