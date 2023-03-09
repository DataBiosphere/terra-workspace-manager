package bio.terra.workspace.service.resource.controlled.flight.create;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.GCP_CLOUD_CONTEXT;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import java.util.UUID;

/**
 * Retrieve the cloud context, if applicable, and store it in the working map. Since this step only
 * reads data, it is idempotent
 */
public class GetCloudContextStep implements Step {

  private final UUID workspaceUuid;
  private final CloudPlatform cloudPlatform;
  private final GcpCloudContextService gcpCloudContextService;
  private final AzureCloudContextService azureCloudContextService;
  private final AwsCloudContextService awsCloudContextService;

  public GetCloudContextStep(
      UUID workspaceUuid,
      CloudPlatform cloudPlatform,
      GcpCloudContextService gcpCloudContextService,
      AzureCloudContextService azureCloudContextService,
      AwsCloudContextService awsCloudContextService) {
    this.workspaceUuid = workspaceUuid;
    this.cloudPlatform = cloudPlatform;
    this.gcpCloudContextService = gcpCloudContextService;
    this.azureCloudContextService = azureCloudContextService;
    this.awsCloudContextService = awsCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {

    FlightMap workingMap = flightContext.getWorkingMap();
    // Get the cloud context and store it in the working map
    switch (cloudPlatform) {
      case GCP -> {
        if (workingMap.get(GCP_CLOUD_CONTEXT, GcpCloudContext.class) == null) {
          AuthenticatedUserRequest userRequest =
              FlightUtils.getRequired(
                  flightContext.getInputParameters(),
                  JobMapKeys.AUTH_USER_INFO.getKeyName(),
                  AuthenticatedUserRequest.class);
          workingMap.put(
              GCP_CLOUD_CONTEXT,
              gcpCloudContextService.getRequiredGcpCloudContext(workspaceUuid, userRequest));
        }
      }
      case AZURE -> {
        if (workingMap.get(AZURE_CLOUD_CONTEXT, AzureCloudContext.class) == null) {
          workingMap.put(
              AZURE_CLOUD_CONTEXT,
              azureCloudContextService.getRequiredAzureCloudContext(workspaceUuid));
        }
      }
      case AWS -> {
        if (workingMap.get(AWS_CLOUD_CONTEXT, AwsCloudContext.class) == null) {
          workingMap.put(
              AWS_CLOUD_CONTEXT, awsCloudContextService.getRequiredAwsCloudContext(workspaceUuid));
        }
      }
      case ANY -> {
        // A flexible resource is the only controlled resource that has an ANY CloudPlatform.
      }
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
