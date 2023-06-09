package bio.terra.workspace.service.workspace.flight.create.workspace;

import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.policy.TpsUtilities;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step that creates a Sam workspace resource. This only runs for MC_WORKSPACE stage workspaces,
 * as RAWLS_WORKSPACEs use existing Sam resources instead.
 */
public class CreateWorkspaceAuthzStep implements Step {

  private final SamService samService;
  private final TpsApiDispatch tpsApiDispatch;
  private final AuthenticatedUserRequest userRequest;
  private final Workspace workspace;
  private final FeatureConfiguration features;
  private final String projectOwnerGroupId;

  private static final Logger logger = LoggerFactory.getLogger(CreateWorkspaceAuthzStep.class);

  public CreateWorkspaceAuthzStep(
      Workspace workspace,
      SamService samService,
      TpsApiDispatch tpsApiDispatch,
      FeatureConfiguration features,
      AuthenticatedUserRequest userRequest,
      String projectOwnerGroupId) {
    this.samService = samService;
    this.userRequest = userRequest;
    this.workspace = workspace;
    this.tpsApiDispatch = tpsApiDispatch;
    this.features = features;
    this.projectOwnerGroupId = projectOwnerGroupId;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws RetryException, InterruptedException {

    // Even though WSM should own this resource, Stairway steps can run multiple times, so it's
    // possible this step already created the resource. If WSM can either read the existing Sam
    // resource or create a new one, this is considered successful.
    if (!canReadExistingWorkspace(workspace.getWorkspaceId())) {
      List<String> authDomains = List.of();
      if (features.isTpsEnabled()) {
        // Don't depend on the PAO being configured.
        TpsPaoGetResult pao = tpsApiDispatch.getPao(workspace.workspaceId());
        if (pao != null) {
          authDomains = TpsUtilities.getGroupConstraintsFromInputs(pao.getEffectiveAttributes());
        }
      }
      samService.createWorkspaceWithDefaults(
          userRequest, workspace.getWorkspaceId(), authDomains, projectOwnerGroupId);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    samService.deleteWorkspace(userRequest, workspace.getWorkspaceId());
    return StepResult.getStepResultSuccess();
  }

  private boolean canReadExistingWorkspace(UUID workspaceUuid) throws InterruptedException {
    return samService.isAuthorized(
        userRequest,
        SamConstants.SamResource.WORKSPACE,
        workspaceUuid.toString(),
        SamConstants.SamWorkspaceAction.READ);
  }
}
