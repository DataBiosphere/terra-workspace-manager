package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import java.util.UUID;

/**
 * A simple type extension for GetWorkspaceManagedIdentityForDeleteStep, to type as a
 * DeleteControlledResourceStep. TODO: figure out if this needs functionality from
 * DeleteControlledAzureResourceStep (TBD, WOR-787), and adjust as needed
 */
public class GetWorkspaceManagedIdentityForDeleteStep extends GetWorkspaceManagedIdentityStep
    implements DeleteControlledResourceStep {

  public GetWorkspaceManagedIdentityForDeleteStep(
      UUID workspaceId,
      String managedIdentityName,
      MissingIdentityBehavior failOnMissing,
      ManagedIdentityHelper managedIdentityHelper) {
    super(workspaceId, managedIdentityName, failOnMissing, managedIdentityHelper);
  }
}
