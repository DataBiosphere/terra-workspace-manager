package bio.terra.workspace.service.workspace.flight.cloud.azure;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.UUID;

public class ValidateLandingZoneRegionAgainstPolicyStep implements Step {
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final AuthenticatedUserRequest userRequest;
  private final TpsApiDispatch tpsApiDispatch;
  private final UUID workspaceUuid;

  public ValidateLandingZoneRegionAgainstPolicyStep(
      LandingZoneApiDispatch landingZoneApiDispatch,
      AuthenticatedUserRequest userRequest,
      TpsApiDispatch tpsApiDispatch,
      UUID workspaceUuid) {
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.userRequest = userRequest;
    this.tpsApiDispatch = tpsApiDispatch;
    this.workspaceUuid = workspaceUuid;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final BearerToken bearerToken = new BearerToken(userRequest.getRequiredToken());
    var landingZoneId = landingZoneApiDispatch.getLandingZoneId(bearerToken, workspaceUuid);
    ResourceValidationUtils.validateRegionAgainstPolicy(
        tpsApiDispatch,
        workspaceUuid,
        landingZoneApiDispatch.getAzureLandingZoneRegion(bearerToken, landingZoneId),
        CloudPlatform.AZURE);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
