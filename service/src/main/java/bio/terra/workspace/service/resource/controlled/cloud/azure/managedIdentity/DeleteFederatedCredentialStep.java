package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.DeleteAzureControlledResourceStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteFederatedCredentialStep extends DeleteAzureControlledResourceStep {
  private static final Logger logger = LoggerFactory.getLogger(DeleteFederatedCredentialStep.class);
  public final String k8sNamespace;
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final MissingIdentityBehavior missingIdentityBehavior;

  public DeleteFederatedCredentialStep(
      String k8sNamespace,
      AzureConfiguration azureConfig,
      CrlService crlService,
      MissingIdentityBehavior missingIdentityBehavior) {
    this.k8sNamespace = k8sNamespace;
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.missingIdentityBehavior = missingIdentityBehavior;
  }

  @Override
  public StepResult deleteResource(FlightContext context) {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    var msiManager = crlService.getMsiManager(azureCloudContext, azureConfig);

    // guard against the managed identity not existing from a previous deletion
    if (!GetManagedIdentityStep.managedIdentityExistsInFlightWorkingMap(context)
        && missingIdentityBehavior == MissingIdentityBehavior.ALLOW_MISSING) {
      logger.info("Managed identity not found, but allowed to be missing");
      return StepResult.getStepResultSuccess();
    }

    String uamiName = GetManagedIdentityStep.getManagedIdentityName(context);
    msiManager
        .identities()
        .manager()
        .serviceClient()
        .getFederatedIdentityCredentials()
        .delete(azureCloudContext.getAzureResourceGroupId(), uamiName, k8sNamespace);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // humpty dumpty sat on a wall
    // humpty dumpty had a great fall
    // all the king's horses and all the king's men
    // couldn't undo this step again
    return StepResult.getStepResultSuccess();
  }
}
