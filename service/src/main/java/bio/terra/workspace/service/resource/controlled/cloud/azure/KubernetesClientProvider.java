package bio.terra.workspace.service.resource.controlled.cloud.azure;

import bio.terra.landingzone.library.landingzones.definition.ArmManagers;
import com.azure.resourcemanager.containerservice.ContainerServiceManager;
import io.kubernetes.client.openapi.apis.CoreV1Api;

public interface KubernetesClientProvider {
  CoreV1Api createCoreApiClient(ContainerServiceManager containerServiceManager, String mrgName, String aksClusterName);
}
