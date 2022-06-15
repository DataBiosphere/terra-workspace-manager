package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.compute.ComputeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoveManagedIdentityAzureVmStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(RemoveManagedIdentityAzureVmStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;
  private final ControlledAzureVmResource resource;

  public RemoveManagedIdentityAzureVmStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      SamService samService,
      AuthenticatedUserRequest userRequest,
      ControlledAzureVmResource resource) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.samService = samService;
    this.userRequest = userRequest;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    String petManagedIdentityId =
        SamRethrow.onInterrupted(
            () ->
                samService.getOrCreateUserManagedIdentity(
                    userRequest,
                    azureCloudContext.getAzureSubscriptionId(),
                    azureCloudContext.getAzureTenantId(),
                    azureCloudContext.getAzureResourceGroupId()),
            "getPetManagedIdentity");

    return AzureVmHelper.removePetManagedIdentitiesFromVm(
        azureCloudContext, computeManager, resource.getVmName(), petManagedIdentityId);
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo removing of user-assigned managed identities of Azure vm resource {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
