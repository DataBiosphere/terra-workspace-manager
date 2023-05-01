package bio.terra.workspace.service.workspace.flight.cloud.azure;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.resources.ResourceManager;

public class ValidateMRGStep implements Step {
  private final CrlService crlService;
  private final AzureConfiguration azureConfig;

  public ValidateMRGStep(CrlService crlService, AzureConfiguration azureConfig) {
    this.crlService = crlService;
    this.azureConfig = azureConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    FlightMap workingMap = flightContext.getWorkingMap();
    var spendProfile =
      FlightUtils.getRequired(workingMap, WorkspaceFlightMapKeys.SPEND_PROFILE, SpendProfile.class);

    AzureCloudContext azureCloudContext =
      new AzureCloudContext(
        spendProfile.tenantId().toString(),
        spendProfile.subscriptionId().toString(),
        spendProfile.managedResourceGroupId(),
        /*commonFields=*/ null);

    try {
      ResourceManager resourceManager =
          crlService.getResourceManager(azureCloudContext, azureConfig);
      resourceManager.resourceGroups().getByName(azureCloudContext.getAzureResourceGroupId());
    } catch (Exception azureError) {
      throw new CloudContextRequiredException("Invalid Azure cloud context", azureError);
    }

    // Store the cloud context in the working map. It is used to update
    // the DB in the common end step of the flight.
    workingMap.put(WorkspaceFlightMapKeys.CLOUD_CONTEXT, azureCloudContext);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
