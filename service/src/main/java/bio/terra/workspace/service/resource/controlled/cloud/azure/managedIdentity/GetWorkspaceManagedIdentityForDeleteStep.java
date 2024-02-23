package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;

import java.util.UUID;

public class GetWorkspaceManagedIdentityForDeleteStep extends GetWorkspaceManagedIdentityStep implements DeleteControlledResourceStep {

  public GetWorkspaceManagedIdentityForDeleteStep(
      UUID workspaceId,
      String managedIdentityName,
      MissingIdentityBehavior failOnMissing,
      ManagedIdentityHelper managedIdentityHelper) {
    super(workspaceId, managedIdentityName, failOnMissing, managedIdentityHelper);
  }

}
