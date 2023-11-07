package bio.terra.workspace.service.resource.controlled.flight.clone.azure.managedIdentity;

import static bio.terra.workspace.common.utils.FlightUtils.getInputParameterOrWorkingValue;
import static bio.terra.workspace.common.utils.FlightUtils.getRequired;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.common.utils.IamRoleUtils;
import bio.terra.workspace.generated.model.ApiAzureManagedIdentityCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.ControlledAzureManagedIdentityResource;
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

public class CopyAzureManagedIdentityDefinitionStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(CopyAzureManagedIdentityDefinitionStep.class);

  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;
  private final ControlledAzureManagedIdentityResource sourceIdentity;
  private final ControlledResourceService controlledResourceService;
  private final CloningInstructions resolvedCloningInstructions;

  public CopyAzureManagedIdentityDefinitionStep(
      SamService samService,
      AuthenticatedUserRequest userRequest,
      ControlledAzureManagedIdentityResource sourceIdentity,
      ControlledResourceService controlledResourceService,
      CloningInstructions resolvedCloningInstructions) {
    this.samService = samService;
    this.userRequest = userRequest;
    this.sourceIdentity = sourceIdentity;
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
        getRequired(inputParameters, ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    var destinationResourceId =
        getRequired(inputParameters, ControlledResourceKeys.DESTINATION_RESOURCE_ID, UUID.class);
    var userRequest =
        getRequired(
            inputParameters,
            JobMapKeys.AUTH_USER_INFO.getKeyName(),
            AuthenticatedUserRequest.class);

    ControlledAzureManagedIdentityResource destinationIdentityResource =
        ControlledAzureManagedIdentityResource.builder()
            .managedIdentityName("id%s".formatted(UUID.randomUUID().toString()))
            .common(
                sourceIdentity.buildControlledCloneResourceCommonFields(
                    destinationWorkspaceId,
                    destinationResourceId,
                    null,
                    destinationResourceName,
                    description,
                    samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest),
                    sourceIdentity.getRegion()))
            .build();

    ControlledResourceIamRole iamRole =
        IamRoleUtils.getIamRoleForAccessScope(sourceIdentity.getAccessScope());

    // save the identity definition for downstream steps
    workingMap.put(ControlledResourceKeys.CLONED_RESOURCE_DEFINITION, destinationIdentityResource);

    ApiAzureManagedIdentityCreationParameters destinationCreationParameters =
        new ApiAzureManagedIdentityCreationParameters().name(destinationResourceName);

    // create the identity
    try {
      controlledResourceService.createControlledResourceSync(
          destinationIdentityResource, iamRole, userRequest, destinationCreationParameters);
    } catch (DuplicateResourceException e) {
      // We are catching DuplicateResourceException here since we check for the container's presence
      // earlier in the parent flight of this step and bail out if it already exists.
      // A duplicate resource being present in this context means we are in a retry and can move on
      logger.info(
          "Destination azure managed identity already exists, resource_id = {}, name = {}",
          destinationResourceId,
          destinationResourceName);
    }

    var identityResult =
        new ClonedAzureResource(
            resolvedCloningInstructions,
            sourceIdentity.getWorkspaceId(),
            sourceIdentity.getResourceId(),
            destinationIdentityResource);

    workingMap.put(WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE, identityResult);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    var clonedIdentity =
        context
            .getWorkingMap()
            .get(
                ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
                ControlledAzureManagedIdentityResource.class);
    try {
      if (clonedIdentity != null) {
        controlledResourceService.deleteControlledResourceSync(
            clonedIdentity.getWorkspaceId(),
            clonedIdentity.getResourceId(),
            /* forceDelete= */ false,
            userRequest);
      }
    } catch (ResourceNotFoundException e) {
      logger.info(
          "No managed identity resource found {} in WSM, assuming it was previously removed.",
          clonedIdentity.getResourceId());
      return StepResult.getStepResultSuccess();
    } catch (ManagementException e) {
      if (AzureManagementExceptionUtils.isExceptionCode(
          e, AzureManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
        logger.info(
            "Identity with ID {} not found in Azure, assuming it was previously removed. Result from Azure = {}",
            clonedIdentity.getResourceId(),
            e.getValue().getCode(),
            e);
        return StepResult.getStepResultSuccess();
      }
      logger.warn(
          "Deleting cloned identity with ID {} failed, retrying.", clonedIdentity.getResourceId());
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }
}
