package bio.terra.workspace.service.iam;

import bio.terra.workspace.app.configuration.SamConfiguration;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.service.create.exception.SamApiException;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Sam {
  private final SamConfiguration samConfig;

  @Autowired
  public Sam(SamConfiguration samConfig) {
    this.samConfig = samConfig;
  }

  private ApiClient getApiClient(String accessToken) {
    ApiClient client = new ApiClient();
    client.setAccessToken(accessToken);
    return client.setBasePath(samConfig.getBasePath());
  }

  private ResourcesApi samResourcesApi(String accessToken) {
    return new ResourcesApi(getApiClient(accessToken));
  }

  public void createDefaultResource(CreateWorkspaceRequestBody body) {
    ResourcesApi resourceApi = samResourcesApi(body.getAuthToken());

    try {
      resourceApi.createResourceWithDefaults(
          SamUtils.SAM_WORKSPACE_RESOURCE, body.getId().toString());
    } catch (ApiException apiException) {
      throw new SamApiException(apiException);
    }
  }
}
