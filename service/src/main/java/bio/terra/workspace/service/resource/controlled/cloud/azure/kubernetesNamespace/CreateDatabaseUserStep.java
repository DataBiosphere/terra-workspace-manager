package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.AzureDatabaseUtilsRunner;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetManagedIdentityStep;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateDatabaseUserStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateDatabaseUserStep.class);
  private final UUID workspaceId;
  private final AzureDatabaseUtilsRunner azureDatabaseUtilsRunner;
  private final ControlledAzureKubernetesNamespaceResource resource;
  private final ResourceDao resourceDao;

  public CreateDatabaseUserStep(
      UUID workspaceId,
      AzureDatabaseUtilsRunner azureDatabaseUtilsRunner,
      ControlledAzureKubernetesNamespaceResource resource,
      ResourceDao resourceDao) {
    this.workspaceId = workspaceId;
    this.azureDatabaseUtilsRunner = azureDatabaseUtilsRunner;
    this.resource = resource;
    this.resourceDao = resourceDao;
  }

  private String getCreatePodName() {
    return "create-user-" + this.resource.getKubernetesServiceAccount();
  }

  private String getUndoPodName() {
    return "undo-create-user-" + this.resource.getKubernetesServiceAccount();
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    var databaseNames =
        resource.getDatabases().stream()
            .map(this::getDatabaseName)
            .flatMap(Optional::stream)
            .collect(Collectors.toSet());

    if (databaseNames.size() != resource.getDatabases().size()) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new BadRequestException("Some databases were not found."));
    }

    logger.info(
        "Creating database user for namespace {} with databases {}",
        resource.getKubernetesNamespace(),
        databaseNames);
    azureDatabaseUtilsRunner.createUser(
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class),
        workspaceId,
        getCreatePodName(),
        resource.getKubernetesServiceAccount(),
        GetManagedIdentityStep.getManagedIdentityPrincipalId(context),
        databaseNames);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    try {
      azureDatabaseUtilsRunner.deleteUser(
          context
              .getWorkingMap()
              .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class),
          workspaceId,
          getUndoPodName(),
          resource.getKubernetesServiceAccount());
    } catch (Exception e) {
      logger.error(
          "Failed to undo create database user {}", resource.getKubernetesServiceAccount(), e);
    }
    return StepResult.getStepResultSuccess();
  }

  private Optional<String> getDatabaseName(UUID databaseResourceId) {
    try {
      var wsmResource = resourceDao.getResource(resource.getWorkspaceId(), databaseResourceId);
      if (wsmResource.getResourceType() == WsmResourceType.CONTROLLED_AZURE_DATABASE) {
        ControlledAzureDatabaseResource databaseResource =
            wsmResource.castByEnum(WsmResourceType.CONTROLLED_AZURE_DATABASE);
        return Optional.of(databaseResource.getDatabaseName());
      } else {
        logger.info("Resource {} is not a CONTROLLED_AZURE_DATABASE", databaseResourceId);
        return Optional.empty();
      }
    } catch (ResourceNotFoundException e) {
      logger.info("Resource {} not found", databaseResourceId);
      return Optional.empty();
    }
  }
}
