package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.utils.Rethrow;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.msi.MsiManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Queries Sam for any action managed identities for the calling user, and assigns them to the VM.
 */
public class AssignActionManagedIdentitiesVmStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(AssignActionManagedIdentitiesVmStep.class);

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;
  private final ControlledAzureVmResource resource;

  public AssignActionManagedIdentitiesVmStep(
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
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
                AzureCloudContext.class);

    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    MsiManager msiManager = crlService.getMsiManager(azureCloudContext, azureConfig);

    // Query Sam for azure_managed_identity resources the user has access to
    var privateAzureStorageAccountResources =
        Rethrow.onInterrupted(
            () ->
                samService.listResourceIds(
                    userRequest, SamConstants.SamResource.AZURE_MANAGED_IDENTITY),
            "listSamResources");

    // Query Sam for action managed identities for the above resources
    var actionManagedIdentities =
        privateAzureStorageAccountResources.stream()
            .flatMap(
                resource ->
                    Rethrow.onInterrupted(
                        () ->
                            samService
                                .getActionManagedIdentity(
                                    userRequest,
                                    SamConstants.SamResource.AZURE_MANAGED_IDENTITY,
                                    resource,
                                    SamConstants.SamAzureManagedIdentityAction.IDENTIFY)
                                .stream(),
                        "getActionManagedIdentity"));

    // Assign each action managed identity to the VM. Short circuit if any assignment failed.
    return actionManagedIdentities
        .map(
            identity ->
                AzureVmHelper.assignManagedIdentityToVm(
                    azureCloudContext, computeManager, msiManager, resource.getVmName(), identity))
        .filter(a -> !a.isSuccess())
        .findFirst()
        .orElse(StepResult.getStepResultSuccess());
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
                AzureCloudContext.class);
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    // Just unassign all identities from the VM in the undo step
    return AzureVmHelper.removeAllUserAssignedManagedIdentitiesFromVm(
        azureCloudContext, computeManager, resource.getVmName());
  }
}
