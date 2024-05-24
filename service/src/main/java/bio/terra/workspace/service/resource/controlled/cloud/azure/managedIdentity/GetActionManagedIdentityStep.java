package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gets an Azure Managed Identity for a user's 'action managed identity', which may be used to grant
 * users access to private ACRs. This step will insert a fetched action identity into the flight
 * context if one can be obtained from Sam.
 */
public class GetActionManagedIdentityStep implements Step {

  public static final String ACTION_IDENTITY = "ACTION_IDENTITY";
  private final SamService samService;
  private final WorkspaceService workspaceService;
  private final AuthenticatedUserRequest userRequest;
  private final UUID workspaceId;
  private final Logger logger = LoggerFactory.getLogger(GetActionManagedIdentityStep.class);

  public GetActionManagedIdentityStep(
      SamService samService,
      WorkspaceService workspaceService,
      UUID workspaceId,
      AuthenticatedUserRequest userRequest) {
    this.samService = samService;
    this.workspaceService = workspaceService;
    this.workspaceId = workspaceId;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    String billingProfileId = workspaceService.getWorkspace(workspaceId).spendProfileId().getId();
    logger.info(
        "Querying Sam for action identity using billing profile id: '{}'", billingProfileId);

    Optional<String> actionIdentity =
        samService.getActionIdentityForUser(billingProfileId, userRequest);
    if (actionIdentity.isPresent()) {
      logger.info("Fetched action managed identity '{}' from sam.", actionIdentity.get());
      putActionIdentityInContext(context, actionIdentity.get());
    } else {
      // NB: It is not an error if we fail to find an action identity,
      // it just means that the user doesn't have access to any private ACRs.
      logger.info("No action identities found to assign to batch pool.");
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  private void putActionIdentityInContext(FlightContext context, String actionIdentity) {
    context.getWorkingMap().put(ACTION_IDENTITY, actionIdentity);
  }
}
