package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.db.exception.LandingZoneNotFoundException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.Optional;
import java.util.UUID;

public class GetSharedStorageAccountStep implements Step {
  private final UUID workspaceId;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final SamService samService;

  public GetSharedStorageAccountStep(
      UUID workspaceId, LandingZoneApiDispatch landingZoneApiDispatch, SamService samService) {
    this.workspaceId = workspaceId;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.samService = samService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    try {
      var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
      UUID landingZoneId = landingZoneApiDispatch.getLandingZoneId(bearerToken, workspaceId);
      Optional<ApiAzureLandingZoneDeployedResource> existingSharedStorageAccount =
          landingZoneApiDispatch.getSharedStorageAccount(bearerToken, landingZoneId);
      if (existingSharedStorageAccount.isPresent()) {
        context
            .getWorkingMap()
            .put(ControlledResourceKeys.STORAGE_ACCOUNT, existingSharedStorageAccount.get());
        return StepResult.getStepResultSuccess();
      }

      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new ResourceNotFoundException(
              String.format(
                  "Shared storage account not found in landing zone. Landing zone ID='%s'.",
                  landingZoneId)));
    } catch (IllegalStateException illegalStateException) { // Thrown by landingZoneApiDispatch
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new LandingZoneNotFoundException(
              "Landing zone associated with the billing profile not found."));
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Do nothing. This is a read only step.
    return StepResult.getStepResultSuccess();
  }
}
