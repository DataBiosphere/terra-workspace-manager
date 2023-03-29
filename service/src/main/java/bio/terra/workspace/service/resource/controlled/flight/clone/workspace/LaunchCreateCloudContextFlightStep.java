package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.FlightNotFoundException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstant;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.Objects;
import java.util.Optional;

public class LaunchCreateCloudContextFlightStep implements Step {

  private final WorkspaceService workspaceService;
  private final GcpCloudContextService gcpCloudContextService;
  private final TpsApiDispatch tpsApiDispatch;
  private final CloudPlatform cloudPlatform;
  private final String flightIdKey;

  public LaunchCreateCloudContextFlightStep(
      WorkspaceService workspaceService,
      GcpCloudContextService gcpCloudContextService,
      TpsApiDispatch tpsApiDispatch,
      CloudPlatform cloudPlatform,
      String flightIdKey) {
    this.workspaceService = workspaceService;
    this.gcpCloudContextService = gcpCloudContextService;
    this.tpsApiDispatch = tpsApiDispatch;
    this.cloudPlatform = cloudPlatform;
    this.flightIdKey = flightIdKey;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntries(
        context.getInputParameters(),
        ControlledResourceKeys.SOURCE_WORKSPACE_ID,
        JobMapKeys.AUTH_USER_INFO.getKeyName(),
        JobMapKeys.REQUEST.getKeyName());
    validateRequiredEntries(context.getWorkingMap(), flightIdKey);

    var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    var destinationWorkspace =
        Objects.requireNonNull(
            context.getInputParameters().get(JobMapKeys.REQUEST.getKeyName(), Workspace.class));

    var cloudContextJobId = context.getWorkingMap().get(flightIdKey, String.class);

    boolean flightAlreadyExists;
    try {
      context.getStairway().getFlightState(cloudContextJobId);
      flightAlreadyExists = true;
    } catch (FlightNotFoundException e) {
      flightAlreadyExists = false;
    } catch (DatabaseOperationException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    // if we already have a flight, don't launch another one
    if (!flightAlreadyExists) {
      if (CloudPlatform.AZURE == cloudPlatform) {
        workspaceService.createAzureCloudContext(
            destinationWorkspace,
            cloudContextJobId,
            userRequest,
            null,
            context
                .getInputParameters()
                .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class));
      } else {
        workspaceService.createGcpCloudContext(
            destinationWorkspace, cloudContextJobId, userRequest);

        // Use the default zone of the workspace if it exists. Otherwise, use the default zone
        // constant.
        String destinationDefaultZone =
            gcpCloudContextService
                .getGcpCloudContext(destinationWorkspace.getWorkspaceId())
                .orElse(new GcpCloudContext())
                .getGcpDefaultZone();

        gcpCloudContextService.updateGcpCloudContext(
            tpsApiDispatch,
            destinationWorkspace.getWorkspaceId(),
            Optional.ofNullable(destinationDefaultZone).orElse(GcpResourceConstant.DEFAULT_ZONE),
            userRequest);
      }
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Destroy the created workspace and cloud context. The only time we want to run the workspace
   * delete is if the create workspace subflight succeeded, but a later step in the flight fails.
   * The failure of the create workspace flight will have deleted the workspace on undo and we don't
   * get here. But if we fail to create the flight context, we want to delete the workspace.
   */
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    var destinationWorkspace =
        context.getInputParameters().get(JobMapKeys.REQUEST.getKeyName(), Workspace.class);
    if (destinationWorkspace != null && userRequest != null) {
      // delete workspace is idempotent, so it's safe to call it more than once
      workspaceService.deleteWorkspace(destinationWorkspace, userRequest);
    } // otherwise, if it never got created, that's fine too
    return StepResult.getStepResultSuccess();
  }
}
