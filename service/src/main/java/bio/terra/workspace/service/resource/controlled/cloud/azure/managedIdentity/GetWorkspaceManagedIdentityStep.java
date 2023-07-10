package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.util.UUID;

/** Gets an Azure Managed Identity that exists in a workspace. */
public class GetWorkspaceManagedIdentityStep implements Step, ManagedIdentityStep {
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final UUID workspaceId;
  private final ResourceDao resourceDao;
  private final UUID managedIdentityId;

  public GetWorkspaceManagedIdentityStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      UUID workspaceId,
      ResourceDao resourceDao,
      UUID managedIdentityId) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.workspaceId = workspaceId;
    this.resourceDao = resourceDao;
    this.managedIdentityId = managedIdentityId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    var msiManager = crlService.getMsiManager(azureCloudContext, azureConfig);
    ControlledAzureManagedIdentityResource managedIdentityResource =
        resourceDao
            .getResource(workspaceId, managedIdentityId)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY);
    if (managedIdentityResource == null) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new BadRequestException(
              String.format(
                  "An Azure Managed Identity with id %s does not exist in workspace %s",
                  managedIdentityId, workspaceId)));
    }
    var uamiName = managedIdentityResource.getManagedIdentityName();

    var uami =
        msiManager
            .identities()
            .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), uamiName);

    context.getWorkingMap().put(MANAGED_IDENTITY, uami);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo
    return StepResult.getStepResultSuccess();
  }
}
