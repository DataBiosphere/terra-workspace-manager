package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.cloudres.azure.resourcemanager.compute.data.CreatePublicIpRequestData;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledAzureIpResource;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.network.models.IpAllocationMethod;

/** Creates an Azure IP address. Designed to run directly after {@link GetAzureIpStep}. */
public class CreateAzureIpStep implements Step {

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureIpResource resource;
  private final AzureCloudContext azureCloudContext;

  public CreateAzureIpStep(
      AzureConfiguration azureConfig,
      AzureCloudContext azureCloudContext,
      CrlService crlService,
      ControlledAzureIpResource resource) {
    this.azureConfig = azureConfig;
    this.azureCloudContext = azureCloudContext;
    this.crlService = crlService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    computeManager
        .networkManager()
        .publicIpAddresses()
        .define(resource.getIpName())
        .withRegion(resource.getRegion())
        .withExistingResourceGroup(azureCloudContext.getAzureResourceGroupId())
        .withDynamicIP()
        .withTag("workspaceId", resource.getWorkspaceId().toString())
        .withTag("resourceId", resource.getResourceId().toString())
        .create(
            Defaults.buildContext(
                CreatePublicIpRequestData.builder()
                    .setName(resource.getIpName())
                    .setRegion(Region.fromName(resource.getRegion()))
                    .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                    .setIpAllocationMethod(IpAllocationMethod.DYNAMIC)
                    .build()));

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    computeManager
        .networkManager()
        .publicIpAddresses()
        .deleteByResourceGroup(azureCloudContext.getAzureResourceGroupId(), resource.getIpName());
    return StepResult.getStepResultSuccess();
  }
}
