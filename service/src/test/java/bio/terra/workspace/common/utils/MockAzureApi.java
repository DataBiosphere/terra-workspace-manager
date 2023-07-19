package bio.terra.workspace.common.utils;

import org.springframework.stereotype.Component;

@Component
public class MockAzureApi extends MockMvcUtils {
  // Disks
  public static final String CREATE_CONTROLLED_AZURE_DISK_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/disks";
  public static final String CONTROLLED_AZURE_DISK_PATH_FORMAT =
      CREATE_CONTROLLED_AZURE_DISK_PATH_FORMAT + "/%s";

  // VM
  public static final String CREATE_CONTROLLED_AZURE_VM_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/vm";
  public static final String CONTROLLED_AZURE_VM_PATH_FORMAT =
      CREATE_CONTROLLED_AZURE_VM_PATH_FORMAT + "/%s";

  // Storage Container
  public static final String CREATE_CONTROLLED_AZURE_STORAGE_CONTAINER_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/storageContainer";
  public static final String CONTROLLED_AZURE_STORAGE_CONTAINER_PATH_FORMAT =
      CREATE_CONTROLLED_AZURE_STORAGE_CONTAINER_PATH_FORMAT + "/%s";
  public static final String CLONE_CONTROLLED_AZURE_STORAGE_CONTAINER_PATH_FORMAT =
      CONTROLLED_AZURE_STORAGE_CONTAINER_PATH_FORMAT + "/clone";
  public static final String CONTROLLED_AZURE_STORAGE_CONTAINER_SAS_TOKEN_PATH_FORMAT =
      CONTROLLED_AZURE_STORAGE_CONTAINER_PATH_FORMAT + "/getSasToken";

  // Batch Pool
  public static final String CREATE_CONTROLLED_AZURE_BATCH_POOL_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/batchpool";
  public static final String CONTROLLED_AZURE_BATCH_POOL_PATH_FORMAT =
      CREATE_CONTROLLED_AZURE_BATCH_POOL_PATH_FORMAT + "/%s";
}
