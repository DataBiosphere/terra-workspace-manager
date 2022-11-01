package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import static bio.terra.workspace.service.resource.controlled.flight.clone.workspace.WorkspaceCloneUtils.buildDestinationControlledAzureContainer;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.IamRoleUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.generated.model.ApiClonedControlledAzureStorageContainer;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureStorageContainer;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class CopyAzureStorageContainerDefinitionStep implements Step {

  private final AuthenticatedUserRequest userRequest;
  private final ControlledAzureStorageContainerResource sourceContainer;
  private final ControlledResourceService controlledResourceService;
  private final CloningInstructions resolvedCloningInstructions;

  public CopyAzureStorageContainerDefinitionStep(
      AuthenticatedUserRequest userRequest,
      ResourceDao resourceDao,
      LandingZoneApiDispatch landingZoneApiDispatch,
      ControlledAzureStorageContainerResource sourceContainer,
      ControlledResourceService controlledResourceService,
      CloningInstructions resolvedCloningInstructions) {
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
    validateInputs(inputParameters, workingMap);

    // get the inputs from the flight context
    var destinationResourceName =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_NAME,
            WorkspaceFlightMapKeys.ResourceKeys.PREVIOUS_RESOURCE_NAME,
            String.class);
    var description =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_DESCRIPTION,
            WorkspaceFlightMapKeys.ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION,
            String.class);
    var destinationWorkspaceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    var destinationContainerName =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_CONTAINER_NAME, String.class);
    var destinationResourceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID, UUID.class);
    var destStorageAccountId =
        flightContext
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys
                    .DESTINATION_STORAGE_ACCOUNT_RESOURCE_ID,
                UUID.class);

    ControlledAzureStorageContainerResource destinationContainerResource =
        buildDestinationControlledAzureContainer(
            sourceContainer,
            destStorageAccountId,
            destinationWorkspaceId,
            destinationResourceId,
            destinationResourceName,
            description,
            destinationContainerName);
    ApiAzureStorageContainerCreationParameters destinationCreationParameters =
        new ApiAzureStorageContainerCreationParameters()
            .storageContainerName(destinationContainerName)
            .storageAccountId(destStorageAccountId);
    ControlledResourceIamRole iamRole =
        IamRoleUtils.getIamRoleForAccessScope(sourceContainer.getAccessScope());

    // create the container
    ControlledAzureStorageContainerResource clonedContainer =
        controlledResourceService
            .createControlledResourceSync(
                destinationContainerResource, iamRole, userRequest, destinationCreationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER);

    // save the container definition for downstream steps
    workingMap.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE_DEFINITION, clonedContainer);

    var apiCreatedContainer =
        new ApiCreatedControlledAzureStorageContainer()
            .azureStorageContainer(clonedContainer.toApiResource())
            .resourceId(destinationContainerResource.getResourceId());

    var apiContainerResult =
        new ApiClonedControlledAzureStorageContainer()
            .effectiveCloningInstructions(resolvedCloningInstructions.toApiModel())
            .storageContainer(apiCreatedContainer)
            .sourceWorkspaceId(sourceContainer.getWorkspaceId())
            .sourceResourceId(sourceContainer.getResourceId());

    if (resolvedCloningInstructions.equals(CloningInstructions.COPY_DEFINITION)) {
      FlightUtils.setResponse(flightContext, apiContainerResult, HttpStatus.OK);
    }

    return StepResult.getStepResultSuccess();
  }

  private void validateInputs(FlightMap inputParameters, FlightMap workingMap) {
    FlightUtils.validateRequiredEntries(
        inputParameters,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_CONTAINER_NAME);
    FlightUtils.validateRequiredEntries(
        workingMap,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_STORAGE_ACCOUNT_RESOURCE_ID);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    var clonedContainer =
        context
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
                ControlledAzureStorageContainerResource.class);
    if (clonedContainer != null) {
      controlledResourceService.deleteControlledResourceSync(
          clonedContainer.getWorkspaceId(), clonedContainer.getResourceId(), userRequest);
    }
    return StepResult.getStepResultSuccess();
  }
}
