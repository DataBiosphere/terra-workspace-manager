package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.cloudres.azure.resourcemanager.compute.data.CreateDiskRequestData;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates an Azure Disk. Designed to run directly after {@link GetAzureDiskStep}. */
public class CreateAzureDiskStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(CreateAzureDiskStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureDiskResource resource;

  public CreateAzureDiskStep(
      AzureConfiguration azureConfig, CrlService crlService, ControlledAzureDiskResource resource) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    try {
      computeManager
          .disks()
          .define(resource.getDiskName())
          .withRegion(resource.getRegion())
          .withExistingResourceGroup(azureCloudContext.getAzureResourceGroupId())
          .withData()
          .withSizeInGB(resource.getSize())
          .withTag("workspaceId", resource.getWorkspaceId().toString())
          .withTag("resourceId", resource.getResourceId().toString())
          .create(
              Defaults.buildContext(
                  CreateDiskRequestData.builder()
                      .setName(resource.getDiskName())
                      .setRegion(Region.fromName(resource.getRegion()))
                      .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                      .setTenantId(azureCloudContext.getAzureTenantId())
                      .setSubscriptionId(azureCloudContext.getAzureSubscriptionId())
                      .setSize(resource.getSize())
                      .build()));
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have created this resource. In all
      // other cases, surface the exception and attempt to retry.
      if (AzureManagementExceptionUtils.isExceptionCode(
          e, AzureManagementExceptionUtils.CONFLICT)) {
        logger.info(
            "Azure Disk {} in managed resource group {} already exists",
            resource.getDiskName(),
            azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    try {
      computeManager
          .disks()
          .deleteByResourceGroup(
              azureCloudContext.getAzureResourceGroupId(), resource.getDiskName());
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have deleted this resource.
      if (AzureManagementExceptionUtils.isExceptionCode(
          e, AzureManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
        logger.info(
            "Azure Disk {} in managed resource group {} already deleted",
            resource.getDiskName(),
            azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }
}
