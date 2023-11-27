package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import static bio.terra.workspace.common.utils.FlightUtils.getInputParameterOrWorkingValue;
import static bio.terra.workspace.common.utils.FlightUtils.getRequired;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.common.utils.IamRoleUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureDatabaseCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.common.ClonedAzureResource;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.azure.core.management.exception.ManagementException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CopyControlledAzureDatabaseDefinitionStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(CopyControlledAzureDatabaseDefinitionStep.class);

  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;
  private final ControlledAzureDatabaseResource sourceDatabase;
  private final ControlledResourceService controlledResourceService;
  private final CloningInstructions resolvedCloningInstructions;
  private final ResourceDao resourceDao;

  public CopyControlledAzureDatabaseDefinitionStep(
      SamService samService,
      AuthenticatedUserRequest userRequest,
      ControlledAzureDatabaseResource sourceDatabase,
      ControlledResourceService controlledResourceService,
      CloningInstructions resolvedCloningInstructions,
      ResourceDao resourceDao) {
    this.samService = samService;
    this.userRequest = userRequest;
    this.sourceDatabase = sourceDatabase;
    this.controlledResourceService = controlledResourceService;
    this.resolvedCloningInstructions = resolvedCloningInstructions;
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    var inputParameters = flightContext.getInputParameters();
    var workingMap = flightContext.getWorkingMap();

    // get the inputs from the flight context
    var destinationResourceName =
        getInputParameterOrWorkingValue(
            flightContext,
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_NAME,
            WorkspaceFlightMapKeys.ResourceKeys.PREVIOUS_RESOURCE_NAME,
            String.class);
    var description =
        getInputParameterOrWorkingValue(
            flightContext,
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_DESCRIPTION,
            WorkspaceFlightMapKeys.ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION,
            String.class);
    var destinationWorkspaceId =
        getRequired(inputParameters, ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    var destinationResourceId =
        getRequired(inputParameters, ControlledResourceKeys.DESTINATION_RESOURCE_ID, UUID.class);

    var destinationManagedIdentity =
        Optional.ofNullable(
                resourceDao.getResourceByName(
                    destinationWorkspaceId, sourceDatabase.getDatabaseOwner()))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No managed identity %s to own database %s in destination workspace"
                            .formatted(sourceDatabase.getDatabaseOwner(), destinationResourceId)));

    var destinationDatabaseName =
        Optional.ofNullable(
                inputParameters.get(ControlledResourceKeys.DESTINATION_DATABASE_NAME, String.class))
            .orElse("db%s".formatted(UUID.randomUUID().toString().replace("-", "_")));

    ControlledAzureDatabaseResource destinationDatabaseResource =
        ControlledAzureDatabaseResource.builder()
            .databaseName(destinationDatabaseName)
            .databaseOwner(destinationManagedIdentity.getName())
            .common(
                sourceDatabase.buildControlledCloneResourceCommonFields(
                    destinationWorkspaceId,
                    destinationResourceId,
                    null,
                    destinationResourceName,
                    description,
                    samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest),
                    sourceDatabase.getRegion()))
            .build();

    ControlledResourceIamRole iamRole =
        IamRoleUtils.getIamRoleForAccessScope(sourceDatabase.getAccessScope());

    workingMap.put(ControlledResourceKeys.CLONED_RESOURCE_DEFINITION, destinationDatabaseResource);

    ApiAzureDatabaseCreationParameters destinationCreationParameters =
        new ApiAzureDatabaseCreationParameters().name(destinationResourceName);

    // create the database
    try {
      controlledResourceService.createControlledResourceSync(
          destinationDatabaseResource, iamRole, userRequest, destinationCreationParameters);
    } catch (DuplicateResourceException e) {
      // We are catching DuplicateResourceException here since we check for the container's presence
      // earlier in the parent flight of this step and bail out if it already exists.
      // A duplicate resource being present in this context means we are in a retry and can move on
      logger.info(
          "Destination azure database already exists, resource_id = {}, name = {}",
          destinationResourceId,
          destinationResourceName);
    }

    var databaseResult =
        new ClonedAzureResource(
            resolvedCloningInstructions,
            sourceDatabase.getWorkspaceId(),
            sourceDatabase.getResourceId(),
            destinationDatabaseResource);

    workingMap.put(WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE, databaseResult);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    var clonedDatabase =
        context
            .getWorkingMap()
            .get(
                ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
                ControlledAzureDatabaseResource.class);
    try {
      if (clonedDatabase != null) {
        controlledResourceService.deleteControlledResourceSync(
            clonedDatabase.getWorkspaceId(),
            clonedDatabase.getResourceId(),
            /* forceDelete= */ false,
            userRequest);
      }
    } catch (ResourceNotFoundException e) {
      logger.info(
          "No database resource found {} in WSM, assuming it was previously removed.",
          clonedDatabase.getResourceId());
      return StepResult.getStepResultSuccess();
    } catch (ManagementException e) {
      if (AzureManagementExceptionUtils.isExceptionCode(
          e, AzureManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
        logger.info(
            "Database with ID {} not found in Azure, assuming it was previously removed. Result from Azure = {}",
            clonedDatabase.getResourceId(),
            e.getValue().getCode(),
            e);
        return StepResult.getStepResultSuccess();
      }
      logger.warn(
          "Deleting cloned database with ID {} failed, retrying.", clonedDatabase.getResourceId());
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }
}
