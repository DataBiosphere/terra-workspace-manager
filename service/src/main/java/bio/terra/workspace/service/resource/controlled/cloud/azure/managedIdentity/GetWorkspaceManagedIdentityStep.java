package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import java.util.UUID;

/**
 * Gets an Azure Managed Identity that exists in a workspace. Failure behavior is configured via the
 * MissingIdentityBehavior parameter; if the identity is not found and the behavior is
 * ALLOW_MISSING, the step will succeed. This helps in deletion scenarios when the managed identity
 * may have already been deleted out of band but consuming flights want to proceed.
 */
public class GetWorkspaceManagedIdentityStep implements Step, GetManagedIdentityStep {
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final UUID workspaceId;
  private final ResourceDao resourceDao;
  private final String managedIdentityName;
  private final MissingIdentityBehavior missingIdentityBehavior;

  public GetWorkspaceManagedIdentityStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      UUID workspaceId,
      ResourceDao resourceDao,
      String managedIdentityName,
      MissingIdentityBehavior failOnMissing) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.workspaceId = workspaceId;
    this.resourceDao = resourceDao;
    this.managedIdentityName = managedIdentityName;
    this.missingIdentityBehavior = failOnMissing;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    var msiManager = crlService.getMsiManager(azureCloudContext, azureConfig);

    var managedIdentityResource = getManagedIdentityResource();

    if (managedIdentityResource == null
        && missingIdentityBehavior == MissingIdentityBehavior.ALLOW_MISSING) {
      return StepResult.getStepResultSuccess();
    }

    var uamiName = managedIdentityResource.getManagedIdentityName();

    try {
      var uami =
          msiManager
              .identities()
              .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), uamiName);

      putManagedIdentityInContext(context, uami);

      return StepResult.getStepResultSuccess();
    } catch (ManagementException e) {
      return new StepResult(AzureManagementExceptionUtils.maybeRetryStatus(e), e);
    }
  }

  private ControlledAzureManagedIdentityResource getManagedIdentityResource() {
    try {
      return resourceDao
          .getResourceByName(workspaceId, managedIdentityName)
          .castByEnum(WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY);
    } catch (ResourceNotFoundException e) {
      // There are some older resources where the resource id was stored before we decided
      // storing the resource name would be better. This is a fallback to support those
      // resources. If the resource is not found by name and the name is an uuid, try by id.
      try {
        return resourceDao
            .getResource(workspaceId, UUID.fromString(managedIdentityName))
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY);
      } catch (ResourceNotFoundException | IllegalArgumentException ex) {
        if (missingIdentityBehavior.equals(MissingIdentityBehavior.FAIL_ON_MISSING)) {
          throw e;
        } else {
          return null;
        }
      }
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo
    return StepResult.getStepResultSuccess();
  }
}
