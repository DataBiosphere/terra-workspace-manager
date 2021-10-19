package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import org.apache.commons.lang3.StringUtils;

/**
 * Gets an Azure Disk, and fails if it already exists. This step is designed to run immediately before
 * {@link CreateAzureDiskStep} to ensure idempotency of the create operation.
 */
public class GetAzureDiskStep implements Step {
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureDiskResource resource;
  private final AzureCloudContext azureCloudContext;

  public GetAzureDiskStep(
      AzureConfiguration azureConfig,
      AzureCloudContext azureCloudContext,
      CrlService crlService,
      ControlledAzureDiskResource resource) {
    this.azureConfig = azureConfig;
    this.azureCloudContext = azureCloudContext;
    this.crlService = crlService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    try {
      computeManager
          .disks()
          .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), resource.getDiskName());
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new DuplicateResourceException(
              String.format(
                  "An Azure DISK with name %s already exists in resource group %s",
                  azureCloudContext.getAzureResourceGroupId(), resource.getDiskName())));
    } catch (ManagementException e) {
      // Azure error codes can be found here:
      // https://docs.microsoft.com/en-us/azure/azure-resource-manager/templates/common-deployment-errors
      if (StringUtils.equals(e.getValue().getCode(), "ResourceNotFound")) {
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo
    return StepResult.getStepResultSuccess();
  }
}
