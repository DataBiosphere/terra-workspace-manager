package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retrieve the cloud context, if applicable, and store it in the working map. Since this step only
 * reads data, it is idempotent
 */
public class GetCloudContextStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(GetCloudContextStep.class);
  private final UUID workspaceUuid;
  private final CloudPlatform cloudPlatform;
  private final GcpCloudContextService gcpCloudContextService;
  private final AzureCloudContextService azureCloudContextService;

  public GetCloudContextStep(
      UUID workspaceUuid,
      CloudPlatform cloudPlatform,
      GcpCloudContextService gcpCloudContextService,
      AzureCloudContextService azureCloudContextService) {
    this.workspaceUuid = workspaceUuid;
    this.cloudPlatform = cloudPlatform;
    this.gcpCloudContextService = gcpCloudContextService;
    this.azureCloudContextService = azureCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    AuthenticatedUserRequest userRequest =
        FlightUtils.getRequired(
            flightContext.getInputParameters(),
            JobMapKeys.AUTH_USER_INFO.getKeyName(),
            AuthenticatedUserRequest.class);

    // Get the cloud context and store it in the working map
    switch (cloudPlatform) {
      case AZURE:
        AzureCloudContext azureCloudContext =
            azureCloudContextService.getRequiredAzureCloudContext(workspaceUuid);
        flightContext
            .getWorkingMap()
            .put(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, azureCloudContext);
        break;

      case GCP:
        GcpCloudContext gcpCloudContext =
            gcpCloudContextService.getRequiredGcpCloudContext(workspaceUuid, userRequest);
        flightContext
            .getWorkingMap()
            .put(ControlledResourceKeys.GCP_CLOUD_CONTEXT, gcpCloudContext);
        break;

      case ANY:
      default:
        // There cannot be an ANY resource that is also a controlled resource.
        throw new InternalLogicException(
            "Invalid cloud platform for controlled resource: " + cloudPlatform);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
