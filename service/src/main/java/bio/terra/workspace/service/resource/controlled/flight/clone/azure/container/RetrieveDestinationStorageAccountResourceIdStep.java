package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.db.exception.LandingZoneNotFoundException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.container.exception.InvalidStorageAccountException;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.util.Optional;
import java.util.UUID;

/**
 * Attempts to fetch the storage account ID for a new container in the destination workspace
 * following process:
 *
 * <p>1. Checks the destination workspace for a single storage account; if more than one is present
 * we are in an unsupported state and fail out.
 *
 * <p>2. If no storage accounts are present, attempts to check the owning Landing Zone. If present,
 * returns the storage account from the LZ, otherwise we fail out.
 */
public class RetrieveDestinationStorageAccountResourceIdStep implements Step {

  private final ResourceDao resourceDao;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final AuthenticatedUserRequest userRequest;

  public RetrieveDestinationStorageAccountResourceIdStep(
      ResourceDao resourceDao,
      LandingZoneApiDispatch landingZoneApiDispatch,
      AuthenticatedUserRequest userRequest) {
    this.resourceDao = resourceDao;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap inputParameters = context.getInputParameters();
    FlightUtils.validateRequiredEntries(
        inputParameters, WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID);
    FlightUtils.validateRequiredEntries(
        context.getWorkingMap(), WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT);

    var destinationWorkspaceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
                AzureCloudContext.class);

    var sourceStorageAccounts =
        resourceDao.enumerateResources(
            destinationWorkspaceId, WsmResourceFamily.AZURE_STORAGE_ACCOUNT, null, 0, 100);

    if (sourceStorageAccounts.size() == 1) {
      context
          .getWorkingMap()
          .put(
              WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_STORAGE_ACCOUNT_RESOURCE_ID,
              sourceStorageAccounts.get(0).getResourceId());
      return StepResult.getStepResultSuccess();
    }
    if (sourceStorageAccounts.size() > 1) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new InvalidStorageAccountException(
              "Multiple storage accounts configured for destination workspace "
                  + destinationWorkspaceId));
    }

    // fall back to the landing zone's storage account, if present
    try {
      UUID lzId = landingZoneApiDispatch.getLandingZoneId(azureCloudContext);
      Optional<ApiAzureLandingZoneDeployedResource> lzStorageAcct =
          landingZoneApiDispatch.getSharedStorageAccount(
              new BearerToken(userRequest.getRequiredToken()), lzId);
      if (lzStorageAcct.isPresent()) {
        context
            .getWorkingMap()
            .put(
                WorkspaceFlightMapKeys.ControlledResourceKeys
                    .DESTINATION_STORAGE_ACCOUNT_RESOURCE_ID,
                lzStorageAcct.get().getResourceId());
        return StepResult.getStepResultSuccess();
      }
    } catch (IllegalStateException e) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new LandingZoneNotFoundException(
              String.format(
                  "Landing zone associated with the Azure cloud context not found. TenantId='%s', SubscriptionId='%s', ResourceGroupId='%s'",
                  azureCloudContext.getAzureTenantId(),
                  azureCloudContext.getAzureSubscriptionId(),
                  azureCloudContext.getAzureResourceGroupId())));
    }

    return new StepResult(
        StepStatus.STEP_RESULT_FAILURE_FATAL,
        new InvalidStorageAccountException(
            "Storage account not found in landing zone or workspace for workspace ID "
                + destinationWorkspaceId));
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
