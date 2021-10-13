package bio.terra.workspace.service.workspace.flight.create.azure;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores the previously generated Google Project Id in the {@link WorkspaceDao} as the Google cloud
 * context for the workspace.
 */
public class ValidateMRGStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(ValidateMRGStep.class);
  private final CrlService crlService;
  private final AzureConfiguration azureConfig;

  public ValidateMRGStep(CrlService crlService, AzureConfiguration azureConfig) {
    this.crlService = crlService;
    this.azureConfig = azureConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    AzureCloudContext azureCloudContext =
        flightContext
            .getInputParameters()
            .get(JobMapKeys.REQUEST.getKeyName(), AzureCloudContext.class);

    try {
      ResourceManager resourceManager =
          crlService.getResourceManager(azureCloudContext, azureConfig);
      resourceManager.resourceGroups().getByName(azureCloudContext.getAzureResourceGroupId());
    } catch (Exception azureError) {
      throw new CloudContextRequiredException("Invalid Azure cloud context", azureError);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
