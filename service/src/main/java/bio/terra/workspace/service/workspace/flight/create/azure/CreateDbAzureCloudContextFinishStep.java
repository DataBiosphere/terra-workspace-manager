package bio.terra.workspace.service.workspace.flight.create.azure;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.AZURE_MANAGED_RESOURCE_GROUP_ID;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.AZURE_SUBSCRIPTION_ID;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.AZURE_TENANT_ID;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.CloudContextHolder;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Updates the previously stored cloud context row, filling in the context JSON. */
public class CreateDbAzureCloudContextFinishStep implements Step {
  private final UUID workspaceUuid;
  private final AzureCloudContextService azureCloudContextService;
  private final FeatureConfiguration featureConfiguration;

  public CreateDbAzureCloudContextFinishStep(
      UUID workspaceUuid,
      AzureCloudContextService azureCloudContextService,
      FeatureConfiguration featureConfiguration) {
    this.workspaceUuid = workspaceUuid;
    this.azureCloudContextService = azureCloudContextService;
    this.featureConfiguration = featureConfiguration;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    AzureCloudContext azureCloudContext;
    if (featureConfiguration.isBpmEnabled()) {
      azureCloudContext =
          new AzureCloudContext(
              flightContext.getWorkingMap().get(AZURE_TENANT_ID, String.class),
              flightContext.getWorkingMap().get(AZURE_SUBSCRIPTION_ID, String.class),
              flightContext.getWorkingMap().get(AZURE_MANAGED_RESOURCE_GROUP_ID, String.class));
    } else {
      azureCloudContext =
          flightContext
              .getInputParameters()
              .get(JobMapKeys.REQUEST.getKeyName(), AzureCloudContext.class);
    }
    // Create the cloud context; throws if the context already exists.
    azureCloudContextService.createAzureCloudContextFinish(
        workspaceUuid, azureCloudContext, flightContext.getFlightId());

    CloudContextHolder cch = new CloudContextHolder();
    cch.setAzureCloudContext(azureCloudContext);

    FlightUtils.setResponse(flightContext, cch, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // We do not undo anything here. The create step will delete the row, if need be.
    return StepResult.getStepResultSuccess();
  }
}
