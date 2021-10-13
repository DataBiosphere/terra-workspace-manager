package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gets an Azure IP, and fails if it already exists. This step is designed to run immediately before
 * {@link CreateAzureIpStep} to ensure idempotency of the create operation.
 */
public class GetAzureIpStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(GetAzureIpStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureIpResource resource;
  private final AzureCloudContext azureCloudContext;

  public GetAzureIpStep(
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
    try {
      computeManager
          .networkManager()
          .publicIpAddresses()
          .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), resource.getIpName());
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new DuplicateResourceException(
              String.format(
                  "An Azure IP with name %s already exists in resource group %s",
                  azureCloudContext.getAzureResourceGroupId(), resource.getIpName())));
    } catch (ManagementException e) {
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
