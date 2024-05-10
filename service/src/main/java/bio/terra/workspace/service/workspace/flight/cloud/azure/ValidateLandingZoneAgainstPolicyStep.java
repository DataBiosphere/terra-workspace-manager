package bio.terra.workspace.service.workspace.flight.cloud.azure;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.policy.PolicyValidator;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.policy.TpsUtilities;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.ArrayList;
import java.util.UUID;

public class ValidateLandingZoneAgainstPolicyStep implements Step {
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final AuthenticatedUserRequest userRequest;
  private final TpsApiDispatch tpsApiDispatch;
  private final UUID workspaceUuid;
  private final WorkspaceService workspaceService;
  private final PolicyValidator policyValidator;

  public ValidateLandingZoneAgainstPolicyStep(
      LandingZoneApiDispatch landingZoneApiDispatch,
      AuthenticatedUserRequest userRequest,
      TpsApiDispatch tpsApiDispatch,
      UUID workspaceUuid,
      WorkspaceService workspaceService,
      PolicyValidator policyValidator) {
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.userRequest = userRequest;
    this.tpsApiDispatch = tpsApiDispatch;
    this.workspaceUuid = workspaceUuid;
    this.workspaceService = workspaceService;
    this.policyValidator = policyValidator;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final BearerToken bearerToken = new BearerToken(userRequest.getRequiredToken());
    var workspace = workspaceService.getWorkspace(workspaceUuid);
    var landingZoneId = landingZoneApiDispatch.getLandingZoneId(bearerToken, workspace);
    ResourceValidationUtils.validateRegionAgainstPolicy(
        tpsApiDispatch,
        workspaceUuid,
        landingZoneApiDispatch.getLandingZoneRegionUsingWsmToken(landingZoneId),
        CloudPlatform.AZURE);

    var policies = tpsApiDispatch.getPao(workspaceUuid);

    var validationErrors = new ArrayList<String>();
    if (TpsUtilities.containsProtectedDataPolicy(policies.getEffectiveAttributes())) {
      var landingZone = landingZoneApiDispatch.getAzureLandingZone(bearerToken, landingZoneId);
      validationErrors.addAll(
          policyValidator.validateLandingZoneSupportsProtectedData(landingZone));
    }
    validationErrors.addAll(
        policyValidator.validateWorkspaceConformsToDataTrackingPolicy(
            workspace, policies, userRequest));
    if (!validationErrors.isEmpty()) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL, new PolicyConflictException(validationErrors));
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
