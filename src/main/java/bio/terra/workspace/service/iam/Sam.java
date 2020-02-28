package bio.terra.workspace.service.iam;

import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;

public class Sam {

    public static ResourcesApi samResourcesApi(String accessToken, String basePath) {
        ApiClient client = new ApiClient();
        client.setAccessToken(accessToken);
        client.setBasePath(basePath);
        return new ResourcesApi(client);
    }

}
