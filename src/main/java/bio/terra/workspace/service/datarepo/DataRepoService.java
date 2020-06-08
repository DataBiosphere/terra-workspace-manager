package bio.terra.workspace.service.datarepo;

import bio.terra.datarepo.api.RepositoryApi;
import bio.terra.datarepo.client.ApiClient;
import bio.terra.datarepo.client.ApiException;
import bio.terra.workspace.app.configuration.DataRepoConfig;
import bio.terra.workspace.common.exception.ValidationException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DataRepoService {

  private final DataRepoConfig dataRepoConfig;

  @Autowired
  public DataRepoService(DataRepoConfig dataRepoConfig) {
    this.dataRepoConfig = dataRepoConfig;
  }

  private ApiClient getApiClient(String accessToken) {
    ApiClient client = new ApiClient();
    client.setAccessToken(accessToken);
    return client;
  }

  private RepositoryApi repositoryApi(String instance, AuthenticatedUserRequest userReq) {
    validateInstance(instance);
    return new RepositoryApi(getApiClient(userReq.getRequiredToken()).setBasePath(instance));
  }

  public void validateInstance(String instance) {
    if (!dataRepoConfig.getInstances().containsValue(instance.toLowerCase().trim())) {
      throw new ValidationException(
          "Data repository instance "
              + instance
              + " is not allowed. Valid instances are: "
              + String.join(", ", dataRepoConfig.getInstances().keySet()));
    }
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
