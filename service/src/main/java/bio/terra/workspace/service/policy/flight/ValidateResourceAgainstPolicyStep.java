package bio.terra.workspace.service.policy.flight;

import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.HashSet;
import java.util.UUID;

public class ValidateResourceAgainstPolicyStep implements Step {

  private final TpsApiDispatch tpsApiDispatch;
  private final CloudPlatform cloudPlatform;
  private final ControlledAzureStorageResource resource;
  private final UUID workspaceId;

  public ValidateResourceAgainstPolicyStep(
      TpsApiDispatch tpsApiDispatch,
      CloudPlatform cloudPlatform,
      ControlledAzureStorageResource resource,
      UUID workspaceId) {
    this.tpsApiDispatch = tpsApiDispatch;
    this.cloudPlatform = cloudPlatform;
    this.resource = resource;
    this.workspaceId = workspaceId;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final TpsPaoGetResult effectivePolicies = tpsApiDispatch.getPao(workspaceId);

    // Validate the workspace controlled resources against any region policies.
    HashSet<String> validRegions = new HashSet<>();
    for (String validRegion :
        tpsApiDispatch.listValidRegionsForPao(effectivePolicies, cloudPlatform)) {
      validRegions.add(validRegion.toLowerCase());
    }

    if (!validRegions.contains(resource.getRegion().toLowerCase())) {
      throw new PolicyConflictException(
          "Attempting to create resource in invalid region: " + resource.getRegion());
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return context.getResult();
  }
}
