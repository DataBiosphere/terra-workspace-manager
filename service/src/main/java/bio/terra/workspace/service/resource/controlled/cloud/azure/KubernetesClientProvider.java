package bio.terra.workspace.service.resource.controlled.cloud.azure;

import com.azure.resourcemanager.containerservice.ContainerServiceManager;
import io.kubernetes.client.openapi.apis.CoreV1Api;

public interface KubernetesClientProvider {
  CoreV1Api createCoreApiClient(
      ContainerServiceManager containerServiceManager, String mrgName, String aksClusterName);
}
