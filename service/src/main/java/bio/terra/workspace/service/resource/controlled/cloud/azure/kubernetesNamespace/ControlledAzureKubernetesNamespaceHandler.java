package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;

public class ControlledAzureKubernetesNamespaceHandler implements WsmResourceHandler {
  private static ControlledAzureKubernetesNamespaceHandler theHandler;

  public static ControlledAzureKubernetesNamespaceHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAzureKubernetesNamespaceHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAzureKubernetesNamespaceAttributes attributes =
        DbSerDes.fromJson(
            dbResource.getAttributes(), ControlledAzureKubernetesNamespaceAttributes.class);

    return ControlledAzureKubernetesNamespaceResource.builder()
        .kubernetesNamespace(attributes.getKubernetesNamespace())
        .managedIdentity(attributes.getManagedIdentity())
        .kubernetesServiceAccount(attributes.getKubernetesServiceAccount())
        .databases(attributes.getDatabases())
        .common(new ControlledResourceFields(dbResource))
        .build();
  }
}
