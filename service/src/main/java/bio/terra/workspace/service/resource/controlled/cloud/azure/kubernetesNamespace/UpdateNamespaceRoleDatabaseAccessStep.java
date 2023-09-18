package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.AzureDatabaseUtilsRunner;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateNamespaceRoleDatabaseAccessStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(UpdateNamespaceRoleDatabaseAccessStep.class);
  private final UUID workspaceId;
  private final AzureDatabaseUtilsRunner azureDatabaseUtilsRunner;
  private final ControlledAzureKubernetesNamespaceResource resource;
  private final ResourceDao resourceDao;
  private final UpdateNamespaceRoleDatabaseAccessStepMode mode;

  public UpdateNamespaceRoleDatabaseAccessStep(
      UUID workspaceId,
      AzureDatabaseUtilsRunner azureDatabaseUtilsRunner,
      ControlledAzureKubernetesNamespaceResource resource,
      ResourceDao resourceDao,
      UpdateNamespaceRoleDatabaseAccessStepMode mode) {
    this.workspaceId = workspaceId;
    this.azureDatabaseUtilsRunner = azureDatabaseUtilsRunner;
    this.resource = resource;
    this.resourceDao = resourceDao;
    this.mode = mode;
  }

  private String getPodName() {
    return mode.name() + "-namespace-role-" + this.resource.getResourceId();
  }

  private String getUndoPodName() {
    return "undo-" + getPodName();
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException {
    return switch (mode) {
      case REVOKE -> revokeAccess(context);
      case RESTORE -> restoreAccess(context);
    };
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // these are opposite of doStep
    return switch (mode) {
      case RESTORE -> revokeAccess(context);
      case REVOKE -> restoreAccess(context);
    };
  }

  private StepResult revokeAccess(FlightContext context) throws InterruptedException {
    logger.info("Revoking database access for namespace {}", resource.getKubernetesNamespace());
    azureDatabaseUtilsRunner.revokeNamespaceRoleAccess(
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class),
        workspaceId,
        getPodName(),
        resource.getKubernetesServiceAccount());
    return StepResult.getStepResultSuccess();
  }

  private StepResult restoreAccess(FlightContext context) throws InterruptedException {
    // only restore access if the resource is active to avoid accidentally granting access to a
    // resource that is not active
    if (isPrivateResourceActive()) {
      logger.info("Restoring database access for namespace {}", resource.getKubernetesNamespace());
      azureDatabaseUtilsRunner.restoreNamespaceRoleAccess(
          context
              .getWorkingMap()
              .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class),
          workspaceId,
          getUndoPodName(),
          resource.getKubernetesServiceAccount());
    } else {
      logger.info(
          "Skipping restore of database access for namespace {} because the resource is not active",
          resource.getKubernetesNamespace());
    }
    return StepResult.getStepResultSuccess();
  }

  private boolean isPrivateResourceActive() {
    return resourceDao
        .getResource(this.workspaceId, this.resource.getResourceId())
        .castToControlledResource()
        .getPrivateResourceState()
        .stream()
        .anyMatch(PrivateResourceState.ACTIVE::equals);
  }
}
