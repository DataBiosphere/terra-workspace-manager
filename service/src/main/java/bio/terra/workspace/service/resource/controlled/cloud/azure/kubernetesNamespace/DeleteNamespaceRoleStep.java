package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.service.resource.controlled.cloud.azure.DeleteAzureControlledResourceStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.AzureDatabaseUtilsRunner;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteNamespaceRoleStep
    extends DeleteAzureControlledResourceStep<ControlledAzureKubernetesNamespaceResource> {
  private static final Logger logger = LoggerFactory.getLogger(DeleteNamespaceRoleStep.class);
  private final UUID workspaceId;
  private final AzureDatabaseUtilsRunner azureDatabaseUtilsRunner;

  public DeleteNamespaceRoleStep(
      UUID workspaceId,
      AzureDatabaseUtilsRunner azureDatabaseUtilsRunner,
      ControlledAzureKubernetesNamespaceResource resource) {
    super(resource);
    this.workspaceId = workspaceId;
    this.azureDatabaseUtilsRunner = azureDatabaseUtilsRunner;
  }

  private String getDeletePodName() {
    return "delete-namespace-role-" + this.resource.getResourceId();
  }

  @Override
  public StepResult deleteResource(FlightContext context) throws InterruptedException {
    logger.info("Deleting namespace role for namespace {}", resource.getKubernetesNamespace());
    try {
      azureDatabaseUtilsRunner.deleteNamespaceRole(
          context
              .getWorkingMap()
              .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class),
          workspaceId,
          getDeletePodName(),
          resource.getKubernetesServiceAccount());
    } catch (IllegalStateException e) {
      // no shared database means no role to delete
      if (e.getMessage().contains("No shared database found")) {
        return StepResult.getStepResultSuccess();
      }
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // nothing to undo
    return StepResult.getStepResultSuccess();
  }
}
