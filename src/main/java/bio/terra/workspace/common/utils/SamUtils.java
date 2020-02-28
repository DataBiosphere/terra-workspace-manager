package bio.terra.workspace.common.utils;

import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;

public class SamUtils {

  public static String SAM_WORKSPACE_RESOURCE = "mc-workspace";

  public static ResourcesApi samResourcesApi(String accessToken, String basePath) {
    ApiClient client = new ApiClient();
    client.setAccessToken(accessToken);
    client.setBasePath(basePath);
    return new ResourcesApi(client);
  }
}
