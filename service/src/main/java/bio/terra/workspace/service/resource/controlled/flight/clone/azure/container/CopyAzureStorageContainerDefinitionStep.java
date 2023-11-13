package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import static bio.terra.workspace.common.utils.FlightUtils.getInputParameterOrWorkingValue;
import static bio.terra.workspace.common.utils.FlightUtils.getRequired;
import static bio.terra.workspace.service.resource.controlled.flight.clone.workspace.WorkspaceCloneUtils.buildDestinationControlledAzureContainer;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.common.utils.IamRoleUtils;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.common.ClonedAzureResource;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.azure.core.management.exception.ManagementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CopyAzureStorageContainerDefinitionStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(CopyAzureStorageContainerDefinitionStep.class);

  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;
  private final ControlledAzureStorageContainerResource sourceContainer;
  private final ControlledResourceService controlledResourceService;
  private final CloningInstructions resolvedCloningInstructions;

  public CopyAzureStorageContainerDefinitionStep(
      SamService samService,
      AuthenticatedUserRequest userRequest,
      ControlledAzureStorageContainerResource sourceContainer,
      ControlledResourceService controlledResourceService,
      CloningInstructions resolvedCloningInstructions) {
    this.samService = samService;
    this.userRequest = userRequest;
    this.sourceContainer = sourceContainer;
    this.controlledResourceService = controlledResourceService;
    this.resolvedCloningInstructions = resolvedCloningInstructions;
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
        getRequired(
            inputParameters,
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
            UUID.class);
    var destinationContainerName =
        getRequired(
            inputParameters, ControlledResourceKeys.DESTINATION_CONTAINER_NAME, String.class);
    var destinationResourceId =
        getRequired(
            inputParameters,
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID,
            UUID.class);
    var userRequest =
        getRequired(
            inputParameters,
            JobMapKeys.AUTH_USER_INFO.getKeyName(),
            AuthenticatedUserRequest.class);

    ApiAzureLandingZoneDeployedResource sharedAccount =
        getRequired(
            workingMap,
            ControlledResourceKeys.SHARED_STORAGE_ACCOUNT,
            ApiAzureLandingZoneDeployedResource.class);

    // we omit the storage account ID since we only support cloning to a landing zone backed storage
    // account
    ApiAzureStorageContainerCreationParameters destinationCreationParameters =
        new ApiAzureStorageContainerCreationParameters()
            .storageContainerName(destinationContainerName);

    ControlledAzureStorageContainerResource destinationContainerResource =
        buildDestinationControlledAzureContainer(
            sourceContainer,
            destinationWorkspaceId,
            destinationResourceId,
            destinationResourceName,
            description,
            destinationCreationParameters.getStorageContainerName(),
            samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest),
            sharedAccount.getRegion());
    ControlledResourceIamRole iamRole =
        IamRoleUtils.getIamRoleForAccessScope(sourceContainer.getAccessScope());

    // save the container definition for downstream steps
    workingMap.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
        destinationContainerResource);

    // create the container
    try {
      controlledResourceService.createControlledResourceSync(
          destinationContainerResource, iamRole, userRequest, destinationCreationParameters);
    } catch (DuplicateResourceException e) {
      // We are catching DuplicateResourceException here since we check for the container's presence
      // earlier in the parent flight of this step and bail out if it already exists.
      // A duplicate resource being present in this context means we are in a retry and can move on
      logger.info(
          "Destination azure storage container already exists, resource_id = {}, name = {}",
          destinationResourceId,
          destinationResourceName);
    }

    var containerResult =
        new ClonedAzureResource(
            resolvedCloningInstructions,
            sourceContainer.getWorkspaceId(),
            sourceContainer.getResourceId(),
            destinationContainerResource);

    workingMap.put(ControlledResourceKeys.CLONED_RESOURCE, containerResult);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    var clonedContainer =
        context
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
                ControlledAzureStorageContainerResource.class);
    try {
      if (clonedContainer != null) {
        controlledResourceService.deleteControlledResourceSync(
            clonedContainer.getWorkspaceId(),
            clonedContainer.getResourceId(),
            /* forceDelete= */ false,
            userRequest);
      }
    } catch (ResourceNotFoundException e) {
      logger.info(
          "No storage container resource found {} in WSM, assuming it was previously removed.",
          clonedContainer.getResourceId());
      return StepResult.getStepResultSuccess();
    } catch (ManagementException e) {
      if (AzureManagementExceptionUtils.isExceptionCode(
              e, AzureManagementExceptionUtils.RESOURCE_NOT_FOUND)
          || AzureManagementExceptionUtils.isExceptionCode(
              e, AzureManagementExceptionUtils.CONTAINER_NOT_FOUND)) {
        logger.info(
            "Container with ID {} not found in Azure, assuming it was previously removed. Result from Azure = {}",
            clonedContainer.getResourceId(),
            e.getValue().getCode(),
            e);
        return StepResult.getStepResultSuccess();
      }
      logger.warn(
          "Deleting cloned container with ID {} failed, retrying.",
          clonedContainer.getResourceId());
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }
}
