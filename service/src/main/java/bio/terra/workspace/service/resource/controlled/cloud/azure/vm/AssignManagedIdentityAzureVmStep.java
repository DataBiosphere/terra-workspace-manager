package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetManagedIdentityStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.msi.MsiManager;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssignManagedIdentityAzureVmStep implements Step {

  private static final Logger logger =
      LoggerFactory.getLogger(AssignManagedIdentityAzureVmStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureVmResource resource;

  public AssignManagedIdentityAzureVmStep(
      AzureConfiguration azureConfig, CrlService crlService, ControlledAzureVmResource resource) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    MsiManager msiManager = crlService.getMsiManager(azureCloudContext, azureConfig);

    Set<String> userAssignedIdentities = new HashSet<>();

    // Add user assigned identities from the request, if any
    // Note: passing identities via request is only accepted for application-managed resources.
    // The calling application is responsible for validating the identities with Sam.
    if (CollectionUtils.isNotEmpty(resource.getUserAssignedIdentities())) {
      userAssignedIdentities.addAll(resource.getUserAssignedIdentities());
    }

    // If there is a pet managed identity in the working map, add it
    final Optional<String> petManagedIdentityId =
        Optional.ofNullable(
            context
                .getWorkingMap()
                .get(GetManagedIdentityStep.MANAGED_IDENTITY_RESOURCE_ID, String.class));
    petManagedIdentityId.ifPresent(userAssignedIdentities::add);

    logger.info(
        "Assigning managed identities {} to VM resource {} in workspace {}",
        String.join(",", userAssignedIdentities),
        resource.getResourceId(),
        resource.getWorkspaceId());

    // Assign each managed identity to the VM. Short circuit if any assignment failed.
    return userAssignedIdentities.stream()
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
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    return AzureVmHelper.removeAllUserAssignedManagedIdentitiesFromVm(
        azureCloudContext, computeManager, resource.getVmName());
  }
}
