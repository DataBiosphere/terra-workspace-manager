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

    // Query Sam for private_azure_storage_account resources the user has access to
    var privateAzureStorageAccountResources =
        Rethrow.onInterrupted(
            () ->
                samService.listResourceIds(
                    userRequest, SamConstants.SamResource.PRIVATE_AZURE_STORAGE_ACCOUNT),
            "listSamResources");

    // Query Sam for action managed identities for the above resources
    var actionManagedIdentities =
        privateAzureStorageAccountResources.stream()
            .flatMap(
                r ->
                    Rethrow.onInterrupted(
                        () ->
                            samService
                                .getActionManagedIdentity(
                                    userRequest,
                                    SamConstants.SamResource.PRIVATE_AZURE_STORAGE_ACCOUNT,
                                    r,
                                    SamConstants.SamPrivateAzureStorageAccountAction.IDENTIFY)
                                .stream(),
                        "getActionManagedIdentity"));

    // Assign each action managed identity to the VM. Short circuit if any assignment failed.
    return actionManagedIdentities
        .map(
            i ->
                AzureVmHelper.assignManagedIdentityToVm(
                    azureCloudContext, computeManager, msiManager, resource.getVmName(), i))
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
