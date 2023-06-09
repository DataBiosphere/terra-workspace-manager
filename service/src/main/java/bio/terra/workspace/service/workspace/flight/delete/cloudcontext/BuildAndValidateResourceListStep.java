package bio.terra.workspace.service.workspace.flight.delete.cloudcontext;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.RESOURCE_DELETE_FLIGHT_PAIR_LIST;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.CloudContextService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuildAndValidateResourceListStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(BuildAndValidateResourceListStep.class);
  private final AuthenticatedUserRequest userRequest;
  private final CloudContextService cloudContextService;
  private final SamService samService;
  private final UUID workspaceUuid;

  public BuildAndValidateResourceListStep(
      CloudContextService cloudContextService,
      SamService samService,
      AuthenticatedUserRequest userRequest,
      UUID workspaceUuid) {
    this.cloudContextService = cloudContextService;
    this.samService = samService;
    this.userRequest = userRequest;
    this.workspaceUuid = workspaceUuid;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // Get a list of resources in the order they are to be deleted. That order must be
    // respected throughout the rest of the flight.
    List<ControlledResource> resources = cloudContextService.makeOrderedResourceList(workspaceUuid);

    // Verify that the caller is allowed to delete all resources.
    for (ControlledResource resource : resources) {
      samService.checkAuthz(
          userRequest,
          resource.getCategory().getSamResourceName(),
          resource.getResourceId().toString(),
          SamConstants.SamControlledResourceActions.DELETE_ACTION);
    }

    // Generate pairs of (resourceId, flightId) maintaining the ordering
    List<ResourceDeleteFlightPair> resourcePairs = new ArrayList<>();
    for (ControlledResource resource : resources) {
      resourcePairs.add(
          new ResourceDeleteFlightPair(resource.getResourceId(), UUID.randomUUID().toString()));
    }
    context
        .getWorkingMap()
        .put(
            RESOURCE_DELETE_FLIGHT_PAIR_LIST,
            resourcePairs.toArray(new ResourceDeleteFlightPair[0]));
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
