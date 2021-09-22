package bio.terra.workspace.service.resource.controlled.flight.create;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.cloudres.azure.resourcemanager.compute.data.CreatePublicIpRequestData;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.generated.model.ApiAzureIpCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledAzureIpResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.network.models.IpAllocationMethod;
import com.azure.resourcemanager.network.models.PublicIpAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAzureIpStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(CreateGcsBucketStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureIpResource resource;
  private final WorkspaceService workspaceService;
  private final AzureCloudContext azureCloudContext;

  public CreateAzureIpStep(
      AzureConfiguration azureConfig,
      AzureCloudContext azureCloudContext,
      CrlService crlService,
      ControlledAzureIpResource resource,
      WorkspaceService workspaceService) {
    this.azureConfig = azureConfig;
    this.azureCloudContext = azureCloudContext;
    this.crlService = crlService;
    this.resource = resource;
    this.workspaceService = workspaceService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap inputMap = context.getInputParameters();
    ApiAzureIpCreationParameters creationParameters =
        inputMap.get(CREATION_PARAMETERS, ApiAzureIpCreationParameters.class);

    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    // Don't try to create it if it already exists. At this point the assumption is
    // this is a redo and this step created it already.
    PublicIpAddress existingIP;

    try {
      existingIP =
          computeManager
              .networkManager()
              .publicIpAddresses()
              .getByResourceGroup(
                  azureCloudContext.getAzureResourceGroupId(), resource.getIpName());
    } catch (com.azure.core.management.exception.ManagementException e) {
      // TODO: can we cast this to be more descriptive? Only indication its not found is the message
      // has a 404
      existingIP = null;
    }

    if (existingIP == null) {
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
                      .setRegion(resource.getRegion())
                      .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                      .setIpAllocationMethod(IpAllocationMethod.DYNAMIC)
                      .build()));
    } else {
      context
          .getWorkingMap()
          .put(WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_IP_EXISTED, true);
      logger.info("Ip {} already exists. Continuing.", resource.getIpName());
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    boolean didExist =
        context
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_IP_EXISTED, Boolean.class);

    if (didExist) {
      logger.info("The azure IP existed prior to this step running. Nothing to undo.");
    } else {
      computeManager
          .networkManager()
          .publicIpAddresses()
          .deleteByResourceGroup(azureCloudContext.getAzureResourceGroupId(), resource.getIpName());
    }
    return StepResult.getStepResultSuccess();
  }
}
