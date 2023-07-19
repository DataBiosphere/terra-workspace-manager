package bio.terra.workspace.common.utils;

import org.springframework.stereotype.Component;

@Component
public class MockAzureApi extends MockMvcUtils {
  public static final String CREATE_AZURE_DISK_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/disks";
  public static final String CREATE_AZURE_VM_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/vm";
  public static final String CREATE_AZURE_SAS_TOKEN_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/storageContainer/%s/getSasToken";
  public static final String CREATE_AZURE_BATCH_POOL_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/batchpool";
  public static final String CREATE_AZURE_STORAGE_CONTAINERS_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/storageContainer";
  public static final String AZURE_BATCH_POOL_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/batchpool/%s";
  public static final String AZURE_DISK_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/disks/%s";
  public static final String AZURE_STORAGE_CONTAINER_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/storageContainer/%s";
  public static final String AZURE_VM_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/vm/%s";
  public static final String CLONE_AZURE_STORAGE_CONTAINER_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/storageContainer/%s/clone";
}
