package bio.terra.workspace.service.resource.controlled.flight.clone.flexibleresource;

import static bio.terra.workspace.service.resource.controlled.flight.clone.workspace.WorkspaceCloneUtils.buildDestinationControlledFlexResource;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.IamRoleUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.FlexResourceCreationParameters;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class CloneFlexibleResourceStep implements Step {
  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;
  private final ControlledResourceService controlledResourceService;
  private final ControlledFlexibleResource sourceFlexResource;
  private final CloningInstructions resolvedCloningInstructions;

  public CloneFlexibleResourceStep(
      SamService samService,
      AuthenticatedUserRequest userRequest,
      ControlledResourceService controlledResourceService,
      ControlledFlexibleResource sourceFlexResource,
      CloningInstructions resolvedCloningInstructions) {
    this.samService = samService;
    this.userRequest = userRequest;
    this.controlledResourceService = controlledResourceService;
    this.sourceFlexResource = sourceFlexResource;
    this.resolvedCloningInstructions = resolvedCloningInstructions;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputParameters = flightContext.getInputParameters();
    FlightMap workingMap = flightContext.getWorkingMap();

    FlightUtils.validateRequiredEntries(
        inputParameters,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID);

    String resourceName =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_NAME,
            WorkspaceFlightMapKeys.ResourceKeys.PREVIOUS_RESOURCE_NAME,
            String.class);
    String description =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_DESCRIPTION,
            WorkspaceFlightMapKeys.ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION,
            String.class);

    UUID destinationWorkspaceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    UUID destinationResourceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID, UUID.class);
    UUID destinationFolderId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_FOLDER_ID, UUID.class);

    // New flex resource to be created in flight.
    ControlledFlexibleResource destinationFlexResource =
        buildDestinationControlledFlexResource(
            sourceFlexResource,
            destinationWorkspaceId,
            destinationResourceId,
            destinationFolderId,
            resourceName,
            description,
            samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest));

    ControlledResourceIamRole iamRole =
        IamRoleUtils.getIamRoleForAccessScope(sourceFlexResource.getAccessScope());

    FlexResourceCreationParameters destCreationParameters =
        FlexResourceCreationParameters.fromFlexResource(sourceFlexResource);

    ControlledFlexibleResource clonedFlexResource =
        controlledResourceService
            .createControlledResourceSync(
                destinationFlexResource, iamRole, userRequest, destCreationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE);

    workingMap.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
        clonedFlexResource);

    FlightUtils.setResponse(flightContext, clonedFlexResource, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  // Remove the controlled resource.
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    ControlledFlexibleResource clonedFlexResource =
        context
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
                ControlledFlexibleResource.class);
    if (clonedFlexResource == null) {
      return StepResult.getStepResultSuccess();
    }
    FlightMap inputParameters = context.getInputParameters();
    UUID destinationWorkspaceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    UUID destinationResourceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID, UUID.class);

    controlledResourceService.deleteControlledResourceSync(
        destinationWorkspaceId, destinationResourceId, /* forceDelete= */ false, userRequest);
    return StepResult.getStepResultSuccess();
  }
}
