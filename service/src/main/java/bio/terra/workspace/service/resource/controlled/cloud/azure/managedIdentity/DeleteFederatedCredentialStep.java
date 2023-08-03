package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import static bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetFederatedIdentityStep.FEDERATED_IDENTITY_EXISTS;

import bio.terra.landingzone.stairway.flight.utils.FlightUtils;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.msi.MsiManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteFederatedCredentialStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(DeleteFederatedCredentialStep.class);
  public final String k8sNamespace;
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;

  public DeleteFederatedCredentialStep(
      String k8sNamespace, AzureConfiguration azureConfig, CrlService crlService) {
    this.k8sNamespace = k8sNamespace;
    this.azureConfig = azureConfig;
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    if (!FlightUtils.getRequired(
        context.getWorkingMap(), FEDERATED_IDENTITY_EXISTS, Boolean.class)) {
      logger.info("Federated identity already gone");
      return StepResult.getStepResultSuccess();
    }

    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    var msiManager = crlService.getMsiManager(azureCloudContext, azureConfig);

    // the code above was lookup and setup, now we are ready to create the federated identity and
    // k8s service account
    deleteFederatedCredentials(
        msiManager,
        k8sNamespace,
        GetManagedIdentityStep.getManagedIdentityName(context),
        k8sNamespace);
    return StepResult.getStepResultSuccess();
  }

  private void deleteFederatedCredentials(
      MsiManager msiManager, String mrgName, String uamiName, String k8sNamespace) {
    msiManager
        .identities()
        .manager()
        .serviceClient()
        .getFederatedIdentityCredentials()
        .delete(mrgName, uamiName, k8sNamespace);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
