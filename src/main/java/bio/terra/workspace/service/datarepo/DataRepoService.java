package bio.terra.workspace.service.datarepo;

import bio.terra.datarepo.api.RepositoryApi;
import bio.terra.datarepo.client.ApiClient;
import bio.terra.datarepo.client.ApiException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import org.springframework.stereotype.Component;

@Component
public class DataRepoService {

  private ApiClient getApiClient(String accessToken) {
    ApiClient client = new ApiClient();
    client.setAccessToken(accessToken);
    return client;
  }

  private RepositoryApi repositoryApi(String instance, AuthenticatedUserRequest userReq) {
    return new RepositoryApi(getApiClient(userReq.getRequiredToken()).setBasePath(instance));
  }

  public boolean snapshotExists(
      String instance, String snapshotId, AuthenticatedUserRequest userReq) {
    RepositoryApi repositoryApi = repositoryApi(instance, userReq);

    try {
      repositoryApi.retrieveSnapshot(snapshotId);
      return true;
    } catch (ApiException e) {
      return false;
    }
  }
}
