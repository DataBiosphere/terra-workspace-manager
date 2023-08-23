package bio.terra.workspace.service.policy.flight;

import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.ArrayList;
import java.util.UUID;

public class ValidateWorkspaceAgainstPolicyStep implements Step {
  private final UUID workspaceId;
  private final CloudPlatform cloudPlatform;
  private final String destinationLocation;
  private final AuthenticatedUserRequest userRequest;
  private final ResourceDao resourceDao;
  private final TpsApiDispatch tpsApiDispatch;
  private final CloningInstructions cloningInstructions;

  public ValidateWorkspaceAgainstPolicyStep(
      UUID workspaceId,
      CloudPlatform cloudPlatform,
      String destinationLocation,
      CloningInstructions cloningInstructions,
      AuthenticatedUserRequest userRequest,
      ResourceDao resourceDao,
      TpsApiDispatch tpsApiDispatch) {
    this.workspaceId = workspaceId;
    this.cloudPlatform = cloudPlatform;
    this.userRequest = userRequest;
    this.tpsApiDispatch = tpsApiDispatch;
    this.resourceDao = resourceDao;
    this.destinationLocation = destinationLocation;
    this.cloningInstructions = cloningInstructions;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final TpsPaoGetResult effectivePolicies =
        FlightUtils.getRequired(
            flightContext.getWorkingMap(),
            WorkspaceFlightMapKeys.EFFECTIVE_POLICIES,
            TpsPaoGetResult.class);

    if (cloudPlatform != CloudPlatform.GCP) {
      // We can only validate GCP right now. We'll need to add other platforms
      // to the ontology before we can validate them.
      return StepResult.getStepResultSuccess();
    }

    var validRegions = tpsApiDispatch.listValidRegionsForPao(effectivePolicies, cloudPlatform);

    var validationErrors =
        new ArrayList<>(
            ResourceValidationUtils.validateExistingResourceRegions(
                workspaceId, validRegions, cloudPlatform, resourceDao));

    if (!cloningInstructions.isReferenceClone()
        && destinationLocation != null
        && !validRegions.contains(destinationLocation.toLowerCase())) {
      validationErrors.add(
          String.format(
              "The specified destination location '%s' violates region policies",
              destinationLocation));
    }

    if (validationErrors.isEmpty()) {
      return StepResult.getStepResultSuccess();
    } else {
      throw new PolicyConflictException(validationErrors);
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to do. Continue undoing.
    return StepResult.getStepResultSuccess();
  }
}
