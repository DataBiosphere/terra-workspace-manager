package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import bio.terra.stairway.*;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.IamRoleUtils;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.azure.core.management.exception.ManagementException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAzureStorageContainerStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(CreateAzureStorageContainerStep.class);
  private final ControlledResourceService controlledResourceService;
  private final String storageContainerName;
  private final UUID resourceId;

  public CreateAzureStorageContainerStep(
      String storageContainerName,
      UUID resourceId,
      ControlledResourceService controlledResourceService) {
    this.controlledResourceService = controlledResourceService;
    this.storageContainerName = storageContainerName;
    this.resourceId = resourceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    var destinationWorkspaceId =
        context
            .getInputParameters()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

    var sourceDatabase =
        FlightUtils.getRequired(
            context.getInputParameters(),
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE,
            ControlledAzureDatabaseResource.class);

    ControlledAzureStorageContainerResource controlledContainerResource =
        new ControlledAzureStorageContainerResource(
            WsmResourceFields.builder()
                .workspaceUuid(destinationWorkspaceId)
                .resourceId(resourceId)
                .name(storageContainerName)
                .cloningInstructions(CloningInstructions.COPY_NOTHING)
                .properties(Map.of())
                .createdByEmail(
                    Objects.requireNonNull(
                            context
                                .getInputParameters()
                                .get(
                                    JobMapKeys.AUTH_USER_INFO.getKeyName(),
                                    AuthenticatedUserRequest.class))
                        .getEmail())
                .state(WsmResourceState.CREATING)
                .build(),
            WsmControlledResourceFields.fromControlledResource(sourceDatabase),
            storageContainerName);

    ApiAzureStorageContainerCreationParameters destinationCreationParameters =
        new ApiAzureStorageContainerCreationParameters().storageContainerName(storageContainerName);
    ControlledResourceIamRole iamRole =
        IamRoleUtils.getIamRoleForAccessScope(sourceDatabase.getAccessScope());

    try {
      ControlledResource destinationContainer =
          controlledResourceService.createControlledResourceSync(
              controlledContainerResource, iamRole, userRequest, destinationCreationParameters);

      context
          .getWorkingMap()
          .put(
              WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_STORAGE_CONTAINER,
              destinationContainer);
    } catch (DuplicateResourceException e) {
      // We are catching DuplicateResourceException here since we check for the container's presence
      // earlier in the parent flight of this step and bail out if it already exists.
      // A duplicate resource being present in this context means we are in a retry and can move on
      logger.info(
          "Destination azure storage container already exists, resource_id = {}, name = {}",
          resourceId,
          storageContainerName);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // N.B. this method is called directly by DeleteAzureStorageContainerStep's `doStep` method.
    var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    var createdContainer =
        context
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_STORAGE_CONTAINER,
                ControlledAzureStorageContainerResource.class);
    try {
      if (createdContainer != null) {
        controlledResourceService.deleteControlledResourceSync(
            createdContainer.getWorkspaceId(),
            createdContainer.getResourceId(),
            /* forceDelete= */ false,
            userRequest);
      }
    } catch (ResourceNotFoundException e) {
      logger.info(
          "No storage container resource found {} in WSM, assuming it was previously removed.",
          createdContainer.getResourceId());
      return StepResult.getStepResultSuccess();
    } catch (ManagementException e) {
      if (AzureManagementExceptionUtils.isExceptionCode(
              e, AzureManagementExceptionUtils.RESOURCE_NOT_FOUND)
          || AzureManagementExceptionUtils.isExceptionCode(
              e, AzureManagementExceptionUtils.CONTAINER_NOT_FOUND)) {
        logger.info(
            "Container with ID {} not found in Azure, assuming it was previously removed. Result from Azure = {}",
            createdContainer.getResourceId(),
            e.getValue().getCode(),
            e);
        return StepResult.getStepResultSuccess();
      }
      logger.warn(
          "Deleting cloned container with ID {} failed, retrying.",
          createdContainer.getResourceId());
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }
}
