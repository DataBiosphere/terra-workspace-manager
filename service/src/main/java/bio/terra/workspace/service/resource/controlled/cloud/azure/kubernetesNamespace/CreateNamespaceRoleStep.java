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
import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateNamespaceRoleStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateNamespaceRoleStep.class);
  private final UUID workspaceId;
  private final AzureDatabaseUtilsRunner azureDatabaseUtilsRunner;
  private final ControlledAzureKubernetesNamespaceResource resource;
  private final ResourceDao resourceDao;

  public CreateNamespaceRoleStep(
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
    return "create-namespace-role-" + this.resource.getResourceId();
  }

  private String getUndoPodName() {
    return "undo-create-namespace-role-" + this.resource.getResourceId();
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    var databaseResolutions =
        resource.getDatabases().stream()
            .map(this::getDatabaseResource)
            .map(this::validateDatabaseAccess)
            .toList();

    var errorMessages =
        databaseResolutions.stream()
            .filter(r -> r.errorMessage().isPresent())
            .map(r -> r.errorMessage().get())
            .toList();
    if (!errorMessages.isEmpty()) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new BadRequestException(
              "Could not connect to database(s): [%s]"
                  .formatted(String.join(", ", errorMessages))));
    }

    var databaseNames =
        databaseResolutions.stream()
            .map(r -> r.resource().orElseThrow().getDatabaseName())
            .collect(Collectors.toSet());

    if (databaseNames.size() != resource.getDatabases().size()) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new BadRequestException("Some databases were not found."));
    }

    logger.info(
        "Creating namespace role for namespace {} with databases {}",
        resource.getKubernetesNamespace(),
        databaseNames);
    azureDatabaseUtilsRunner.createNamespaceRole(
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
      azureDatabaseUtilsRunner.deleteNamespaceRole(
          context
              .getWorkingMap()
              .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class),
          workspaceId,
          getUndoPodName(),
          resource.getKubernetesServiceAccount());
    } catch (Exception e) {
      logger.error(
          "Failed to undo create namespace role {}", resource.getKubernetesServiceAccount(), e);
    }
    return StepResult.getStepResultSuccess();
  }

  @VisibleForTesting
  DatabaseResolution getDatabaseResource(UUID databaseResourceId) {
    try {
      var wsmResource = resourceDao.getResource(resource.getWorkspaceId(), databaseResourceId);
      if (wsmResource.getResourceType() == WsmResourceType.CONTROLLED_AZURE_DATABASE) {
        ControlledAzureDatabaseResource databaseResource =
            wsmResource.castByEnum(WsmResourceType.CONTROLLED_AZURE_DATABASE);
        return new DatabaseResolution(
            databaseResourceId, Optional.of(databaseResource), Optional.empty());
      } else {
        logger.info("Resource {} is not a CONTROLLED_AZURE_DATABASE", databaseResourceId);
        return new DatabaseResolution(
            databaseResourceId,
            Optional.empty(),
            Optional.of(
                "Resource %s is not a CONTROLLED_AZURE_DATABASE".formatted(databaseResourceId)));
      }
    } catch (ResourceNotFoundException e) {
      return new DatabaseResolution(
          databaseResourceId,
          Optional.empty(),
          Optional.of("Resource %s does not exist".formatted(databaseResourceId)));
    }
  }

  @VisibleForTesting
  DatabaseResolution validateDatabaseAccess(DatabaseResolution resolution) {
    if (resolution.resource().isEmpty()) {
      return resolution;
    }
    var dbResource = resolution.resource().get();
    return switch (dbResource.getAccessScope()) {
      case ACCESS_SCOPE_SHARED -> validateSharedScopeAccess(resolution, dbResource);
      case ACCESS_SCOPE_PRIVATE -> validatePrivateScopeAccess(resolution, dbResource);
    };
  }

  private DatabaseResolution validatePrivateScopeAccess(
      DatabaseResolution resolution, ControlledAzureDatabaseResource dbResource) {
    if (dbResource.getAssignedUser().equals(resource.getAssignedUser())) {
      return resolution;
    } else {
      return new DatabaseResolution(
          resolution.resourceId(),
          resolution.resource(),
          Optional.of(
              "Connection to private database %s is only permitted to assigned user"
                  .formatted(resolution.resourceId())));
    }
  }

  private DatabaseResolution validateSharedScopeAccess(
      DatabaseResolution resolution, ControlledAzureDatabaseResource dbResource) {
    if (dbResource.getAllowAccessForAllWorkspaceUsers()
        || dbResource.getDatabaseOwner().equals(resource.getManagedIdentity())) {
      return resolution;
    } else {
      return new DatabaseResolution(
          resolution.resourceId(),
          resolution.resource(),
          Optional.of(
              "Connection to database %s is only permitted to owner identity"
                  .formatted(resolution.resourceId())));
    }
  }
}

record DatabaseResolution(
    UUID resourceId,
    Optional<ControlledAzureDatabaseResource> resource,
    Optional<String> errorMessage) {}
