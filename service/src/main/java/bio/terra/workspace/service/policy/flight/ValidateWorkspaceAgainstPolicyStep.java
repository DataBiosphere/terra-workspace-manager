package bio.terra.workspace.service.policy.flight;

import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class ValidateWorkspaceAgainstPolicyStep implements Step {
  private final UUID workspaceId;
  private final CloudPlatform cloudPlatform;
  private final String destinationLocation;
  private final AuthenticatedUserRequest userRequest;
  private final ResourceDao resourceDao;
  private final TpsApiDispatch tpsApiDispatch;

  public ValidateWorkspaceAgainstPolicyStep(
      UUID workspaceId,
      CloudPlatform cloudPlatform,
      String destinationLocation,
      AuthenticatedUserRequest userRequest,
      ResourceDao resourceDao,
      TpsApiDispatch tpsApiDispatch) {
    this.workspaceId = workspaceId;
    this.cloudPlatform = cloudPlatform;
    this.userRequest = userRequest;
    this.tpsApiDispatch = tpsApiDispatch;
    this.resourceDao = resourceDao;
    this.destinationLocation = destinationLocation;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final TpsPaoGetResult effectivePolicies =
        flightContext
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.EFFECTIVE_POLICIES, TpsPaoGetResult.class);

    if (cloudPlatform != CloudPlatform.GCP) {
      // We can only validate GCP right now. We'll need to add other platforms
      // to the ontology before we can validate them.
      return StepResult.getStepResultSuccess();
    }

    // Validate the workspace controlled resources against any region policies.
    HashSet<String> validRegions = new HashSet<>();
    for (String validRegion :
        tpsApiDispatch.listValidRegionsForPao(effectivePolicies, cloudPlatform)) {
      validRegions.add(validRegion.toLowerCase());
    }
    List<ControlledResource> existingResources =
        resourceDao.listControlledResources(workspaceId, cloudPlatform);

    for (var existingResource : existingResources) {
      if (existingResource.getRegion() == null) {
        // older resources may not have a region.
        continue;
      }
      if (!validRegions.contains(existingResource.getRegion().toLowerCase())) {
        throw new PolicyConflictException("Workspace contains resources in violation of policy.");
      }
    }

    if (destinationLocation != null && !validRegions.contains(destinationLocation.toLowerCase())) {
      throw new PolicyConflictException(
          "The specified destination location violates region policies");
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Validation step so there should be nothing to undo, only propagate the flight failure.
    return context.getResult();
  }
}
