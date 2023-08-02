package bio.terra.workspace.service.policy.flight;

import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.policy.TpsUtilities;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MergeGroupsStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(MergeGroupsStep.class);

  private final UUID workspaceId;
  private final SamService samService;
  private final TpsApiDispatch tpsApiDispatch;
  private final AuthenticatedUserRequest userRequest;

  public MergeGroupsStep(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      TpsApiDispatch tpsApiDispatch,
      SamService samService) {

    this.workspaceId = workspaceId;
    this.samService = samService;
    this.tpsApiDispatch = tpsApiDispatch;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    TpsPaoGetResult mergedPao =
        flightContext
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.EFFECTIVE_POLICIES, TpsPaoGetResult.class);

    List<String> mergedGroups =
        new ArrayList<>(
            TpsUtilities.getGroupConstraintsFromInputs(mergedPao.getEffectiveAttributes()));

    try {
      samService.addGroupsToAuthDomain(
          userRequest,
          SamConstants.SamResource.WORKSPACE,
          this.workspaceId.toString(),
          mergedGroups);
    } catch (Exception ex) {
      logger.info("Attempt to add groups to auth domain failed", ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Since we can't remove groups from the auth domain, there's nothing to do.
    return StepResult.getStepResultSuccess();
  }
}
