package bio.terra.workspace.service.workspace.flight.cloud.azure;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.model.SpendProfile;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.AzureCloudContextFields;
import bio.terra.workspace.service.workspace.model.CloudContextCommonFields;
import com.azure.core.management.AzureEnvironment;
import com.azure.resourcemanager.resources.ResourceManager;

public class ValidateMRGStep implements Step {
  private final CrlService crlService;
  private final AzureConfiguration azureConfig;
  private final SpendProfile spendProfile;

  public ValidateMRGStep(
      CrlService crlService, AzureConfiguration azureConfig, SpendProfile spendProfile) {
    this.crlService = crlService;
    this.azureConfig = azureConfig;
    this.spendProfile = spendProfile;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    AzureCloudContext azureCloudContext =
        new AzureCloudContext(
            new AzureCloudContextFields(
                spendProfile.tenantId().toString(),
                spendProfile.subscriptionId().toString(),
                spendProfile.managedResourceGroupId(),
                AzureEnvironment.AZURE),
            new CloudContextCommonFields(
                spendProfile.id(),
                WsmResourceState.CREATING,
                flightContext.getFlightId(),
                /* error= */ null));

    try {
      ResourceManager resourceManager =
          crlService.getResourceManager(azureCloudContext, azureConfig);
      // On the create path, read through the context fields, since the context is not yet in
      // the READY state.
      String resourceGroupId = azureCloudContext.getContextFields().getAzureResourceGroupId();
      resourceManager.resourceGroups().getByName(resourceGroupId);
    } catch (Exception azureError) {
      throw new CloudContextRequiredException("Invalid Azure cloud context", azureError);
    }

    // Store the cloud context in the working map. It is used to update
    // the DB in the common end step of the flight.
    flightContext.getWorkingMap().put(WorkspaceFlightMapKeys.CLOUD_CONTEXT, azureCloudContext);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
